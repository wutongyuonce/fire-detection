"""
Train2: YOLOv8s + CBAM 注意力机制训练

配置：
  - 模型: YOLOv8s + CBAM 注意力机制
  - 输入: 960x960
  - Epochs: 100
  - Batch: 8
  
目标：
  - 通过注意力机制增强特征学习
  - 针对小火点优化检测
  - 评估注意力对性能的改进
"""

import argparse
import os
import warnings
from pathlib import Path
import torch
import torch.nn as nn
import shutil

# 忽略不重要的警告
warnings.filterwarnings('ignore', category=FutureWarning)
warnings.filterwarnings('ignore', category=UserWarning)

CODE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = CODE_DIR.parent
DATA_YAML = PROJECT_DIR / 'MyData_Fire' / 'data.yaml'
RUNS_DIR = PROJECT_DIR / 'runs'
CHECKPOINTS_DIR = PROJECT_DIR / 'checkpoints'

# 训练超参数
TRAIN2_EPOCHS = 100
TRAIN2_BATCH = 8
TRAIN2_IMG_SIZE = 960
TRAIN2_PATIENCE = 25
TRAIN2_LR0 = 0.002
TRAIN2_LRF = 0.01
TRAIN2_WORKERS = 4
TRAIN2_PROJECT_NAME = 'train2'
TRAIN2_PROGRESS_MODE = 'single'


def configure_progress_output(mode='single'):
    """配置训练日志输出，减少 PowerShell 中的多行进度条刷屏。"""
    mode = (mode or 'single').lower()
    if mode not in {'single', 'off', 'auto'}:
        mode = 'single'

    if mode == 'off':
        # 完全关闭 ultralytics 进度条与详细日志
        os.environ['YOLO_VERBOSE'] = 'false'
        os.environ['TQDM_DISABLE'] = '1'
    elif mode == 'single':
        # 保留进度条，但降低刷新频率，尽量保持单行刷新
        os.environ['YOLO_VERBOSE'] = 'true'
        os.environ['TQDM_DISABLE'] = '0'
        os.environ['TQDM_MININTERVAL'] = '1.2'
        os.environ['TQDM_MINITERS'] = '20'


def get_yolo_class():
    """在设置日志环境变量后再导入 YOLO，确保进度配置生效。"""
    from ultralytics import YOLO
    return YOLO


def resolve_weights_path(weights):
    """优先从 code 目录解析权重路径。"""
    p = Path(weights)
    if p.is_absolute():
        return str(p)

    code_candidate = CODE_DIR / weights
    if code_candidate.exists():
        print(f"  ✓ 权重文件已在 code 目录下: {code_candidate}")
        return str(code_candidate)

    if p.exists():
        print(f"  ✓ 权重文件位置: {p.resolve()}")
        return str(p.resolve())

    # 若文件不存在，仍返回 code 目录候选路径，方便 ultralytics 在同一路径下载和读取
    print(f"  ℹ 权重文件 '{weights}' 不存在，将尝试在 code 目录下解析/下载: {code_candidate}")
    return str(code_candidate)


# ============================================================================
# CBAM (Convolutional Block Attention Module) 注意力机制
# ============================================================================

