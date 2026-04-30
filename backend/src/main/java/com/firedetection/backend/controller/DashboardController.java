package com.firedetection.backend.controller;

import com.firedetection.backend.common.ApiResponse;
import com.firedetection.backend.dto.dashboard.DashboardOverviewResponse;
import com.firedetection.backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> getOverview() {
        return ApiResponse.success(dashboardService.getOverview());
    }
}
