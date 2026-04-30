package com.firedetection.backend.service.impl;

import com.firedetection.backend.common.PageResponse;
import com.firedetection.backend.entity.FireEvent;
import com.firedetection.backend.entity.FireImage;
import com.firedetection.backend.repository.FireEventRepository;
import com.firedetection.backend.repository.FireImageRepository;
import com.firedetection.backend.service.FireEventService;
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
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FireEventServiceImpl implements FireEventService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FireEventRepository fireEventRepository;
    private final FireImageRepository fireImageRepository;

    @Value("${app.storage.root:storage}")
    private String storageRoot;

    public FireEventServiceImpl(FireEventRepository fireEventRepository, FireImageRepository fireImageRepository) {
        this.fireEventRepository = fireEventRepository;
        this.fireImageRepository = fireImageRepository;
    }

    @Override
    public PageResponse<Map<String, Object>> listEvents(int pageNum, int pageSize, String sourceType,
                                                        String sourceName, String startTime, String endTime) {
        Pageable pageable = PageRequest.of(Math.max(pageNum - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "eventTime"));
        Specification<FireEvent> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(sourceType)) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            if (hasText(sourceName)) {
                predicates.add(cb.like(root.get("sourceName"), "%" + sourceName + "%"));
            }
            LocalDateTime start = parseDateTime(startTime, true);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventTime"), start));
            }
            LocalDateTime end = parseDateTime(endTime, false);
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventTime"), end));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FireEvent> page = fireEventRepository.findAll(specification, pageable);
        List<Map<String, Object>> records = page.getContent().stream().map(this::toEventSummaryMap).toList();
        return PageResponse.of(records, page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public Map<String, Object> getEventDetail(Long id) {
        FireEvent event = getEventOrThrow(id);
        List<Map<String, Object>> images = fireImageRepository.findByEventIdOrderByCaptureTimeDesc(id)
                .stream()
                .map(this::toImageMap)
                .toList();

        Map<String, Object> detail = new LinkedHashMap<>(toEventSummaryMap(event));
        detail.put("durationSeconds", event.getDurationSeconds());
        detail.put("taskFrameNo", event.getTaskFrameNo());
        detail.put("remark", event.getRemark());
        detail.put("images", images);
        detail.put("createdAt", format(event.getCreatedAt()));
        detail.put("updatedAt", format(event.getUpdatedAt()));
        return detail;
    }

    @Override
    @Transactional
    public boolean deleteEvent(Long id) {
        FireEvent event = getEventOrThrow(id);
        List<FireImage> images = fireImageRepository.findByEventIdOrderByCaptureTimeDesc(id);
        for (FireImage image : images) {
            deleteStorageFile(image.getFilePath());
        }
        fireImageRepository.deleteAll(images);
        if (hasText(event.getSnapshotPath())) {
            deleteStorageFile(event.getSnapshotPath());
        }
        fireEventRepository.delete(event);
        return true;
    }

    private FireEvent getEventOrThrow(Long id) {
        return fireEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fire event not found: " + id));
    }

    private Map<String, Object> toEventSummaryMap(FireEvent event) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", event.getId());
        record.put("eventNo", event.getEventNo());
        record.put("taskId", event.getTaskId());
        record.put("sourceType", event.getSourceType());
        record.put("sourceName", event.getSourceName());
        record.put("eventTime", format(event.getEventTime()));
        record.put("confidence", event.getConfidence());
        record.put("snapshotUrl", toStaticUrl(event.getSnapshotPath()));
        return record;
    }

    private Map<String, Object> toImageMap(FireImage image) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", image.getId());
        data.put("fileName", image.getFileName());
        data.put("filePath", image.getFilePath());
        data.put("fileUrl", toStaticUrl(image.getFilePath()));
        data.put("captureTime", format(image.getCaptureTime()));
        return data;
    }

    private void deleteStorageFile(String relativePath) {
        if (!hasText(relativePath)) {
            return;
        }
        Path path = Paths.get(storageRoot).toAbsolutePath().normalize().resolve(relativePath).normalize();
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
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
}
