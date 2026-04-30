package com.firedetection.backend.dto.dashboard;

import java.util.Map;

public record DashboardOverviewResponse(
        int todayFireCount,
        Map<String, Object> latestEvent
) {
}
