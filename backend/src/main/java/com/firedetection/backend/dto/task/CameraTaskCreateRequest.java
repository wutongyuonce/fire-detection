package com.firedetection.backend.dto.task;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CameraTaskCreateRequest(
        String sourceName,
        @NotNull @Min(0) Integer cameraIndex,
        @DecimalMin("0.0") BigDecimal confThreshold,
        @DecimalMin("0.0") BigDecimal holdSeconds,
        @DecimalMin("0.0") BigDecimal cooldownSeconds
) {
}
