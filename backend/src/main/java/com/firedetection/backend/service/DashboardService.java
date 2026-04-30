package com.firedetection.backend.service;

import com.firedetection.backend.dto.dashboard.DashboardOverviewResponse;

public interface DashboardService {

    DashboardOverviewResponse getOverview();
}
