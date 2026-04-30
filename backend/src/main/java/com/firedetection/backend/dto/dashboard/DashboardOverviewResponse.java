package com.firedetection.backend.dto.dashboard;

import java.util.Map;

public record DashboardOverviewResponse(
        int runningTaskCount,
        int todayFireCount,
        String systemStatus,
        Map<String, Object> latestEvent
) {
}
