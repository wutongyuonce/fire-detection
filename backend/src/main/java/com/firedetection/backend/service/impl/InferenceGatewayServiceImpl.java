package com.firedetection.backend.service.impl;

import com.firedetection.backend.dto.inference.FrameAnalyzeRequest;
import com.firedetection.backend.dto.inference.FrameAnalyzeResponse;
import com.firedetection.backend.dto.inference.VideoAnalyzeRequest;
import com.firedetection.backend.dto.inference.VideoAnalyzeResponse;
import com.firedetection.backend.entity.DetectTask;
import com.firedetection.backend.service.InferenceGatewayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class InferenceGatewayServiceImpl implements InferenceGatewayService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.inference.base-url:http://localhost:8000}")
    private String baseUrl;

    @Value("${app.inference.weights-path:../best.pt}")
    private String weightsPath;

    @Value("${app.inference.device:cpu}")
    private String device;

    @Value("${app.inference.default-conf-threshold:0.30}")
    private BigDecimal defaultConfThreshold;

    @Value("${app.storage.root:storage}")
    private String storageRoot;

    @Value("${app.storage.snapshot-dir:snapshots/fire}")
    private String snapshotDir;

    @Override
    public VideoAnalyzeResponse analyzeVideo(DetectTask task, BigDecimal confThreshold) {
        VideoAnalyzeRequest request = new VideoAnalyzeRequest(
                task.getTaskNo(),
                resolveAbsolutePath(task.getVideoPath()),
                resolveProjectPath(weightsPath),
                resolveStoragePath(snapshotDir),
                snapshotDir.replace("\\", "/"),
                task.getSourceName(),
                device,
                confThreshold != null ? confThreshold : defaultConfThreshold
        );

        try {
            VideoAnalyzeResponse response = restTemplate.postForObject(
                    baseUrl + "/infer/video/analyze",
                    request,
                    VideoAnalyzeResponse.class
            );
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Inference service returned empty response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call inference service", ex);
        }
    }

    @Override
    public FrameAnalyzeResponse analyzeFrame(String taskNo, String imageBase64, String sourceName,
                                             Integer frameIndex, BigDecimal confThreshold) {
        FrameAnalyzeRequest request = new FrameAnalyzeRequest(
                taskNo,
                imageBase64,
                resolveProjectPath(weightsPath),
                resolveStoragePath(snapshotDir),
                snapshotDir.replace("\\", "/"),
                sourceName,
                frameIndex,
                device,
                confThreshold != null ? confThreshold : defaultConfThreshold
        );

        try {
            FrameAnalyzeResponse response = restTemplate.postForObject(
                    baseUrl + "/infer/frame/analyze",
                    request,
                    FrameAnalyzeResponse.class
            );
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Inference service returned empty response");
            }
            return response;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call inference service", ex);
        }
    }

    private String resolveAbsolutePath(String relativePath) {
        return Paths.get(storageRoot).toAbsolutePath().normalize().resolve(relativePath).normalize().toString();
    }

    private String resolveStoragePath(String relativePath) {
        return Paths.get(storageRoot).toAbsolutePath().normalize().resolve(relativePath).normalize().toString();
    }

    private String resolveProjectPath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize().toString();
        }
        return path.toAbsolutePath().normalize().toString();
    }
}
