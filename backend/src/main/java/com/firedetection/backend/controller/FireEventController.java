package com.firedetection.backend.controller;

import com.firedetection.backend.common.ApiResponse;
import com.firedetection.backend.common.PageResponse;
import com.firedetection.backend.service.FireEventService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/fire-events")
public class FireEventController {

    private final FireEventService fireEventService;

    public FireEventController(FireEventService fireEventService) {
        this.fireEventService = fireEventService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> listEvents(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.success(fireEventService.listEvents(pageNum, pageSize, sourceType, sourceName, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getEventDetail(@PathVariable Long id) {
        return ApiResponse.success(fireEventService.getEventDetail(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteEvent(@PathVariable Long id) {
        return ApiResponse.success(fireEventService.deleteEvent(id));
    }
}
