package com.firedetection.backend.dto.inference;

import java.math.BigDecimal;

public record FrameAnalyzeRequest(
        String taskNo,
        String imageBase64,
        String weightsPath,
        String snapshotDir,
        String snapshotRelativeDir,
        String sourceName,
        Integer frameIndex,
        String device,
        BigDecimal confThreshold
) {
}
