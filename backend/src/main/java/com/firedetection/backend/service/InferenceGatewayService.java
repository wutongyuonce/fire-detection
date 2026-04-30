package com.firedetection.backend.service;

import com.firedetection.backend.dto.inference.FrameAnalyzeResponse;
import com.firedetection.backend.dto.inference.VideoAnalyzeResponse;
import com.firedetection.backend.entity.DetectTask;

import java.math.BigDecimal;

public interface InferenceGatewayService {

    VideoAnalyzeResponse analyzeVideo(DetectTask task, BigDecimal confThreshold);

    FrameAnalyzeResponse analyzeFrame(String taskNo, String imageBase64, String sourceName,
                                      Integer frameIndex, BigDecimal confThreshold);
}
