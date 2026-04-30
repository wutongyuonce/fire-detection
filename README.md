# 施工场地火焰监测系统

基于 YOLOv8 的施工场地视频火焰实时监测系统，支持上传视频离线分析和本地摄像头实时监控两种模式，能够自动识别火焰目标、实时框选标注、保存火情事件与截图。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 · Vite 5 · Element Plus · Vue Router 4 · Axios |
| 后端 | Java 17 · Spring Boot 3.3.5 · Spring Data JPA · MySQL 8 |
| 推理 | Python 3.12 · FastAPI · Ultralytics YOLOv8 · OpenCV |
| 存储 | MySQL（结构化数据） · 本地文件系统（视频/截图） |

## 系统架构

```
┌────────────────────────────┐
│   Vue 3 Frontend (:5173)   │
│  Dashboard / Video / Camera │
└─────────────┬──────────────┘
              │ HTTP
              ▼
┌────────────────────────────┐
│  Spring Boot Backend (:8080)│
│  Task · Event · Dashboard   │
└──────┬──────────┬──────────┘
       │          │
       ▼          ▼
  ┌─────────┐  ┌──────────────┐
  │ MySQL   │  │ FastAPI (:8000)│
  │ :3306   │  │  YOLOv8 推理   │
  └─────────┘  └──────────────┘
```

## 功能概览

### 系统概览（Dashboard）

- 今日摄像头火情数量统计
- 最近一次摄像头火情详情（事件编号、时间、置信度、截图）
- 仅展示摄像头来源（`CAMERA`）数据，视频分析数据不混入

### 视频分析

- 上传本地视频文件，后端同步调用 YOLO 逐帧分析
- 分析完成后自动刷新火情事件列表
- 事件使用视频内时间码（`HH:mm:ss`）记录
- 支持事件详情查看、删除、清空已上传视频

### 实时摄像头监控

- 开启摄像头后自动录制，每 3 秒截取一帧进行 YOLO 检测
- 检测到火焰时，前端 Canvas 实时绘制红色半透明框选区域，显示类别和置信度
- 摄像头状态跨页面持久化：切换到其他页面时录制和分析不中断
- 检测到火情时自动保存事件和截图，自动刷新事件列表
- 录制视频支持页面内回放

## 前置条件

