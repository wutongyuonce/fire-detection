# 施工场地火焰监测系统设计文档

## 1. 文档说明

本文档基于 `requirements.md` 中确认的需求，给出施工场地火焰监测系统的总体设计方案与模块设计方案。

本文档反映截至 2026-04-30 的系统实际实现状态。

## 2. 总体设计

### 2.1 总体架构

系统采用前后端分离架构，由四个核心部分组成：

- Vue 3 前端应用（端口 5173）
- Java Spring Boot 后端服务（端口 8080）
- Python FastAPI 推理服务（端口 8000）
- MySQL 数据库 + 本地文件存储

```text
[Vue Frontend :5173]
        |
        v
[Java Spring Boot Backend :8080]
        |
        v
[Python FastAPI Inference :8000]
        |
        +----> [YOLOv8 Model Weights]
        |
        +----> [storage/snapshots/fire/]
        |
        +----> [MySQL :3306]
```

### 2.2 核心业务流

系统支持两种独立的检测方式，结果完全分离：

1. **视频分析**：用户上传视频文件 → 后端保存 → 同步调用 Python 推理服务逐帧分析 → 保存火情事件 → 返回结果。事件编号前缀 `VEVT`，使用视频内时间码（HH:mm:ss）。
2. **实时摄像头监控**：前端打开摄像头并自动录制 → 每 3 秒截取一帧发送到后端 → YOLO 检测 → 返回检测框坐标 → 前端 Canvas 叠加实时框选火焰 → 检测到火情时保存事件。事件编号前缀 `CEVT`，使用实际时间。
3. **系统概览**：仅统计和展示摄像头来源（`CAMERA`）的火情事件。

### 2.3 技术选型

#### 前端

- Vue 3 + Vite 5.4
- Element Plus（UI 组件库）
- Vue Router 4（路由管理）
- Axios（HTTP 请求）

#### 后端

- Java 17 + Spring Boot 3.3.5
- Spring Data JPA + MySQL 8
- RestTemplate（调用推理服务）

#### 推理服务

- Python 3.12 + FastAPI
- Ultralytics YOLOv8（火焰检测模型）
- OpenCV（视频帧处理）
- NumPy（图像数组处理）

#### 存储

- MySQL：保存检测任务、火情事件、火情图片等结构化数据
- 本地文件系统 `storage/` 目录：保存上传视频和火情截图

## 3. 前端模块设计

### 3.1 页面结构

