package com.firedetection.backend.dto.task;

import java.util.Map;

public record TaskStatusResponse(
        Long taskId,
        String status,
        boolean hasActiveFire,
        Map<String, Object> latestEvent
) {
}