class ChannelAttention(nn.Module):
    """通道注意力模块"""
    def __init__(self, in_channels, reduction_ratio=16):
        super(ChannelAttention, self).__init__()
        self.avg_pool = nn.AdaptiveAvgPool2d(1)
        self.max_pool = nn.AdaptiveMaxPool2d(1)
        
        hidden_channels = max(1, in_channels // reduction_ratio)
        self.fc = nn.Sequential(
            nn.Conv2d(in_channels, hidden_channels, kernel_size=1, bias=False),
            nn.ReLU(inplace=True),
            nn.Conv2d(hidden_channels, in_channels, kernel_size=1, bias=False)
        )
        self.sigmoid = nn.Sigmoid()
    
    def forward(self, x):
        avg_out = self.fc(self.avg_pool(x))
        max_out = self.fc(self.max_pool(x))
        out = avg_out + max_out
        return self.sigmoid(out)


class SpatialAttention(nn.Module):
    """空间注意力模块"""
    def __init__(self, kernel_size=7):
        super(SpatialAttention, self).__init__()
        padding = (kernel_size - 1) // 2
        self.conv = nn.Conv2d(2, 1, kernel_size=kernel_size, padding=padding, bias=False)
        self.sigmoid = nn.Sigmoid()
    
    def forward(self, x):
        avg_out = torch.mean(x, dim=1, keepdim=True)
        max_out, _ = torch.max(x, dim=1, keepdim=True)
        x_cat = torch.cat([avg_out, max_out], dim=1)
        out = self.conv(x_cat)
        return self.sigmoid(out)


class CBAM(nn.Module):
    """CBAM: Convolutional Block Attention Module
    
    包含通道注意力和空间注意力两个模块
    """
    def __init__(self, in_channels, reduction_ratio=16, kernel_size=7):
        super(CBAM, self).__init__()
        self.channel_attention = ChannelAttention(in_channels, reduction_ratio)
        self.spatial_attention = SpatialAttention(kernel_size)
    
    def forward(self, x):
        out = x * self.channel_attention(x)
        out = out * self.spatial_attention(out)
        return out


# ============================================================================
# GPU 显存检查
# ============================================================================

def check_gpu_memory():
    """检查 GPU 显存状态"""
    print("\n" + "=" * 60)
    print("GPU 信息")
    print("=" * 60)
    
    if torch.cuda.is_available():
        device_count = torch.cuda.device_count()
        print(f"✓ CUDA 可用，检测到 {device_count} 个 GPU")
        
        for i in range(device_count):
            props = torch.cuda.get_device_properties(i)
            print(f"\n  GPU {i}: {props.name}")
            print(f"  - 显存: {props.total_memory / 1e9:.1f} GB")
            print(f"  - 计算能力: {props.major}.{props.minor}")
            
            # 实时显存使用
            allocated = torch.cuda.memory_allocated(i) / 1e9
            reserved = torch.cuda.memory_reserved(i) / 1e9
            print(f"  - 已用: {allocated:.2f} GB / 预留: {reserved:.2f} GB")
    else:
        print("✗ CUDA 不可用，将使用 CPU 训练（极慢）")
        return None
    
    return torch.cuda.get_device_properties(0)


# ============================================================================
# CBAM 模型增强
# ============================================================================

def add_cbam_to_model(model):
    """给 YOLOv8 模型的 backbone 添加 CBAM 注意力"""
    print("  → 正在添加 CBAM 注意力机制到模型...")
    
    try:
        # 关键修复：将 CBAM 显式注册为 model.model 的子模块，确保参数被优化器追踪
        if not hasattr(model.model, 'cbam_blocks'):
            model.model.cbam_blocks = nn.ModuleDict()

        def key_of(module_name):
            # ModuleDict 的 key 不能含 '.'
            return module_name.replace('.', '__')

        # 先冻结模块列表，避免在遍历时新增子模块触发 "dictionary changed size"
        named_modules_snapshot = list(model.model.named_modules())

        hook_count = 0
        handles = []
        for name, module in named_modules_snapshot:
            if isinstance(module, nn.Conv2d) and 'model' in name and 'head' not in name:
                if module.out_channels in [128, 256, 512]:  # 重要的特征层
                    cbam_key = key_of(name)
                    if cbam_key not in model.model.cbam_blocks:
                        model.model.cbam_blocks[cbam_key] = CBAM(module.out_channels)

                    cbam_layer = model.model.cbam_blocks[cbam_key]

                    def hook(_module, _input, output, layer=cbam_layer):
                        return output * layer(output)

                    handles.append(module.register_forward_hook(hook))
                    hook_count += 1

        # 保存 hook 句柄，便于后续调试/清理
        model.model.cbam_hook_handles = handles

        # 训练前可见性检查：确认 CBAM 参数已进入参数列表
        cbam_param_count = sum(p.numel() for n, p in model.model.named_parameters() if 'cbam_blocks' in n)

        print(f"  ✓ 已在 {hook_count} 个关键层添加 CBAM 注意力")
        print(f"  ✓ 注意力层通道数: 128, 256, 512")
        print(f"  ✓ 已注册 CBAM 可训练参数: {cbam_param_count:,}")

        if hook_count == 0 or cbam_param_count == 0:
            raise RuntimeError("CBAM 注入未生效（hook 或参数数目为 0）")
        
    except Exception as e:
        raise RuntimeError(f"CBAM 添加出错: {e}") from e
    
    return model


# ============================================================================
# Checkpoint 管理
# ============================================================================

def copy_best_weights_to_checkpoint(source_dir, checkpoint_file_name):
    """复制最佳权重到 checkpoint 目录"""
    try:
        source_best = Path(source_dir) / 'weights' / 'best.pt'
        CHECKPOINTS_DIR.mkdir(parents=True, exist_ok=True)
        
        if source_best.exists():
            target_best = CHECKPOINTS_DIR / checkpoint_file_name
            shutil.copy(source_best, target_best)
            print(f"\n  ✓ 权重已保存到: {target_best}")
        else:
            print(f"\n  ⚠ 未找到最优权重文件: {source_best}")
    except Exception as e:
        print(f"\n  ⚠ 保存 checkpoint 出错: {e}")


# ============================================================================
# Train2: 带 CBAM 注意力的 YOLOv8s
# ============================================================================

def train2(resume=False, weights='yolov8s.pt', epochs=100, progress='single'):
    """Train2: 带 CBAM 注意力的模型"""
    print("\n" + "=" * 60)
    print("🎯 Train2: YOLOv8s + CBAM 注意力")
    print("=" * 60)
    print("""
配置：
  - 模型: YOLOv8s + CBAM 注意力机制
  - 输入: 960x960
  - Epochs: 100
  - Batch: 8
  - 注意力: CBAM（通道 + 空间）
  - 时间估计: 4-6 小时
  
改进点：
  - 通道注意力: 学习特征通道之间的关系
  - 空间注意力: 学习空间位置的重要性
  - 高分辨率: 更好地检测小火点
  - 更长训练: 充分学习注意力权重
  
目标：
  - 通过注意力机制增强特征学习
  - 针对小火点优化检测
  - 评估注意力对性能的改进
    """)

    configure_progress_output(progress)
    
    weights_path = resolve_weights_path(weights)
    YOLO = get_yolo_class()
    model = YOLO(weights_path)
    
    # 添加 CBAM 注意力
    model = add_cbam_to_model(model)
    
    if resume:
        print(f"  ✓ 从 checkpoint 续训: {weights_path}")
    else:
        print(f"  ✓ 从预训练权重开始: {weights_path}")
    
    # 训练
    results = model.train(
        data=str(DATA_YAML),
        epochs=epochs,
        imgsz=TRAIN2_IMG_SIZE,
        batch=TRAIN2_BATCH,
        device=0,
        workers=TRAIN2_WORKERS,
        patience=TRAIN2_PATIENCE,
        resume=resume,
        save=True,
        save_period=10,
        exist_ok=True,
        
        augment=True,
        hsv_h=0.015,
        hsv_s=0.7,
        hsv_v=0.4,
        flipud=0.5,
        fliplr=0.5,
        
        optimizer='SGD',
        momentum=0.937,
        weight_decay=0.0005,
        lr0=TRAIN2_LR0,
        lrf=TRAIN2_LRF,
        
        val=True,
        amp=True,
        verbose=False,
        project=str(RUNS_DIR),
        name=TRAIN2_PROJECT_NAME,
    )
    
    print("\n✓ Train2 完成")
    print(f"  - 最佳权重: {RUNS_DIR / TRAIN2_PROJECT_NAME / 'weights' / 'best.pt'}")
    print(f"  - 结果目录: {RUNS_DIR / TRAIN2_PROJECT_NAME}")
    
    # 复制到 checkpoint2
    copy_best_weights_to_checkpoint(RUNS_DIR / TRAIN2_PROJECT_NAME, 'checkpoint2.pt')
    
    return results


# ============================================================================
# 主函数
# ============================================================================

def main():
    parser = argparse.ArgumentParser(
        description='YOLOv8 火焰检测 - Train2: CBAM 注意力',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例用法：
  python train2.py                       # 正常训练
  python train2.py --epochs 150          # 自定义 epoch 数
  python train2.py --resume              # 从上次 checkpoint 续训
  python train2.py --weights path/to/pt # 使用自定义初始权重
        """
    )
    
    parser.add_argument(
        '--resume',
        action='store_true',
        help='从上次保存的 checkpoint 继续训练'
    )
    parser.add_argument(
        '--weights',
        type=str,
        default='yolov8s.pt',
        help='训练初始化权重或 checkpoint 路径 (默认: yolov8s.pt)'
    )
    parser.add_argument(
        '--epochs',
        type=int,
        default=TRAIN2_EPOCHS,
        help=f'训练轮数 (默认: {TRAIN2_EPOCHS})'
    )
    parser.add_argument(
        '--progress',
        type=str,
        choices=['single', 'off', 'auto'],
        default=TRAIN2_PROGRESS_MODE,
        help='进度条显示模式: single(尽量单行刷新)/off(关闭进度条)/auto(默认行为)'
    )
    
    args = parser.parse_args()
    
    # 检查 GPU
    print("\n" + "=" * 60)
    print("🚀 YOLOv8 火焰检测训练 - Train2")
    print("=" * 60)
    
    gpu_props = check_gpu_memory()
    
    # 检查数据集
    data_yaml = DATA_YAML
    if not data_yaml.exists():
        print(f"\n✗ 错误: 找不到数据配置文件 {data_yaml.absolute()}")
        print("  请先运行: python setup_dataset.py")
        return
    
    print(f"\n✓ 数据配置文件: {data_yaml.absolute()}")
    
    # 检查 ultralytics
    try:
        configure_progress_output(args.progress)
        _ = get_yolo_class()
        print("✓ ultralytics 库已安装")
    except ImportError:
        print("\n⚠ 检测到未安装 ultralytics，正在安装...")
        import subprocess
        subprocess.check_call(['pip', 'install', 'ultralytics', '-q'])
        print("✓ ultralytics 安装完成")
    
    # 创建 checkpoints 目录
    CHECKPOINTS_DIR.mkdir(parents=True, exist_ok=True)
    
    # 运行训练
    try:
        train2(resume=args.resume, weights=args.weights, epochs=args.epochs, progress=args.progress)
    
    except KeyboardInterrupt:
        print("\n\n⚠ 训练被中断")
    except Exception as e:
        print(f"\n✗ 训练出错: {e}")
        import traceback
        traceback.print_exc()
    
    print("\n" + "=" * 60)
    print("✓ Train2 执行完成")
    print("=" * 60)
    print("""
后续步骤：
  1. 查看结果:  python evaluate.py --checkpoint checkpoint2
    2. 推理测试:  python inference.py --image <path> --weights checkpoints/checkpoint2.pt
  3. 对比:      对比 checkpoint1 和 checkpoint2 的性能差异
  4. 分析:      CBAM 注意力对检测精度的影响
    """)


if __name__ == '__main__':
    main()
