package com.firedetection.backend.service.impl;

import com.firedetection.backend.common.PageResponse;
import com.firedetection.backend.dto.inference.FrameAnalyzeResponse;
import com.firedetection.backend.dto.inference.VideoAnalyzeResponse;
import com.firedetection.backend.dto.task.CameraTaskCreateRequest;
import com.firedetection.backend.dto.task.TaskStatusResponse;
import com.firedetection.backend.entity.DetectTask;
import com.firedetection.backend.entity.FireEvent;
import com.firedetection.backend.entity.FireImage;
import com.firedetection.backend.enums.SourceType;
import com.firedetection.backend.enums.TaskStatus;
import com.firedetection.backend.enums.TaskType;
import com.firedetection.backend.repository.DetectTaskRepository;
import com.firedetection.backend.repository.FireEventRepository;
import com.firedetection.backend.repository.FireImageRepository;
import com.firedetection.backend.service.InferenceGatewayService;
import com.firedetection.backend.service.TaskService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskServiceImpl implements TaskService {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DetectTaskRepository detectTaskRepository;
    private final FireEventRepository fireEventRepository;
    private final FireImageRepository fireImageRepository;
    private final InferenceGatewayService inferenceGatewayService;

    @Value("${app.storage.root:storage}")
    private String storageRoot;

    @Value("${app.storage.video-dir:uploads/videos}")
    private String videoDir;

    @Value("${app.storage.snapshot-dir:snapshots/fire}")
    private String snapshotDir;

    public TaskServiceImpl(DetectTaskRepository detectTaskRepository,
                           FireEventRepository fireEventRepository,
                           FireImageRepository fireImageRepository,
                           InferenceGatewayService inferenceGatewayService) {
        this.detectTaskRepository = detectTaskRepository;
        this.fireEventRepository = fireEventRepository;
        this.fireImageRepository = fireImageRepository;
        this.inferenceGatewayService = inferenceGatewayService;
    }

    @Override
    @Transactional
    public Map<String, Object> startCameraTask(CameraTaskCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        DetectTask task = new DetectTask();
        task.setTaskNo(generateBizNo("TASK"));
        task.setTaskType(TaskType.CAMERA.name());
        task.setSourceType(SourceType.CAMERA.name());
        task.setSourceName(request.sourceName() != null ? request.sourceName() : "camera-" + request.cameraIndex());
        task.setStatus(TaskStatus.RUNNING.name());
        task.setFrameCount(0);
        task.setFireCount(0);
        task.setResultSummary("Camera task created");
        task.setStartTime(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        DetectTask saved = detectTaskRepository.save(task);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", saved.getId());
        data.put("taskNo", saved.getTaskNo());
        data.put("status", saved.getStatus());
        data.put("cameraIndex", request.cameraIndex());
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> stopTask(Long taskId) {
        DetectTask task = getTaskOrThrow(taskId);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TaskStatus.STOPPED.name());
        task.setEndTime(now);
        task.setUpdatedAt(now);
        detectTaskRepository.save(task);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("status", task.getStatus());
        data.put("message", "Task stopped");
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> uploadVideo(MultipartFile file, String sourceName, BigDecimal confThreshold, String sourceTypeParam) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded video file is empty");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "video.mp4";
        String extension = extractExtension(originalName);
        String storedFileName = "video_" + LocalDateTime.now().format(NO_FORMATTER) + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
        String relativePath = videoDir + "/" + storedFileName;
        Path targetPath = resolveStorageFile(relativePath);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save uploaded video", e);
        }

        LocalDateTime now = LocalDateTime.now();
        DetectTask task = new DetectTask();
        task.setTaskNo(generateBizNo("TASK"));
        task.setTaskType(TaskType.VIDEO.name());
        SourceType resolvedSourceType = SourceType.CAMERA.name().equals(sourceTypeParam) ? SourceType.CAMERA : SourceType.UPLOAD_VIDEO;
        task.setSourceType(resolvedSourceType.name());
        task.setSourceName(sourceName != null && !sourceName.isBlank() ? sourceName : originalName);
        task.setStatus(TaskStatus.RUNNING.name());
        task.setVideoPath(relativePath);
        task.setFrameCount(0);
        task.setFireCount(0);
        task.setResultSummary("Video task created");
        task.setStartTime(now);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        DetectTask saved = detectTaskRepository.save(task);

        try {
            VideoAnalyzeResponse response = inferenceGatewayService.analyzeVideo(saved, confThreshold);
            applyAnalyzeResponse(saved, response);
        } catch (ResponseStatusException ex) {
            markTaskFailed(saved, ex.getReason());
            throw ex;
        } catch (Exception ex) {
            markTaskFailed(saved, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Video inference failed", ex);
        }

        DetectTask refreshed = getTaskOrThrow(saved.getId());
        return toTaskMap(refreshed);
    }

    @Override
    public Map<String, Object> getTaskDetail(Long taskId) {
        return toTaskMap(getTaskOrThrow(taskId));
    }

    @Override
    public TaskStatusResponse getTaskStatus(Long taskId) {
        DetectTask task = getTaskOrThrow(taskId);
        Map<String, Object> latestEvent = fireEventRepository.findTopByTaskIdOrderByEventTimeDesc(taskId)
                .map(this::toLatestEventMap)
                .orElse(null);
        return new TaskStatusResponse(task.getId(), task.getStatus(), latestEvent != null, latestEvent);
    }

    @Override
    public PageResponse<Map<String, Object>> listTasks(int pageNum, int pageSize, String taskType, String status,
                                                       String sourceType, String startTime, String endTime) {
        Pageable pageable = PageRequest.of(Math.max(pageNum - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<DetectTask> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(taskType)) {
                predicates.add(cb.equal(root.get("taskType"), taskType));
            }
            if (hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (hasText(sourceType)) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            LocalDateTime start = parseDateTime(startTime, true);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            LocalDateTime end = parseDateTime(endTime, false);
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DetectTask> page = detectTaskRepository.findAll(specification, pageable);
        List<Map<String, Object>> records = page.getContent().stream().map(this::toTaskSummaryMap).toList();
        return PageResponse.of(records, page.getTotalElements(), pageNum, pageSize);
    }

    private DetectTask getTaskOrThrow(Long taskId) {
        return detectTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId));
    }

    private Map<String, Object> toTaskSummaryMap(DetectTask task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("taskNo", task.getTaskNo());
        data.put("taskType", task.getTaskType());
        data.put("status", task.getStatus());
        data.put("sourceType", task.getSourceType());
        data.put("sourceName", task.getSourceName());
        data.put("createdAt", format(task.getCreatedAt()));
        return data;
    }

    private Map<String, Object> toTaskMap(DetectTask task) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("taskNo", task.getTaskNo());
        data.put("taskType", task.getTaskType());
        data.put("sourceType", task.getSourceType());
        data.put("sourceName", task.getSourceName());
        data.put("status", task.getStatus());
        data.put("videoPath", task.getVideoPath());
        data.put("frameCount", task.getFrameCount());
        data.put("fireCount", task.getFireCount());
        data.put("startTime", format(task.getStartTime()));
        data.put("endTime", format(task.getEndTime()));
        data.put("resultSummary", task.getResultSummary());
        data.put("errorMessage", task.getErrorMessage());
        data.put("createdAt", format(task.getCreatedAt()));
        data.put("updatedAt", format(task.getUpdatedAt()));
        return data;
    }

    private Map<String, Object> toLatestEventMap(FireEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", event.getId());
        data.put("eventNo", event.getEventNo());
        data.put("eventTime", format(event.getEventTime()));
        data.put("confidence", event.getConfidence());
        data.put("snapshotUrl", toStaticUrl(event.getSnapshotPath()));
        return data;
    }

    private void applyAnalyzeResponse(DetectTask task, VideoAnalyzeResponse response) {
        LocalDateTime now = LocalDateTime.now();
        task.setFrameCount(response.frameCount() != null ? response.frameCount() : 0);
        task.setFireCount(response.fireCount() != null ? response.fireCount() : 0);
        task.setResultSummary(response.resultSummary());
        task.setErrorMessage(response.errorMessage());
        task.setEndTime(now);
        task.setUpdatedAt(now);

        if (TaskStatus.FAILED.name().equalsIgnoreCase(response.status())) {
            task.setStatus(TaskStatus.FAILED.name());
            detectTaskRepository.save(task);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    response.errorMessage() != null ? response.errorMessage() : "Inference service returned failed status");
        }

        task.setStatus(TaskStatus.FINISHED.name());
        detectTaskRepository.save(task);

        if (response.events() == null || response.events().isEmpty()) {
            return;
        }

        boolean isVideo = SourceType.UPLOAD_VIDEO.name().equals(task.getSourceType());

        for (VideoAnalyzeResponse.EventItem item : response.events()) {
            FireEvent event = new FireEvent();
            event.setTaskId(task.getId());
            event.setSourceType(task.getSourceType());
            event.setSourceName(task.getSourceName());
            event.setConfidence(item.confidence() != null ? item.confidence() : BigDecimal.ZERO);
            event.setDurationSeconds(item.durationSeconds());
            event.setSnapshotPath(item.snapshotPath());
            event.setTaskFrameNo(item.taskFrameNo());
            event.setCreatedAt(now);
            event.setUpdatedAt(now);

            if (isVideo) {
                String timecode = normalizeTimecode(item.eventTime());
                event.setVideoTimecode(timecode);
                event.setEventTime(parseEventTimeOrNow(item.eventTime()));
                String tc = timecode.replace(":", "");
                event.setEventNo("VEVT" + tc + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
            } else {
                event.setEventTime(parseEventTimeOrNow(item.eventTime()));
                event.setEventNo(generateBizNo("CEVT"));
            }

            FireEvent savedEvent = fireEventRepository.save(event);

            if (hasText(item.snapshotPath())) {
                FireImage image = new FireImage();
                image.setEventId(savedEvent.getId());
                image.setFileName(Paths.get(item.snapshotPath()).getFileName().toString());
                image.setFilePath(item.snapshotPath());
                image.setFileUrl(toStaticUrl(item.snapshotPath()));
                image.setCaptureTime(savedEvent.getEventTime());
                image.setSourceType(savedEvent.getSourceType());
                Path imagePath = resolveStorageFile(item.snapshotPath());
                try {
                    if (Files.exists(imagePath)) {
                        image.setFileSize(Files.size(imagePath));
                    }
                } catch (IOException ignored) {
                }
                image.setCreatedAt(now);
                image.setUpdatedAt(now);
                fireImageRepository.save(image);
            }
        }
    }

    private void markTaskFailed(DetectTask task, String errorMessage) {
        task.setStatus(TaskStatus.FAILED.name());
        task.setErrorMessage(errorMessage);
        task.setEndTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        detectTaskRepository.save(task);
    }

    private String generateBizNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_FORMATTER) + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index) : "";
    }

    private Path resolveStorageFile(String relativePath) {
        return Paths.get(storageRoot).toAbsolutePath().normalize().resolve(relativePath).normalize();
    }

    private String toStaticUrl(String relativePath) {
        if (!hasText(relativePath)) {
            return null;
        }
        return "/static/" + relativePath.replace("\\", "/").replaceFirst("^/+", "");
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME_FORMATTER);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime parseDateTime(String value, boolean startBoundary) {
        if (!hasText(value)) {
            return null;
        }
        try {
            if (value.length() == 10) {
                LocalDate date = LocalDate.parse(value);
                return startBoundary ? date.atStartOfDay() : date.atTime(23, 59, 59);
            }
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid datetime format: " + value);
            }
        }
    }

    private LocalDateTime parseEventTimeOrNow(String value) {
        if (!hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }

    private String normalizeTimecode(String value) {
        if (!hasText(value)) {
            return "00:00:00";
        }
        if (value.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return value;
        }
        if (value.matches("\\d{2}:\\d{2}")) {
            return value + ":00";
        }
        return value;
    }

    public Map<String, Object> clearUploadedVideos() {
        Path videoRoot = resolveStorageFile(videoDir);
        int deletedCount = 0;
        if (Files.exists(videoRoot) && Files.isDirectory(videoRoot)) {
            try (var stream = Files.list(videoRoot)) {
                for (Path file : stream.toList()) {
                    if (Files.isRegularFile(file)) {
                        Files.deleteIfExists(file);
                        deletedCount++;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deletedCount", deletedCount);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> analyzeCameraFrame(String imageBase64, String sourceName, Integer frameIndex, BigDecimal confThreshold) {
        String taskNo = generateBizNo("CAM");
        FrameAnalyzeResponse response = inferenceGatewayService.analyzeFrame(taskNo, imageBase64, sourceName, frameIndex, confThreshold);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskNo", taskNo);
        data.put("status", response.status());
        data.put("hasFire", response.hasFire());
        data.put("topConfidence", response.topConfidence());
        data.put("boxCount", response.boxCount());
        data.put("boxes", response.boxes() != null ? response.boxes().stream().map(box -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("x1", box.x1());
            b.put("y1", box.y1());
            b.put("x2", box.x2());
            b.put("y2", box.y2());
            b.put("confidence", box.confidence());
            b.put("className", box.className());
            return b;
        }).toList() : List.of());
        data.put("snapshotPath", response.snapshotPath() != null ? toStaticUrl(response.snapshotPath()) : null);
        data.put("errorMessage", response.errorMessage());

        if (Boolean.TRUE.equals(response.hasFire()) && response.snapshotPath() != null) {
            LocalDateTime now = LocalDateTime.now();
            DetectTask task = new DetectTask();
            task.setTaskNo(taskNo);
            task.setTaskType(TaskType.CAMERA.name());
            task.setSourceType(SourceType.CAMERA.name());
            task.setSourceName(sourceName != null && !sourceName.isBlank() ? sourceName : "摄像头监控");
            task.setStatus(TaskStatus.FINISHED.name());
            task.setFrameCount(frameIndex != null ? frameIndex : 0);
            task.setFireCount(1);
            task.setResultSummary("Camera frame fire detected");
            task.setStartTime(now);
            task.setEndTime(now);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            DetectTask savedTask = detectTaskRepository.save(task);

            FireEvent event = new FireEvent();
            event.setTaskId(savedTask.getId());
            event.setSourceType(SourceType.CAMERA.name());
            event.setSourceName(savedTask.getSourceName());
            event.setConfidence(response.topConfidence());
            event.setDurationSeconds(BigDecimal.ZERO);
            event.setSnapshotPath(response.snapshotPath());
            event.setTaskFrameNo(frameIndex);
            event.setEventTime(now);
            event.setCreatedAt(now);
            event.setUpdatedAt(now);
            event.setEventNo(generateBizNo("CEVT"));
            fireEventRepository.save(event);
        }

        return data;
    }
}