- **JDK 17**（推荐使用 [jenv](https://github.com/jenv/jenv) 管理）
- **Maven 3.8+**
- **Node.js 18+**（含 npm）
- **Python 3.12+**（推荐使用 [uv](https://github.com/astral-sh/uv) 管理依赖）
- **MySQL 8.0+**
- **摄像头设备**（实时监控功能需要）

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/<your-username>/fire-detection.git
cd fire-detection
```

### 2. 初始化数据库

```bash
mysql -u root -p < sql/schema.sql
```

这会创建 `fire_detection` 数据库及 `detect_task`、`fire_event`、`fire_image` 三张表。

如需修改数据库连接信息，编辑 [backend/src/main/resources/application.yml](file:///Users/a/Desktop/fire-detection/backend/src/main/resources/application.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fire_detection?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
```

### 3. 启动 Python 推理服务（端口 8000）

```bash
# 使用 uv 安装依赖（推荐）
uv sync

# 或使用 pip
pip install -r requirements.txt

# 启动推理服务
uvicorn inference_service:app --host 0.0.0.0 --port 8000
```

推理服务加载项目根目录下的 [best.pt](file:///Users/a/Desktop/fire-detection/best.pt) 模型权重文件。首次启动时 Ultralytics 会自动下载 YOLOv8 基础依赖。

### 4. 启动 Java 后端（端口 8080）

```bash
cd backend

# 确保 JAVA_HOME 指向 JDK 17（使用 jenv 时）
export JAVA_HOME=$(jenv prefix)

mvn spring-boot:run
```

后端启动后会自动创建 `storage/uploads/videos/` 和 `storage/snapshots/fire/` 目录。

### 5. 启动前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173 即可使用。

## 项目结构

```
fire-detection/
├── frontend/                  # Vue 3 前端
│   ├── src/
│   │   ├── api/               # API 接口定义（Axios）
│   │   ├── components/        # 公共组件（AppLayout 侧边栏布局）
│   │   ├── composables/       # 组合式函数（useCameraMonitor 摄像头状态管理）
│   │   ├── router/            # 路由配置
│   │   ├── views/             # 页面视图
│   │   │   ├── DashboardView.vue
│   │   │   ├── VideoEventsView.vue
│   │   │   └── CameraEventsView.vue
│   │   ├── App.vue
│   │   └── main.js
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
│
├── backend/                   # Spring Boot 后端
│   ├── src/main/java/com/firedetection/backend/
│   │   ├── controller/        # REST 控制器
│   │   ├── service/           # 业务服务层
│   │   ├── repository/        # JPA 数据访问层
│   │   ├── entity/            # 数据库实体
│   │   ├── dto/               # 数据传输对象
│   │   ├── enums/             # 枚举定义
│   │   ├── config/            # 配置类
│   │   └── common/            # 通用响应封装
│   ├── src/main/resources/
│   │   └── application.yml
│   ├── storage/               # 本地文件存储（不入 Git）
│   │   ├── uploads/videos/    # 上传的视频文件
│   │   └── snapshots/fire/    # 火情截图
│   └── pom.xml
│
├── inference_service.py       # Python FastAPI 推理服务
├── predict.py                 # 独立预测脚本
├── train2.py                  # 模型训练脚本
├── best.pt                    # YOLOv8 火焰检测模型权重
├── sql/
│   └── schema.sql             # 数据库初始化脚本
├── design.md                  # 系统设计文档
├── requirements.md            # 需求文档
├── pyproject.toml             # Python 项目配置
└── .gitignore
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/tasks/video/upload` | 上传视频并同步分析 |
| `DELETE` | `/api/tasks/video/clear` | 清空已上传视频文件 |
| `POST` | `/api/tasks/camera/analyze-frame` | 摄像头单帧分析（Base64 图片） |
| `GET` | `/api/tasks/{id}` | 查询任务状态 |
| `GET` | `/api/fire-events` | 分页查询火情事件（支持 `sourceType` 过滤） |
| `GET` | `/api/fire-events/{id}` | 火情事件详情 |
| `DELETE` | `/api/fire-events/{id}` | 删除火情事件 |
| `GET` | `/api/dashboard/overview` | 系统概览数据（仅 CAMERA 类型） |

Python 推理服务接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/infer/video/analyze` | 视频文件逐帧分析 |
| `POST` | `/infer/frame/analyze` | 单帧图片分析（返回检测框坐标） |

## 数据模型

- **detect_task** — 检测任务表，记录每次视频分析或摄像头监控任务
- **fire_event** — 火情事件表，事件编号 `VEVT` 前缀为视频分析、`CEVT` 前缀为摄像头监控
- **fire_image** — 火情截图表，关联事件记录对应的截图文件

详细字段定义见 [sql/schema.sql](file:///Users/a/Desktop/fire-detection/sql/schema.sql)。

## 注意事项

- 推理服务默认使用 CPU 进行推理，如有 NVIDIA GPU 可修改 `application.yml` 中 `device` 为 `cuda`
- 上传视频最大支持 500MB，可在 `application.yml` 中调整 `max-file-size`
- YOLO 检测置信度阈值默认 0.30，可在 `application.yml` 中调整 `default-conf-threshold`
- 实时监控的帧采样间隔默认 3 秒，可在前端 `useCameraMonitor.js` 中修改 `ANALYZE_INTERVAL`
- `storage/` 目录下的视频和截图文件不纳入版本控制

## 许可证

本项目仅供学习与研究使用。