系统前端包含 3 个页面，通过左侧导航栏切换：

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/dashboard` | 系统概览 | 展示今日摄像头火情数和最近一次摄像头火情信息 |
| `/video-events` | 视频分析 | 上传视频、同步分析、查看视频分析事件列表 |
| `/camera-events` | 实时监控 | 开启摄像头、自动录制、间隔采样分析、Canvas 实时框选火焰 |

### 3.2 系统概览（DashboardView）

- 展示今日摄像头火情数量（仅统计 `sourceType=CAMERA` 的事件）
- 展示最近一次摄像头火情的事件编号、发生时间、置信度、截图链接
- 无摄像头火情时显示"暂无摄像头火情事件"
- 两个卡片之间保持 28px 间距

### 3.3 视频分析（VideoEventsView）

**上传分析区域：**
- 「选择视频文件」按钮：选择本地视频文件
- 「开始分析」按钮：上传视频到后端，同步等待分析完成
- 「清空视频文件」按钮：删除 `storage/uploads/videos` 下所有已上传视频文件（二次确认）
- 分析完成后自动刷新事件列表

**事件列表：**
- 表格展示视频分析火情事件，列包含：事件编号、视频时间码、帧号、置信度
- 支持查看详情和删除操作
- 视频事件编号格式：`VEVT` + 时间戳 + 序号
- 视频时间码格式：`HH:mm:ss`（从帧号和 FPS 计算得出）

### 3.4 实时监控（CameraEventsView）

**摄像头监控区域：**
- 点击「开启摄像头」后自动开始录制 + 实时分析
- 摄像头状态通过 `useCameraMonitor` composable 管理，具有跨页面持久性——切换到系统概览或视频分析页面时，摄像头录制和分析不会中断，返回实时监控页面后自动重新绑定视频画面
- 每 3 秒截取一帧，将图片转为 Base64 发送到后端进行 YOLO 检测
- 后端返回检测框坐标（x1, y1, x2, y2, confidence），前端在 Canvas 覆盖层上实时绘制红色矩形框
- 框选区域显示火焰类别和置信度百分比标签
- 检测到火情时顶部显示红色闪烁告警"检测到火情！置信度 XX%"
- 未检测到火情时顶部显示绿色"监控中"状态
- 视频画面左上角显示"实时分析中"徽章
- 只有手动点击「关闭摄像头」才会停止录制和分析
- 录制的视频支持页面内回放

**事件列表：**
- 表格展示摄像头火情事件，列包含：事件编号、来源、发生时间、置信度
- 支持查看详情和删除操作
- 检测到新火情时自动刷新列表（通过 composable 的 `onFireDetected` 回调机制）

## 4. 后端模块设计

### 4.1 控制器划分

| 控制器 | 路径前缀 | 职责 |
| --- | --- | --- |
| TaskController | `/api/tasks` | 任务管理：视频上传分析、摄像头帧分析、任务查询 |
| FireEventController | `/api/fire-events` | 火情事件的查询、详情、删除 |
| FireImageController | `/api/fire-images` | 火情图片的查询、详情、删除 |
| DashboardController | `/api/dashboard` | 系统概览数据 |

### 4.2 服务层划分

| 服务 | 职责 |
| --- | --- |
| TaskService | 任务生命周期管理、视频上传分析、摄像头帧分析 |
| FireEventService | 火情事件 CRUD |
| FireImageService | 火情图片 CRUD |
| InferenceGatewayService | 调用 Python 推理服务的适配层 |
| DashboardService | 概览数据聚合（仅 CAMERA 类型事件） |

### 4.3 接口清单

#### 4.3.1 上传视频并同步分析

- 方法：`POST`
- 路径：`/api/tasks/video/upload`
- Content-Type：`multipart/form-data`
- 参数：`file`（视频文件）、`sourceName`（可选）、`confThreshold`（可选）、`sourceType`（可选，传 `UPLOAD_VIDEO`）
- 说明：保存视频文件到 `storage/uploads/videos/`，同步调用 Python 推理服务分析，分析完成后直接返回结果

#### 4.3.2 摄像头帧分析

- 方法：`POST`
- 路径：`/api/tasks/camera/analyze-frame`
- 请求体：
```json
{
  "imageBase64": "<base64编码的JPEG图片>",
  "sourceName": "摄像头监控",
  "frameIndex": 42,
  "confThreshold": 0.30
}
```
- 说明：接收前端截取的摄像头帧图片，转发给 Python 推理服务分析，返回检测框坐标。检测到火情时自动保存 CAM 类型任务和 CEVT 类型事件

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskNo": "CAM202604302000000001",
    "status": "FINISHED",
    "hasFire": true,
    "topConfidence": 0.9234,
    "boxCount": 1,
    "boxes": [
      {
        "x1": 120.5,
        "y1": 80.3,
        "x2": 250.7,
        "y2": 200.1,
        "confidence": 0.9234,
        "className": "fire"
      }
    ],
    "snapshotPath": "http://localhost:8080/files/snapshots/fire/CAM202604302000000001_42_20260430_200030.jpg",
    "errorMessage": null
  }
}
```

#### 4.3.3 启动摄像头检测任务（保留）

- 方法：`POST`
- 路径：`/api/tasks/camera/start`

#### 4.3.4 停止任务（保留）

- 方法：`POST`
- 路径：`/api/tasks/{taskId}/stop`

#### 4.3.5 查询任务详情

- 方法：`GET`
- 路径：`/api/tasks/{taskId}`

