# 施工场地火焰监测系统设计文档

## 1. 文档说明

本文档基于 `requirements.md` 中确认的需求，给出施工场地火焰监测系统的总体设计方案与模块设计方案。

系统设计目标如下：

- 满足施工场地火焰监测的核心业务需求
- 结构简洁，便于快速开发和演示
- 支持前后端分离
- 支持后续逐步扩展

## 2. 总体设计

### 2.1 总体架构

系统采用前后端分离架构，整体由四个核心部分组成：

- Vue 前端应用
- Java 后端服务
- Python 模型推理服务
- 数据存储与文件存储

推荐采用如下简化架构：

1. Vue 前端负责页面展示、视频任务发起、预警展示、历史记录查询。
2. Java 后端负责业务管理、任务管理、事件管理、图片管理、接口统一对外。
3. Python 推理服务负责调用现有 YOLOv8 火焰识别模型，对摄像头视频或上传视频进行分析。
4. MySQL 用于保存结构化业务数据，服务器本地目录用于保存视频文件和火情截图。

### 2.2 架构设计原则

- 简约优先：优先实现可运行的最小可用系统。
- 分层清晰：前端、后端、推理服务职责明确。
- 易于联调：Java 后端通过 HTTP 接口调用 Python 推理服务。
- 易于扩展：后续可替换模型或增加通知能力，不影响主体结构。

### 2.3 技术选型建议

#### 前端

- Vue 3
- Vite
- Element Plus
- Axios

#### 后端

- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA 或 MyBatis-Plus
- MySQL 8

#### 推理服务

- Python 3.12
- FastAPI
- Ultralytics YOLOv8
- OpenCV

#### 存储

- MySQL：保存任务、事件、图片等业务数据
- 本地文件系统：保存上传视频、火情截图

本期不强制引入 Redis、MQ、对象存储等组件，保持部署简单。

## 3. 逻辑架构设计

### 3.1 分层结构

系统逻辑上分为以下几层：

#### 表现层

由 Vue 前端组成，负责：

- 监测页面展示
- 视频上传
- 火情预警弹窗
- 历史记录查询
- 图片详情查看

#### 业务层

由 Java 后端组成，负责：

- 用户请求接入
- 检测任务编排
- 火情事件管理
- 火情图片管理
- 状态查询与结果返回

#### 算法层

由 Python 推理服务组成，负责：

- 加载火焰检测模型
- 执行摄像头流检测
- 执行上传视频离线检测
- 输出截图与检测结果

#### 存储层

负责：

- 保存结构化业务数据
- 保存上传视频文件
- 保存火情截图文件

## 4. 部署设计

### 4.1 部署方式

结合你已确认的需求，系统采用前后端分离部署。

推荐初期部署方式如下：

- 前端部署为静态站点
- Java 后端部署为单体服务
- Python 推理服务部署为单独进程或单独服务
- MySQL 部署在同一台服务器或同一局域网数据库服务器

### 4.2 部署示意

```text
[Vue Frontend]
      |
      v
[Java Backend API]
      |
      v
[Python Inference Service]
      |
      +----> [YOLOv8 Model Weights]
      |
      +----> [Video Files / Snapshot Files]
      |
      +----> [MySQL]
```

### 4.3 接口交互方式

- 前端与 Java 后端：HTTP/HTTPS REST API
- Java 后端与 Python 推理服务：HTTP REST API
- 前端获取检测状态：可先采用轮询，后续可升级为 WebSocket

本期建议优先采用轮询方式，开发简单且足够满足简约系统需求。

## 5. 功能模块设计

### 5.1 前端模块设计

前端建议划分为以下模块：

#### 5.1.1 首页监测模块

功能说明：

- 展示系统标题、当前状态、最近告警信息
- 提供检测方式选择入口
- 展示实时视频或任务状态

核心功能：

- 选择本地摄像头监测
- 选择上传视频检测
- 显示当前系统状态：正常、检测中、告警中、分析完成

#### 5.1.2 实时预警模块

功能说明：

- 接收后端返回的火情状态
- 在页面中进行弹窗告警与红色高亮展示

核心功能：

- 弹窗提示火情事件
- 红色卡片或顶部横幅提示
- 展示时间、来源、置信度、截图缩略图

设计要点：

- 告警样式应足够醒目
- 同一事件应避免重复频繁弹窗

#### 5.1.3 视频上传模块

功能说明：

