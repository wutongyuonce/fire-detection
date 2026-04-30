package com.firedetection.backend.dto.inference;

import java.math.BigDecimal;
import java.util.List;

public record VideoAnalyzeResponse(
        String taskNo,
        String status,
        Integer frameCount,
        Integer fireCount,
        String resultSummary,
        String errorMessage,
        List<EventItem> events
) {
    public record EventItem(
            String eventTime,
            BigDecimal confidence,
            BigDecimal durationSeconds,
            String snapshotPath,
            Integer taskFrameNo
    ) {
    }
}