#### 4.3.6 查询任务状态

- 方法：`GET`
- 路径：`/api/tasks/{taskId}/status`

#### 4.3.7 查询任务列表

- 方法：`GET`
- 路径：`/api/tasks`

#### 4.3.8 清空已上传视频文件

- 方法：`DELETE`
- 路径：`/api/tasks/video/clear`
- 说明：删除 `storage/uploads/videos/` 目录下所有文件，返回删除数量

#### 4.3.9 查询火情事件列表

- 方法：`GET`
- 路径：`/api/fire-events`
- 查询参数：`pageNum`、`pageSize`、`sourceType`、`sourceName`、`startTime`、`endTime`
- 说明：支持按 `sourceType` 过滤（`CAMERA` 或 `UPLOAD_VIDEO`）

#### 4.3.10 查询火情事件详情

- 方法：`GET`
- 路径：`/api/fire-events/{id}`

#### 4.3.11 删除火情事件

- 方法：`DELETE`
- 路径：`/api/fire-events/{id}`

#### 4.3.12 查询火情图片列表

- 方法：`GET`
- 路径：`/api/fire-images`

#### 4.3.13 删除火情图片

- 方法：`DELETE`
- 路径：`/api/fire-images/{id}`

#### 4.3.14 系统概览

- 方法：`GET`
- 路径：`/api/dashboard/overview`
- 说明：仅统计 `CAMERA` 类型事件

