"""
Predict: 从 runs 目录自动读取训练参数和最佳权重，对 predict 目录下图片批量推理。

默认行为：
  - 自动选择最新的 runs/<exp>/ 作为推理来源
  - 读取对应 args.yaml，复用训练时的 imgsz / device / iou / conf 等参数
  - 对 PROJECT_DIR/predict 下的图片进行预测
  - 将结果输出到 PROJECT_DIR/results/<exp>/
"""

import argparse
import warnings
from pathlib import Path

from ultralytics import YOLO
from ultralytics.utils import YAML

warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

CODE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = CODE_DIR.parent
PREDICT_DIR = PROJECT_DIR / "predict"
RESULTS_DIR = PROJECT_DIR / "results"

# ============================================================================
# 用户可配置参数（集中在这里，方便直接修改）
# ============================================================================

DEFAULT_WEIGHTS_PATH = PROJECT_DIR / "runs" / "train2" / "weights" / "best.pt"  # 直接写完整 .pt 路径
DEFAULT_SOURCE_DIR = PREDICT_DIR  # 默认输入目录（图片或视频都可）
DEFAULT_RESULTS_DIR = RESULTS_DIR  # 默认输出根目录
DEFAULT_MODE = "auto"  # auto / image / video

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
VIDEO_SUFFIXES = {".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".m4v"}


def resolve_weights_path(weights: str | None) -> Path:
    """解析权重路径：仅支持显式指定完整权重文件。"""
    if not weights:
        raise ValueError("未指定权重文件，请设置 DEFAULT_WEIGHTS_PATH 或使用 --weights")

    if weights:
        text = str(weights).replace("PROJECT_DIR", str(PROJECT_DIR)).replace("${PROJECT_DIR}", str(PROJECT_DIR))
        w = Path(text)
        if not w.is_absolute():
            w = (PROJECT_DIR / w).resolve()
        if not w.exists():
            raise FileNotFoundError(f"找不到权重文件: {w}")
        return w


def infer_run_dir_from_weights(weights_path: Path) -> Path | None:
    """从 .../runs/<exp>/weights/*.pt 自动反推 run_dir。"""
    if weights_path.parent.name == "weights":
        run_dir = weights_path.parent.parent
        if (run_dir / "args.yaml").exists():
            return run_dir
    return None


def load_train_args(run_dir: Path | None) -> dict:
    """读取训练时保存的 args.yaml。"""
    if run_dir is None:
        return {}

    args_file = run_dir / "args.yaml"
    if not args_file.exists():
        return {}

    return YAML.load(args_file)


def collect_sources(source_path: Path, mode: str = "auto") -> tuple[list[Path], str]:
    """收集待预测输入，支持图片和视频。返回 (文件列表, 媒体类型)。"""
    if not source_path.exists():
        raise FileNotFoundError(f"找不到预测输入路径: {source_path}")

    mode = (mode or "auto").lower()
    if mode not in {"auto", "image", "video"}:
        mode = "auto"

    def classify_suffix(suffix: str) -> str | None:
        sfx = suffix.lower()
        if sfx in IMAGE_SUFFIXES:
            return "image"
        if sfx in VIDEO_SUFFIXES:
            return "video"
        return None

    if source_path.is_file():
        media_type = classify_suffix(source_path.suffix)
        if media_type is None:
            raise ValueError(f"不支持的文件类型: {source_path}")
        if mode != "auto" and mode != media_type:
            raise ValueError(f"当前 mode={mode}，但输入是 {media_type} 文件: {source_path}")
        return [source_path], media_type

    files = sorted([p for p in source_path.rglob("*") if p.is_file()])
    images = [p for p in files if p.suffix.lower() in IMAGE_SUFFIXES]
    videos = [p for p in files if p.suffix.lower() in VIDEO_SUFFIXES]

    if mode == "image":
        return images, "image"
    if mode == "video":
        return videos, "video"

    # auto 模式
    merged = images + videos
    if images and videos:
        return merged, "mixed"
    if videos:
        return videos, "video"
    return images, "image"


def build_predict_kwargs(train_args: dict, source_dir: Path, save_name: str) -> dict:
    """从训练参数构建预测参数。"""
    conf = train_args.get("conf")
    if conf is None:
        conf = 0.25

    kwargs = {
        "source": str(source_dir),
        "imgsz": int(train_args.get("imgsz", 640)),
        "conf": float(conf),
        "iou": float(train_args.get("iou", 0.7)),
        "device": train_args.get("device", "0"),
        "half": bool(train_args.get("half", False)),
        "max_det": int(train_args.get("max_det", 300)),
        "agnostic_nms": bool(train_args.get("agnostic_nms", False)),
        "classes": train_args.get("classes", None),
        "show_labels": bool(train_args.get("show_labels", True)),
        "show_conf": bool(train_args.get("show_conf", True)),
        "show_boxes": bool(train_args.get("show_boxes", True)),
        "line_width": train_args.get("line_width", None),
        "save": True,
        "save_txt": False,
        "save_conf": False,
        "save_crop": False,
        "show": False,
        "project": str(DEFAULT_RESULTS_DIR),
        "name": save_name,
        "exist_ok": True,
        "verbose": False,
    }

    if kwargs["line_width"] is not None:
        kwargs["line_width"] = int(kwargs["line_width"])

    return kwargs


def main():
    parser = argparse.ArgumentParser(
        description="YOLOv8 批量预测 - 自动读取 runs 里的模型参数",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例用法：
    python predict.py                               # 使用顶部 DEFAULT_WEIGHTS_PATH
    python predict.py --weights runs/train2/weights/best.pt
    python predict.py --source predict              # 指定输入目录（可含图片/视频）
    python predict.py --mode image                  # 仅处理图片
    python predict.py --video predict/test.mp4      # 处理单个视频
    python predict.py --video predict/videos        # 处理视频目录
        """,
    )
    parser.add_argument(
        "--weights",
        type=str,
        default=str(DEFAULT_WEIGHTS_PATH) if DEFAULT_WEIGHTS_PATH else None,
        help="显式指定权重文件路径（.pt）",
    )
    parser.add_argument(
        "--source",
        type=str,
        default=str(DEFAULT_SOURCE_DIR),
        help=f"待预测输入路径（目录或单文件）(默认: {DEFAULT_SOURCE_DIR})",
    )
    parser.add_argument(
        "--mode",
        type=str,
        choices=["auto", "image", "video"],
        default=DEFAULT_MODE,
        help="输入类型: auto(自动识别) / image(仅图片) / video(仅视频)",
    )
    parser.add_argument(
        "--video",
        type=str,
        default=None,
        help="视频文件或视频目录。提供该参数时，会覆盖 --source 并按视频模式处理",
    )

    args = parser.parse_args()

    weights_path = resolve_weights_path(args.weights)
    run_dir = infer_run_dir_from_weights(weights_path)
    train_args = load_train_args(run_dir)

    source_path = Path(args.video) if args.video else Path(args.source)
    mode = "video" if args.video else args.mode
    items, media_type = collect_sources(source_path, mode)
    if not items:
        print(f"✗ 在 {source_path} 下没有找到可预测输入，请放入图片或视频")
        return

    run_name = run_dir.name if run_dir else weights_path.stem
    save_name = f"{run_name}_{media_type}"
    predict_kwargs = build_predict_kwargs(train_args, source_path, save_name)

    DEFAULT_RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    result_dir = DEFAULT_RESULTS_DIR / save_name
    result_dir.mkdir(parents=True, exist_ok=True)

    used_args_file = result_dir / "predict_used_args.yaml"
    YAML.save(
        used_args_file,
        {
            "run_dir": str(run_dir) if run_dir else None,
            "weights": str(weights_path),
            "source": str(source_path),
            "mode": media_type,
            "items": len(items),
            "predict_kwargs": {k: (str(v) if isinstance(v, Path) else v) for k, v in predict_kwargs.items()},
            "train_args": train_args,
        },
    )

    print("=" * 60)
    print("YOLOv8 批量预测")
    print("=" * 60)
    print(f"✓ 训练目录: {run_dir if run_dir else '未关联（仅权重文件）'}")
    print(f"✓ 使用权重: {weights_path}")
    print(f"✓ 输入路径: {source_path}")
    print(f"✓ 输入类型: {media_type}")
    print(f"✓ 输入数量: {len(items)}")
    print(f"✓ 输出目录: {result_dir}")
    print("-" * 60)

    model = YOLO(str(weights_path))
    model.predict(**predict_kwargs)

    print("\n✓ 预测完成")
    print(f"  - 结果目录: {result_dir}")
    print(f"  - 参数记录: {used_args_file}")


if __name__ == "__main__":
    main()