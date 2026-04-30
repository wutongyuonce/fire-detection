package com.firedetection.backend.service;

import com.firedetection.backend.common.PageResponse;
import com.firedetection.backend.dto.task.CameraTaskCreateRequest;
import com.firedetection.backend.dto.task.TaskStatusResponse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

public interface TaskService {

    Map<String, Object> startCameraTask(CameraTaskCreateRequest request);

    Map<String, Object> stopTask(Long taskId);

    Map<String, Object> uploadVideo(MultipartFile file, String sourceName, BigDecimal confThreshold);

    Map<String, Object> getTaskDetail(Long taskId);

    TaskStatusResponse getTaskStatus(Long taskId);

    PageResponse<Map<String, Object>> listTasks(int pageNum, int pageSize, String taskType, String status,
                                                String sourceType, String startTime, String endTime);
}