- 支持上传本地视频文件并发起检测

核心功能：

- 选择视频文件
- 显示上传进度
- 提交分析任务
- 查看任务处理状态

#### 5.1.4 历史记录模块

功能说明：

- 查询后端保存的火情事件与火情截图

核心功能：

- 按时间范围查询
- 按来源类型查询
- 查看事件详情
- 查看截图大图
- 删除事件
- 删除图片

#### 5.1.5 前端页面建议

建议最小页面结构如下：

- 登录页或简化入口页
- 监测主页
- 视频分析页
- 火情历史页

### 5.2 后端模块设计

Java 后端建议划分为以下模块：

#### 5.2.1 任务管理模块

功能说明：

- 负责接收前端发起的检测请求
- 为每次检测创建任务
- 跟踪任务状态

任务类型：

- 摄像头检测任务
- 上传视频分析任务

任务状态建议：

- CREATED
- RUNNING
- FINISHED
- FAILED

主要职责：

- 创建任务记录
- 调用 Python 推理服务
- 更新任务状态
- 返回任务结果

#### 5.2.2 火情事件管理模块

功能说明：

- 负责火情事件的保存、查询、删除

主要职责：

- 保存检测出的火情事件
- 提供事件列表查询接口
- 提供事件详情接口
- 提供删除接口

#### 5.2.3 火情图片管理模块

功能说明：

- 负责火情截图文件及其元数据管理

主要职责：

- 保存截图路径和关联关系
- 查询截图列表和详情
- 删除截图文件及数据库记录

#### 5.2.4 文件管理模块

功能说明：

- 负责上传视频文件和截图文件的统一管理

主要职责：

- 视频上传
- 文件路径生成
- 文件删除
- 静态资源访问映射

#### 5.2.5 推理服务集成模块

功能说明：

- 作为 Java 与 Python 服务之间的适配层

主要职责：

- 封装调用推理服务的 HTTP 请求
- 处理请求超时和异常
- 解析推理结果
- 将推理结果转换为业务对象

### 5.3 Python 推理服务模块设计

由于现有模型与检测代码基于 Python，因此建议保留独立推理服务。

#### 5.3.1 模型管理模块

功能说明：

- 加载训练完成的模型权重
- 提供统一推理入口

主要职责：

- 服务启动时加载模型
- 支持配置模型权重路径
- 对外暴露健康检查接口

#### 5.3.2 摄像头检测模块

功能说明：

- 处理本地摄像头视频输入
- 对连续视频帧执行检测

主要职责：

- 打开摄像头或视频采集源
- 按帧检测火焰目标
- 检测到火情时截图
- 将事件数据返回给 Java 后端

#### 5.3.3 上传视频分析模块

功能说明：

- 处理后端传入的视频文件
- 对视频执行离线分析

主要职责：

- 打开视频文件
- 分帧检测
- 保存火情截图
- 汇总分析结果

#### 5.3.4 结果输出模块

功能说明：

- 将推理结果结构化输出给 Java 后端

输出内容建议包括：

- 任务编号
- 是否检测到火情
- 火情发生时间
- 置信度
- 截图路径
- 结果摘要

## 6. 数据流设计

### 6.1 实时摄像头检测数据流

```text
前端发起监测
    ->
Java 后端创建任务
    ->
调用 Python 推理服务
    ->
推理服务读取摄像头并检测
    ->
生成火情截图与事件结果
    ->
Java 后端写入 MySQL
    ->
前端轮询任务状态并显示预警
```

### 6.2 上传视频分析数据流

```text
前端上传视频
    ->
Java 后端保存视频文件
    ->
创建分析任务
    ->
调用 Python 推理服务分析视频
    ->
生成截图与事件结果
    ->
Java 后端保存数据
    ->
前端查看分析结果
```

## 7. 数据库设计

### 7.1 设计目标

数据库设计遵循以下原则：

- 结构简洁，优先支撑当前核心业务
- 表关系清晰，便于后端快速开发
- 字段预留适度扩展空间，但不引入复杂冗余
- 文件内容存储在服务器目录，数据库仅保存元数据和访问路径

本期建议至少设计三张核心业务表：

- 检测任务表 `detect_task`
- 火情事件表 `fire_event`
- 火情图片表 `fire_image`

### 7.2 枚举值约定

为便于前后端和推理服务统一，建议采用如下枚举约定：

#### 任务类型 `task_type`

