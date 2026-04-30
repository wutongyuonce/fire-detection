package com.firedetection.backend.service;

import com.firedetection.backend.common.PageResponse;

import java.util.Map;

public interface FireEventService {

    PageResponse<Map<String, Object>> listEvents(int pageNum, int pageSize, String sourceType,
                                                 String sourceName, String startTime, String endTime);

    Map<String, Object> getEventDetail(Long id);

    boolean deleteEvent(Long id);
}
