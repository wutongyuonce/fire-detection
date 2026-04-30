package com.firedetection.backend.service.impl;

import com.firedetection.backend.dto.dashboard.DashboardOverviewResponse;
import com.firedetection.backend.entity.FireEvent;
import com.firedetection.backend.enums.SourceType;
import com.firedetection.backend.repository.FireEventRepository;
import com.firedetection.backend.service.DashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FireEventRepository fireEventRepository;

    public DashboardServiceImpl(FireEventRepository fireEventRepository) {
        this.fireEventRepository = fireEventRepository;
    }

    @Override
    public DashboardOverviewResponse getOverview() {
        LocalDate today = LocalDate.now();
        int todayFireCount = (int) fireEventRepository.countBySourceTypeAndEventTimeBetween(
                SourceType.CAMERA.name(), today.atStartOfDay(), today.plusDays(1).atStartOfDay().minusSeconds(1));
        Map<String, Object> latestEvent = fireEventRepository.findTopBySourceTypeOrderByEventTimeDesc(SourceType.CAMERA.name())
                .map(this::toLatestEventMap)
                .orElse(null);
        return new DashboardOverviewResponse(todayFireCount, latestEvent);
    }

    private Map<String, Object> toLatestEventMap(FireEvent event) {
        Map<String, Object> latestEvent = new LinkedHashMap<>();
        latestEvent.put("eventId", event.getId());
        latestEvent.put("eventNo", event.getEventNo());
        latestEvent.put("eventTime", format(event.getEventTime()));
        latestEvent.put("confidence", event.getConfidence());
        latestEvent.put("snapshotUrl", toStaticUrl(event.getSnapshotPath()));
        return latestEvent;
    }

    private String toStaticUrl(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return "/static/" + relativePath.replace("\\", "/").replaceFirst("^/+", "");
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME_FORMATTER);
    }
}
