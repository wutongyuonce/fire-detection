package com.firedetection.backend.dto.inference;

import java.math.BigDecimal;
import java.util.List;

public record FrameAnalyzeResponse(
        String taskNo,
        String status,
        Boolean hasFire,
        BigDecimal topConfidence,
        Integer boxCount,
        List<BoundingBox> boxes,
        String snapshotPath,
        String errorMessage
) {
    public record BoundingBox(
            BigDecimal x1,
            BigDecimal y1,
            BigDecimal x2,
            BigDecimal y2,
            BigDecimal confidence,
            Integer classId,
            String className
    ) {
    }
}