响应示例：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "todayFireCount": 3,
    "latestEvent": {
      "eventNo": "CEVT202604302000000003",
      "eventTime": "2026-04-30 20:15:42",
      "confidence": 0.8812,
      "snapshotUrl": "/files/snapshots/fire/CAM202604302000000003_10_20260430_201542.jpg"
    }
  }
}
```

### 4.4 Java 调用 Python 推理服务接口

#### 4.4.1 视频分析

- 方法：`POST`
- 路径：`/infer/video/analyze`
- 说明：分析已保存的视频文件，返回事件列表

#### 4.4.2 单帧分析

- 方法：`POST`
- 路径：`/infer/frame/analyze`
- 说明：分析单张图片帧（Base64 编码），返回检测框坐标和截图路径

请求示例：
```json
{
  "taskNo": "CAM202604302000000001",
  "imageBase64": "<base64数据>",
  "weightsPath": "/absolute/path/to/best.pt",
  "snapshotDir": "/absolute/path/to/storage/snapshots/fire",
  "snapshotRelativeDir": "snapshots/fire",
  "sourceName": "摄像头监控",
  "frameIndex": 42,
  "device": "cpu",
  "confThreshold": 0.30
}
```

#### 4.4.3 健康检查

- 方法：`GET`
- 路径：`/health`

## 5. Python 推理服务设计

### 5.1 模型管理

- 服务启动时懒加载 YOLO 模型权重（默认路径 `../best.pt`）
- 模型在首次请求时加载，后续请求复用已加载的模型实例
- 支持通过请求参数覆盖默认权重路径

### 5.2 视频分析接口

- 接收视频文件绝对路径，逐帧检测
- 每秒采样 3 帧（`frame_interval = fps / 3`）
- 同一火情事件间隔至少 3 秒（`event_gap_frames = fps * 3`）
- 每次检测到新火情事件时保存带标注框的截图
- 使用视频时间码（帧号/FPS 计算 HH:mm:ss）作为事件时间
- 返回事件列表，每条包含 eventTime（视频时间码 HH:mm:ss）、confidence、durationSeconds、snapshotPath、taskFrameNo

### 5.3 单帧分析接口

- 接收 Base64 编码的图片帧
- 解码为 OpenCV 图像后进行 YOLO 检测
- 返回检测框坐标列表（x1, y1, x2, y2, confidence, className）
- 检测到火焰时保存带标注框的截图到 `storage/snapshots/fire/`

### 5.4 统一返回字段

- `taskNo`：任务编号
- `status`：`FINISHED` / `FAILED`
- `hasFire`：是否检测到火情
- `topConfidence`：最高置信度
- `errorMessage`：错误信息

## 6. 数据库设计

### 6.1 枚举约定

#### 任务类型 `task_type`

- `CAMERA`：摄像头实时检测
- `VIDEO`：上传视频离线分析

#### 来源类型 `source_type`

- `CAMERA`：摄像头来源（实时监控 + 摄像头帧分析）
- `UPLOAD_VIDEO`：用户上传视频来源

#### 任务状态 `status`

- `CREATED`：任务已创建
- `RUNNING`：任务执行中
- `FINISHED`：任务已完成
- `FAILED`：任务失败
- `STOPPED`：任务手动停止

#### 事件编号前缀

- `VEVT`：视频分析事件
- `CEVT`：摄像头监控事件

### 6.2 检测任务表 `detect_task`

| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint | 是 | 主键 ID |
| `task_no` | varchar(32) | 是 | 任务编号，业务唯一 |
| `task_type` | varchar(16) | 是 | 任务类型 |
| `source_type` | varchar(32) | 是 | 来源类型 |
| `source_name` | varchar(128) | 否 | 来源名称 |
| `status` | varchar(16) | 是 | 任务状态 |
| `video_path` | varchar(255) | 否 | 上传视频存储相对路径 |
| `frame_count` | int | 否 | 已处理帧数 |
| `fire_count` | int | 否 | 检测到的火情次数 |
| `result_summary` | varchar(500) | 否 | 结果摘要 |
| `error_message` | varchar(500) | 否 | 失败原因 |
| `start_time` | datetime | 否 | 实际开始时间 |
| `end_time` | datetime | 否 | 实际结束时间 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

### 6.3 火情事件表 `fire_event`

| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint | 是 | 主键 ID |
| `event_no` | varchar(32) | 是 | 事件编号，业务唯一 |
| `task_id` | bigint | 是 | 关联检测任务 ID |
| `source_type` | varchar(32) | 是 | 来源类型 |
| `source_name` | varchar(128) | 否 | 来源名称 |
| `event_time` | datetime | 是 | 火情发生时间 |
| `confidence` | decimal(5,4) | 是 | 识别置信度 |
| `duration_seconds` | decimal(8,2) | 否 | 持续时长（秒） |
| `snapshot_path` | varchar(255) | 否 | 主截图相对路径 |
| `task_frame_no` | int | 否 | 对应帧号 |
| `video_timecode` | varchar(32) | 否 | 视频时间码 HH:mm:ss |
| `remark` | varchar(255) | 否 | 备注信息 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

### 6.4 火情图片表 `fire_image`

| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint | 是 | 主键 ID |
| `event_id` | bigint | 是 | 关联火情事件 ID |
| `file_name` | varchar(128) | 是 | 文件名 |
| `file_path` | varchar(255) | 是 | 文件相对路径 |
| `file_url` | varchar(255) | 否 | 前端访问 URL |
| `capture_time` | datetime | 是 | 截图时间 |
| `source_type` | varchar(32) | 是 | 来源类型 |
| `file_size` | bigint | 否 | 文件大小（字节） |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

## 7. 文件存储设计

### 7.1 目录规划

```text
storage/
  uploads/
    videos/          ← 上传的视频文件
  snapshots/
    fire/            ← 火情检测截图（带标注框）
```

### 7.2 文件命名规则

- 上传视频：`video_yyyyMMddHHmmss_xxx.mp4`
- 视频分析截图：`{taskNo}_{frameIndex}_{yyyyMMdd}_{HHmmss}.jpg`
- 摄像头分析截图：`{taskNo}_{frameIndex}_{yyyyMMdd}_{HHmmss}.jpg`

### 7.3 文件访问方式

- 后端通过 Spring Boot 静态资源映射提供访问
- 数据库存储相对路径（如 `snapshots/fire/xxx.jpg`）
- 前端通过 `http://localhost:8080/files/{相对路径}` 访问

