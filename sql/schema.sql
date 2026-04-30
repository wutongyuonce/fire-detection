CREATE DATABASE IF NOT EXISTS fire_detection DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fire_detection;

CREATE TABLE IF NOT EXISTS detect_task (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  task_no VARCHAR(32) NOT NULL COMMENT 'Task number',
  task_type VARCHAR(16) NOT NULL COMMENT 'Task type',
  source_type VARCHAR(32) NOT NULL COMMENT 'Source type',
  source_name VARCHAR(128) DEFAULT NULL COMMENT 'Source name',
  status VARCHAR(16) NOT NULL COMMENT 'Task status',
  video_path VARCHAR(255) DEFAULT NULL COMMENT 'Stored video path',
  frame_count INT DEFAULT 0 COMMENT 'Processed frame count',
  fire_count INT DEFAULT 0 COMMENT 'Detected fire count',
  result_summary VARCHAR(500) DEFAULT NULL COMMENT 'Result summary',
  error_message VARCHAR(500) DEFAULT NULL COMMENT 'Error message',
  start_time DATETIME DEFAULT NULL COMMENT 'Start time',
  end_time DATETIME DEFAULT NULL COMMENT 'End time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (id),
  UNIQUE KEY uk_detect_task_task_no (task_no),
  KEY idx_detect_task_status (status),
  KEY idx_detect_task_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Detection task table';

CREATE TABLE IF NOT EXISTS fire_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  event_no VARCHAR(32) NOT NULL COMMENT 'Event number',
  task_id BIGINT NOT NULL COMMENT 'Task id',
  source_type VARCHAR(32) NOT NULL COMMENT 'Source type',
  source_name VARCHAR(128) DEFAULT NULL COMMENT 'Source name',
  event_time DATETIME NOT NULL COMMENT 'Event time',
  confidence DECIMAL(5,4) NOT NULL COMMENT 'Confidence',
  duration_seconds DECIMAL(8,2) DEFAULT NULL COMMENT 'Duration seconds',
  snapshot_path VARCHAR(255) DEFAULT NULL COMMENT 'Main snapshot path',
  task_frame_no INT DEFAULT NULL COMMENT 'Frame number',
  remark VARCHAR(255) DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (id),
  UNIQUE KEY uk_fire_event_event_no (event_no),
  KEY idx_fire_event_task_id (task_id),
  KEY idx_fire_event_event_time (event_time),
  KEY idx_fire_event_source_type (source_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fire event table';

CREATE TABLE IF NOT EXISTS fire_image (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
  event_id BIGINT NOT NULL COMMENT 'Event id',
  file_name VARCHAR(128) NOT NULL COMMENT 'File name',
  file_path VARCHAR(255) NOT NULL COMMENT 'File path',
  file_url VARCHAR(255) DEFAULT NULL COMMENT 'File url',
  capture_time DATETIME NOT NULL COMMENT 'Capture time',
  source_type VARCHAR(32) NOT NULL COMMENT 'Source type',
  file_size BIGINT DEFAULT NULL COMMENT 'File size in bytes',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (id),
  KEY idx_fire_image_event_id (event_id),
  KEY idx_fire_image_capture_time (capture_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fire image table';
