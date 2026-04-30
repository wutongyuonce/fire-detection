package com.firedetection.backend.controller;

import com.firedetection.backend.common.ApiResponse;
import com.firedetection.backend.common.PageResponse;
import com.firedetection.backend.service.FireImageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fire-images")
public class FireImageController {

    private final FireImageService fireImageService;

    public FireImageController(FireImageService fireImageService) {
        this.fireImageService = fireImageService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> listImages(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(fireImageService.listImages(pageNum, pageSize, eventId, sourceType, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getImageDetail(@PathVariable Long id) {
        return ApiResponse.success(fireImageService.getImageDetail(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteImage(@PathVariable Long id) {
        return ApiResponse.success(fireImageService.deleteImage(id));
    }
}
