CREATE DATABASE IF NOT EXISTS fire_detection DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fire_detection;

CREATE TABLE IF NOT EXISTS detect_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  task_no VARCHAR(32) NOT NULL COMMENT '任务编号，业务唯一',
  task_type VARCHAR(16) NOT NULL COMMENT '任务类型：CAMERA / VIDEO',
  source_type VARCHAR(32) NOT NULL COMMENT '来源类型：CAMERA / UPLOAD_VIDEO',
  source_name VARCHAR(128) DEFAULT NULL COMMENT '来源名称',
  status VARCHAR(16) NOT NULL COMMENT '任务状态：CREATED / RUNNING / FINISHED / FAILED / STOPPED',
  video_path VARCHAR(255) DEFAULT NULL COMMENT '上传视频存储相对路径',
  frame_count INT DEFAULT 0 COMMENT '已处理帧数',
  fire_count INT DEFAULT 0 COMMENT '检测到的火情次数',
  result_summary VARCHAR(500) DEFAULT NULL COMMENT '结果摘要',
  error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  start_time DATETIME DEFAULT NULL COMMENT '实际开始时间',
  end_time DATETIME DEFAULT NULL COMMENT '实际结束时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_detect_task_task_no (task_no),
  KEY idx_detect_task_status (status),
  KEY idx_detect_task_source_type (source_type),
  KEY idx_detect_task_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检测任务表';

CREATE TABLE IF NOT EXISTS fire_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  event_no VARCHAR(32) NOT NULL COMMENT '事件编号，业务唯一，VEVT=视频事件 / CEVT=摄像头事件',
  task_id BIGINT NOT NULL COMMENT '关联检测任务ID',
  source_type VARCHAR(32) NOT NULL COMMENT '来源类型：CAMERA / UPLOAD_VIDEO',
  source_name VARCHAR(128) DEFAULT NULL COMMENT '来源名称',
  event_time DATETIME NOT NULL COMMENT '火情发生时间（视频分析为视频时间码 HH:mm:ss 对应的 datetime，摄像头为实际时间）',
  confidence DECIMAL(5,4) NOT NULL COMMENT '识别置信度',
  duration_seconds DECIMAL(8,2) DEFAULT NULL COMMENT '持续时长（秒）',
  snapshot_path VARCHAR(255) DEFAULT NULL COMMENT '主截图相对路径',
  task_frame_no INT DEFAULT NULL COMMENT '对应帧号',
  video_timecode VARCHAR(32) DEFAULT NULL COMMENT '视频时间码 HH:mm:ss（仅视频分析类型有效）',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fire_event_event_no (event_no),
  KEY idx_fire_event_task_id (task_id),
  KEY idx_fire_event_event_time (event_time),
  KEY idx_fire_event_source_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火情事件表';

CREATE TABLE IF NOT EXISTS fire_image (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  event_id BIGINT NOT NULL COMMENT '关联火情事件ID',
  file_name VARCHAR(128) NOT NULL COMMENT '文件名',
  file_path VARCHAR(255) NOT NULL COMMENT '文件相对路径',
  file_url VARCHAR(255) DEFAULT NULL COMMENT '前端访问URL',
  capture_time DATETIME NOT NULL COMMENT '截图时间',
  source_type VARCHAR(32) NOT NULL COMMENT '来源类型：CAMERA / UPLOAD_VIDEO',
  file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_fire_image_event_id (event_id),
  KEY idx_fire_image_capture_time (capture_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='火情图片表';
