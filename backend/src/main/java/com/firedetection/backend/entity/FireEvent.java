package com.firedetection.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fire_event")
public class FireEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, unique = true, length = 32)
    private String eventNo;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_name", length = 128)
    private String sourceName;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "duration_seconds", precision = 8, scale = 2)
    private BigDecimal durationSeconds;

    @Column(name = "snapshot_path", length = 255)
    private String snapshotPath;

    @Column(name = "task_frame_no")
    private Integer taskFrameNo;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public BigDecimal getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(BigDecimal durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public void setSnapshotPath(String snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public Integer getTaskFrameNo() {
        return taskFrameNo;
    }

    public void setTaskFrameNo(Integer taskFrameNo) {
        this.taskFrameNo = taskFrameNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