- `CAMERA`：摄像头实时检测
- `VIDEO`：上传视频离线分析

#### 来源类型 `source_type`

- `CAMERA`：摄像头来源
- `UPLOAD_VIDEO`：用户上传视频来源

#### 任务状态 `status`

- `CREATED`：任务已创建
- `RUNNING`：任务执行中
- `FINISHED`：任务已完成
- `FAILED`：任务失败
- `STOPPED`：任务手动停止

### 7.3 表关系设计

- 一个检测任务可关联多个火情事件
- 一个火情事件可关联一张或多张火情图片
- 删除检测任务时，不建议级联物理删除事件和图片，避免误删历史数据
- 删除火情事件时，可同步删除其关联图片记录及文件

关系示意如下：

```text
detect_task 1 ---- n fire_event 1 ---- n fire_image
```

### 7.4 检测任务表

表名：`detect_task`

用途说明：

- 保存每一次摄像头检测任务或视频分析任务的主记录
- 用于前端查看处理状态、结果摘要和失败原因

字段设计如下：

| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint | 是 | 主键 ID |
| `task_no` | varchar(32) | 是 | 任务编号，业务唯一 |
| `task_type` | varchar(16) | 是 | 任务类型，`CAMERA`/`VIDEO` |
| `source_type` | varchar(32) | 是 | 来源类型，`CAMERA`/`UPLOAD_VIDEO` |
| `source_name` | varchar(128) | 否 | 来源名称，如摄像头编号、视频文件名 |
| `status` | varchar(16) | 是 | 任务状态 |
| `video_path` | varchar(255) | 否 | 上传视频存储路径 |
| `frame_count` | int | 否 | 已处理帧数 |
| `fire_count` | int | 否 | 检测到的火情次数 |
| `result_summary` | varchar(500) | 否 | 结果摘要 |
| `error_message` | varchar(500) | 否 | 失败原因 |
| `start_time` | datetime | 否 | 实际开始时间 |
| `end_time` | datetime | 否 | 实际结束时间 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

索引建议：

- 唯一索引：`uk_detect_task_task_no(task_no)`
- 普通索引：`idx_detect_task_status(status)`
- 普通索引：`idx_detect_task_created_at(created_at)`

### 7.5 火情事件表

表名：`fire_event`

用途说明：

- 保存火情识别结果的核心业务信息
- 一条记录代表一次有效火情事件

字段设计如下：

| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint | 是 | 主键 ID |
| `event_no` | varchar(32) | 是 | 事件编号，业务唯一 |
| `task_id` | bigint | 是 | 关联检测任务 ID |
| `source_type` | varchar(32) | 是 | 来源类型 |
| `source_name` | varchar(128) | 否 | 来源名称 |
| `event_time` | datetime | 是 | 火情发生时间 |
| `confidence` | decimal(5,4) | 是 | 识别置信度 |
| `duration_seconds` | decimal(8,2) | 否 | 持续时长，适用于实时检测 |
| `snapshot_path` | varchar(255) | 否 | 主截图路径 |
| `task_frame_no` | int | 否 | 对应帧号 |
| `remark` | varchar(255) | 否 | 备注信息 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

索引建议：

- 唯一索引：`uk_fire_event_event_no(event_no)`
- 普通索引：`idx_fire_event_task_id(task_id)`
- 普通索引：`idx_fire_event_event_time(event_time)`
- 普通索引：`idx_fire_event_source_type(source_type)`

### 7.6 火情图片表

表名：`fire_image`

用途说明：

- 保存火情截图文件元数据
- 支持一个事件关联多张截图

字段设计如下：

| 字段名 | 类型 | 非空 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint | 是 | 主键 ID |
| `event_id` | bigint | 是 | 关联火情事件 ID |
| `file_name` | varchar(128) | 是 | 文件名 |
| `file_path` | varchar(255) | 是 | 文件相对路径或映射路径 |
| `file_url` | varchar(255) | 否 | 前端访问 URL |
| `capture_time` | datetime | 是 | 截图时间 |
| `source_type` | varchar(32) | 是 | 来源类型 |
| `file_size` | bigint | 否 | 文件大小，单位字节 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

索引建议：

- 普通索引：`idx_fire_image_event_id(event_id)`
- 普通索引：`idx_fire_image_capture_time(capture_time)`

### 7.7 MySQL 建表示例

以下 SQL 可作为后续实现的初版建表脚本：

