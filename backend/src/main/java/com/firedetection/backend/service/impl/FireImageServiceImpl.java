package com.firedetection.backend.service.impl;

import com.firedetection.backend.common.PageResponse;
import com.firedetection.backend.entity.FireImage;
import com.firedetection.backend.repository.FireImageRepository;
import com.firedetection.backend.service.FireImageService;
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
public class FireImageServiceImpl implements FireImageService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FireImageRepository fireImageRepository;

    @Value("${app.storage.root:storage}")
    private String storageRoot;

    public FireImageServiceImpl(FireImageRepository fireImageRepository) {
        this.fireImageRepository = fireImageRepository;
    }

    @Override
    public PageResponse<Map<String, Object>> listImages(int pageNum, int pageSize, Long eventId,
                                                        String sourceType, String startTime, String endTime) {
        Pageable pageable = PageRequest.of(Math.max(pageNum - 1, 0), pageSize, Sort.by(Sort.Direction.DESC, "captureTime"));
        Specification<FireImage> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }
            if (hasText(sourceType)) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            LocalDateTime start = parseDateTime(startTime, true);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("captureTime"), start));
            }
            LocalDateTime end = parseDateTime(endTime, false);
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("captureTime"), end));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FireImage> page = fireImageRepository.findAll(specification, pageable);
        List<Map<String, Object>> records = page.getContent().stream().map(this::toImageMap).toList();
        return PageResponse.of(records, page.getTotalElements(), pageNum, pageSize);
    }

    @Override
    public Map<String, Object> getImageDetail(Long id) {
        return toImageMap(getImageOrThrow(id));
    }

    @Override
    @Transactional
    public boolean deleteImage(Long id) {
        FireImage image = getImageOrThrow(id);
        deleteStorageFile(image.getFilePath());
        fireImageRepository.delete(image);
        return true;
    }

    private FireImage getImageOrThrow(Long id) {
        return fireImageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fire image not found: " + id));
    }

    private Map<String, Object> toImageMap(FireImage image) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", image.getId());
        detail.put("eventId", image.getEventId());
        detail.put("fileName", image.getFileName());
        detail.put("filePath", image.getFilePath());
        detail.put("fileUrl", toStaticUrl(image.getFilePath()));
        detail.put("sourceType", image.getSourceType());
        detail.put("fileSize", image.getFileSize());
        detail.put("captureTime", format(image.getCaptureTime()));
        detail.put("createdAt", format(image.getCreatedAt()));
        detail.put("updatedAt", format(image.getUpdatedAt()));
        return detail;
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
