package com.firedetection.backend.dto.inference;

import java.math.BigDecimal;

public record VideoAnalyzeRequest(
        String taskNo,
        String videoPath,
        String weightsPath,
        String snapshotDir,
        String snapshotRelativeDir,
        String sourceName,
        String device,
        BigDecimal confThreshold
) {
}