```sql
CREATE TABLE `detect_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_no` VARCHAR(32) NOT NULL COMMENT '任务编号',
  `task_type` VARCHAR(16) NOT NULL COMMENT '任务类型',
  `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型',
  `source_name` VARCHAR(128) DEFAULT NULL COMMENT '来源名称',
  `status` VARCHAR(16) NOT NULL COMMENT '任务状态',
  `video_path` VARCHAR(255) DEFAULT NULL COMMENT '视频路径',
  `frame_count` INT DEFAULT 0 COMMENT '已处理帧数',
  `fire_count` INT DEFAULT 0 COMMENT '火情次数',
  `result_summary` VARCHAR(500) DEFAULT NULL COMMENT '结果摘要',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '失败信息',
  `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_detect_task_task_no` (`task_no`),
  KEY `idx_detect_task_status` (`status`),
  KEY `idx_detect_task_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测任务表';

CREATE TABLE `fire_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_no` VARCHAR(32) NOT NULL COMMENT '事件编号',
  `task_id` BIGINT NOT NULL COMMENT '任务ID',
  `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型',
  `source_name` VARCHAR(128) DEFAULT NULL COMMENT '来源名称',
  `event_time` DATETIME NOT NULL COMMENT '火情发生时间',
  `confidence` DECIMAL(5,4) NOT NULL COMMENT '置信度',
  `duration_seconds` DECIMAL(8,2) DEFAULT NULL COMMENT '持续时间',
  `snapshot_path` VARCHAR(255) DEFAULT NULL COMMENT '主截图路径',
  `task_frame_no` INT DEFAULT NULL COMMENT '帧号',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fire_event_event_no` (`event_no`),
  KEY `idx_fire_event_task_id` (`task_id`),
  KEY `idx_fire_event_event_time` (`event_time`),
  KEY `idx_fire_event_source_type` (`source_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火情事件表';

CREATE TABLE `fire_image` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id` BIGINT NOT NULL COMMENT '事件ID',
  `file_name` VARCHAR(128) NOT NULL COMMENT '文件名',
  `file_path` VARCHAR(255) NOT NULL COMMENT '文件路径',
  `file_url` VARCHAR(255) DEFAULT NULL COMMENT '文件访问URL',
  `capture_time` DATETIME NOT NULL COMMENT '截图时间',
  `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_fire_image_event_id` (`event_id`),
  KEY `idx_fire_image_capture_time` (`capture_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火情图片表';
```

### 7.8 实体对象建议

后端 Java 可对应设计如下实体：

- `DetectTask`
- `FireEvent`
- `FireImage`

同时建议补充以下 DTO：

- `CameraTaskCreateRequest`
- `VideoTaskUploadResponse`
- `TaskStatusResponse`
- `FireEventQueryRequest`
- `FireImageQueryRequest`

## 8. 接口设计

### 8.1 接口设计原则

- 接口路径清晰，按资源分类
- 对前端统一返回结构
- 对上传类接口采用 `multipart/form-data`
- 对查询类接口支持分页
- 对删除接口返回明确处理结果

### 8.2 统一返回结构

建议统一返回格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页返回建议：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "pageNum": 1,
    "pageSize": 10
  }
}
```

### 8.3 前端调用 Java 后端接口

#### 8.3.1 启动摄像头检测任务

- 方法：`POST`
- 路径：`/api/tasks/camera/start`
- 说明：创建摄像头实时检测任务，并调用 Python 推理服务启动检测

请求示例：

```json
{
  "sourceName": "camera-0",
  "cameraIndex": 0,
  "confThreshold": 0.30,
  "holdSeconds": 1.50,
  "cooldownSeconds": 8.00
}
```

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1,
    "taskNo": "TASK202604290001",
    "status": "RUNNING"
  }
}
```

#### 8.3.2 停止摄像头检测任务

- 方法：`POST`
- 路径：`/api/tasks/{taskId}/stop`
- 说明：停止正在运行的摄像头检测任务

响应字段建议：

- `taskId`
- `status`
- `message`

#### 8.3.3 上传视频并创建分析任务

- 方法：`POST`
- 路径：`/api/tasks/video/upload`
- 说明：上传视频文件，创建离线分析任务
- 请求类型：`multipart/form-data`

表单字段建议：

- `file`：视频文件
- `sourceName`：来源名称，可选
- `confThreshold`：置信度阈值，可选

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 2,
    "taskNo": "TASK202604290002",
    "status": "CREATED",
    "videoPath": "storage/uploads/videos/video_20260429103010.mp4"
  }
}
```

