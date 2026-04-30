package com.firedetection.backend.controller;

import com.firedetection.backend.common.ApiResponse;
import com.firedetection.backend.common.PageResponse;
import com.firedetection.backend.dto.task.CameraTaskCreateRequest;
import com.firedetection.backend.dto.task.TaskStatusResponse;
import com.firedetection.backend.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/camera/start")
    public ApiResponse<Map<String, Object>> startCameraTask(@Valid @RequestBody CameraTaskCreateRequest request) {
        return ApiResponse.success(taskService.startCameraTask(request));
    }

    @PostMapping("/{taskId}/stop")
    public ApiResponse<Map<String, Object>> stopTask(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.stopTask(taskId));
    }

    @PostMapping(value = "/video/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> uploadVideo(@RequestPart("file") MultipartFile file,
                                                        @RequestParam(required = false) String sourceName,
                                                        @RequestParam(required = false) BigDecimal confThreshold,
                                                        @RequestParam(required = false) String sourceType) {
        return ApiResponse.success(taskService.uploadVideo(file, sourceName, confThreshold, sourceType));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> getTaskDetail(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.getTaskDetail(taskId));
    }

    @GetMapping("/{taskId}/status")
    public ApiResponse<TaskStatusResponse> getTaskStatus(@PathVariable Long taskId) {
        return ApiResponse.success(taskService.getTaskStatus(taskId));
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> listTasks(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(taskService.listTasks(pageNum, pageSize, taskType, status, sourceType, startTime, endTime));
    }

    @DeleteMapping("/video/clear")
    public ApiResponse<Map<String, Object>> clearUploadedVideos() {
        return ApiResponse.success(taskService.clearUploadedVideos());
    }

    @PostMapping("/camera/analyze-frame")
    public ApiResponse<Map<String, Object>> analyzeCameraFrame(@RequestBody Map<String, Object> body) {
        String imageBase64 = (String) body.get("imageBase64");
        String sourceName = (String) body.get("sourceName");
        Integer frameIndex = body.get("frameIndex") != null ? ((Number) body.get("frameIndex")).intValue() : 0;
        BigDecimal confThreshold = body.get("confThreshold") != null
                ? new BigDecimal(body.get("confThreshold").toString()) : null;
        return ApiResponse.success(taskService.analyzeCameraFrame(imageBase64, sourceName, frameIndex, confThreshold));
    }
}