## 8. 数据流设计

### 8.1 上传视频分析数据流

```text
前端选择视频 → 点击"开始分析"
    → POST /api/tasks/video/upload（multipart/form-data）
    → 后端保存视频到 storage/uploads/videos/
    → 后端创建任务记录（sourceType=UPLOAD_VIDEO）
    → 后端同步调用 Python POST /infer/video/analyze
    → Python 逐帧分析（每秒3帧，事件间隔≥3秒）
    → Python 保存截图到 storage/snapshots/fire/
    → Python 返回事件列表（含视频时间码 HH:mm:ss）
    → 后端保存火情事件（eventNo 前缀 VEVT）
    → 后端返回分析结果给前端
    → 前端自动刷新事件列表
```

### 8.2 实时摄像头监控数据流

```text
前端点击"开启摄像头" → 浏览器获取摄像头流
    → useCameraMonitor composable 在模块级别管理状态
    → 创建隐藏 <video> 元素用于帧截取（不依赖组件 DOM）
    → 自动开始录制（MediaRecorder API）
    → 每3秒截取一帧（canvas.toDataURL）
    → 转为 Base64 → POST /api/tasks/camera/analyze-frame
    → 后端转发到 Python POST /infer/frame/analyze
    → Python YOLO 检测 → 返回 boxes[]（检测框坐标）
    → 检测到火情时 Python 保存截图
    → 后端保存任务和事件（eventNo 前缀 CEVT，sourceType=CAMERA）
    → 后端返回结果（含 boxes 坐标）
    → 前端 Canvas 覆盖层绘制红色火焰框选
    → composable 触发 onFireDetected 回调，事件列表自动刷新
    → 系统概览页面数据同步更新（仅统计 CAMERA 类型）
    → 用户切换页面时 composable 保持运行，摄像头不中断
    → 返回实时监控页面时自动重新绑定 visible video + canvas
```

## 9. 前端预警设计

### 9.1 摄像头实时监控预警

- 检测到火情时：顶部显示红色闪烁告警文字"检测到火情！置信度 XX%"
- 未检测到火情时：顶部显示绿色"监控中"
- 视频画面上叠加 Canvas 覆盖层：红色半透明矩形框标注火焰位置
- 框上方显示白色文字标签：`fire XX.X%`
- 视频左上角显示"实时分析中"绿色徽章

### 9.2 视频分析结果展示

- 分析完成后自动显示事件列表
- 事件详情对话框展示：事件编号、视频时间码、帧号、置信度、截图预览

## 10. 配置说明

### 10.1 后端配置（application.yml）

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fire_detection?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
app:
  storage:
    root: storage
    video-dir: uploads/videos
    snapshot-dir: snapshots/fire
  inference:
    base-url: http://localhost:8000
    weights-path: ../best.pt
    device: cpu
    default-conf-threshold: 0.30
```

### 10.2 Python 推理服务配置

- 默认权重路径：`../best.pt`（相对于 inference_service.py 所在目录）
- 默认截图目录：`storage/snapshots/fire/`
- 视频分析采样频率：每秒 3 帧
- 视频分析事件间隔：≥ 3 秒
- 摄像头帧分析间隔：3 秒（前端控制）
- 默认置信度阈值：0.30

## 11. 启动方式

### 11.1 Python 推理服务

```bash
cd /Users/a/Desktop/fire-detection
source .venv/bin/activate
python inference_service.py
```

### 11.2 Java 后端

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home
cd /Users/a/Desktop/fire-detection/backend
mvn spring-boot:run
```

### 11.3 Vue 前端

```bash
cd /Users/a/Desktop/fire-detection/frontend
npm run dev
```

## 12. 后续扩展建议

- WebSocket 实时推送火情告警
- 多摄像头集中管理
- 误报人工标注与确认
- 火情处置流程跟踪
- 用户登录与角色权限
- 数据统计与报表分析
- 告警消息外发（短信、邮件、企业微信）