#### 8.3.4 查询任务详情

- 方法：`GET`
- 路径：`/api/tasks/{taskId}`
- 说明：返回任务主信息和统计信息

响应字段建议：

- `taskId`
- `taskNo`
- `taskType`
- `sourceType`
- `sourceName`
- `status`
- `frameCount`
- `fireCount`
- `startTime`
- `endTime`
- `resultSummary`
- `errorMessage`

#### 8.3.5 查询任务状态

- 方法：`GET`
- 路径：`/api/tasks/{taskId}/status`
- 说明：供前端轮询任务状态和最新火情结果

响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": 1,
    "status": "RUNNING",
    "hasActiveFire": true,
    "latestEvent": {
      "eventId": 10,
      "eventNo": "EVENT202604290001",
      "eventTime": "2026-04-29 10:31:22",
      "confidence": 0.9234,
      "snapshotUrl": "/static/snapshots/fire/fire_20260429103122.jpg"
    }
  }
}
```

#### 8.3.6 查询任务列表

- 方法：`GET`
- 路径：`/api/tasks`
- 说明：查询检测任务列表

查询参数建议：

- `pageNum`
- `pageSize`
- `taskType`
- `status`
- `sourceType`
- `startTime`
- `endTime`

#### 8.3.7 查询火情事件列表

- 方法：`GET`
- 路径：`/api/fire-events`
- 说明：分页查询火情事件

查询参数建议：

- `pageNum`
- `pageSize`
- `sourceType`
- `sourceName`
- `startTime`
- `endTime`

响应字段建议：

- `id`
- `eventNo`
- `taskId`
- `sourceType`
- `sourceName`
- `eventTime`
- `confidence`
- `snapshotUrl`

#### 8.3.8 查询火情事件详情

- 方法：`GET`
- 路径：`/api/fire-events/{id}`
- 说明：返回事件详情及关联图片列表

响应字段建议：

- `id`
- `eventNo`
- `taskId`
- `sourceType`
- `sourceName`
- `eventTime`
- `confidence`
- `durationSeconds`
- `snapshotUrl`
- `images`

#### 8.3.9 删除火情事件

- 方法：`DELETE`
- 路径：`/api/fire-events/{id}`
- 说明：删除事件，并同步删除关联图片及对应文件

响应示例：

```json
{
  "code": 200,
  "message": "删除成功",
  "data": true
}
```

#### 8.3.10 查询火情图片列表

- 方法：`GET`
- 路径：`/api/fire-images`
- 说明：分页查询火情图片

查询参数建议：

- `pageNum`
- `pageSize`
- `eventId`
- `sourceType`
- `startTime`
- `endTime`

#### 8.3.11 查询火情图片详情

- 方法：`GET`
- 路径：`/api/fire-images/{id}`
- 说明：返回图片元数据与关联事件信息

#### 8.3.12 删除火情图片

- 方法：`DELETE`
- 路径：`/api/fire-images/{id}`
- 说明：删除图片记录及实际文件

#### 8.3.13 获取系统概览信息

- 方法：`GET`
- 路径：`/api/dashboard/overview`
- 说明：用于首页展示简洁概览信息

响应字段建议：

- `runningTaskCount`
- `todayFireCount`
- `latestEvent`
- `systemStatus`

### 8.4 Java 调用 Python 推理服务接口

#### 8.4.1 健康检查

- 方法：`GET`
- 路径：`/health`
- 说明：检查推理服务是否可用

响应示例：

```json
{
  "status": "UP",
  "modelLoaded": true
}
```

#### 8.4.2 启动摄像头检测

- 方法：`POST`
- 路径：`/infer/camera/start`
- 说明：由 Java 后端调用，创建推理侧任务

请求示例：

```json
{
  "taskNo": "TASK202604290001",
  "cameraIndex": 0,
  "weightsPath": "weights/best.pt",
  "confThreshold": 0.30,
  "holdSeconds": 1.50,
  "cooldownSeconds": 8.00
}
```

#### 8.4.3 停止摄像头检测

- 方法：`POST`
- 路径：`/infer/camera/stop`
- 说明：停止推理侧实时任务

#### 8.4.4 分析上传视频

- 方法：`POST`
- 路径：`/infer/video/analyze`
- 说明：分析后端已保存的视频文件

请求示例：

```json
{
  "taskNo": "TASK202604290002",
  "videoPath": "storage/uploads/videos/video_20260429103010.mp4",
  "weightsPath": "weights/best.pt",
  "confThreshold": 0.30
}
```

#### 8.4.5 查询推理任务状态

- 方法：`GET`
- 路径：`/infer/tasks/{taskNo}`
- 说明：返回推理状态和最新事件

响应字段建议：

- `taskNo`
- `status`
- `frameCount`
- `fireCount`
- `latestEvent`
- `errorMessage`

### 8.5 推荐控制器划分

Java 后端建议按以下方式组织控制器：

- `TaskController`
- `FireEventController`
- `FireImageController`
- `DashboardController`

服务层建议：

- `TaskService`
- `FireEventService`
- `FireImageService`
- `InferenceGatewayService`
- `FileStorageService`

### 8.6 异常码建议

为便于前端统一处理，建议定义基础业务错误码：

- `200`：成功
- `400`：请求参数错误
- `404`：资源不存在
- `409`：任务状态冲突
- `500`：系统内部错误
- `10001`：视频上传失败
- `10002`：摄像头启动失败
- `10003`：推理服务不可用
- `10004`：任务执行失败
- `10005`：文件删除失败

## 9. 前端预警设计

### 9.1 状态设计

前端页面状态建议包括：

- 正常
- 检测中
- 预警中
- 分析完成
- 分析失败

### 9.2 预警交互设计

当接收到火情事件时：

- 页面顶部显示红色告警条
- 页面中央或右上角弹出告警弹窗
- 页面展示截图缩略图
- 页面展示时间、来源、置信度

### 9.3 防重复提示设计

为避免同一火情短时间反复弹窗，建议：

- 使用事件编号去重
- 对同一事件仅首次触发强提醒

## 10. 文件存储设计

### 10.1 目录规划建议

建议在服务端统一规划目录：

```text
storage/
  uploads/
    videos/
  snapshots/
    fire/
```

### 10.2 文件命名建议

- 上传视频：`video_yyyyMMddHHmmss_xxx.mp4`
- 火情截图：`fire_yyyyMMddHHmmss_xxx.jpg`

### 10.3 文件访问方式

- 后端提供静态资源访问路径
- 数据库只存储相对路径或可映射路径，避免写死绝对路径

## 11. 安全与异常设计

### 11.1 基础安全

本期建议实现基础安全能力：

- 限制上传视频类型
- 限制上传视频大小
- 对上传文件名进行规范化处理
- 对接口参数做基本校验

### 11.2 异常处理

系统应处理以下异常场景：

- 摄像头无法打开
- 视频文件损坏
- 模型服务启动失败
- 推理超时
- 数据库写入失败
- 文件保存失败

异常处理原则：

- 对前端返回明确错误信息
- 后端记录错误日志
- 任务状态更新为失败

## 12. 非功能设计

### 12.1 性能设计

- 本期支持单机部署
- 优先保证中小规模场景稳定运行
- 推理服务应尽量复用已加载模型，避免重复加载

### 12.2 可维护性设计

- 后端分模块开发
- 前端组件化开发
- 推理逻辑独立封装
- 配置项统一管理

### 12.3 可扩展性设计

后续可扩展：

- WebSocket 实时推送
- 多摄像头管理
- 误报确认
- 告警消息外发
- 对象存储
- 审计日志

## 13. 开发落地建议

建议按以下顺序推进开发：

1. 搭建 Vue 前端基础页面框架。
2. 搭建 Spring Boot 后端基础工程。
3. 将现有 Python 模型推理脚本封装为 FastAPI 服务。
4. 打通“上传视频 -> 推理 -> 保存事件 -> 前端展示”主流程。
5. 打通“摄像头检测 -> 预警展示 -> 保存截图”实时流程。
6. 完善历史记录查询与删除功能。

## 14. 总结

本设计方案围绕“简约、可落地、可扩展”三个原则展开，采用 Vue 前端、Java 后端、Python 推理服务的组合方式，能够较好承接你当前已有的 YOLOv8 火焰识别模型，并快速构建出一个面向施工场地的视频火焰监测系统。

该方案优先实现以下核心闭环：

- 视频输入
- 火焰检测
- 页面预警
- 事件落库
- 图片管理

在此基础上，后续可以逐步扩展消息通知、统计分析和多摄像头管理能力。
