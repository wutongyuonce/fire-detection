package com.firedetection.backend.service;

import com.firedetection.backend.common.PageResponse;

import java.util.Map;

public interface FireImageService {

    PageResponse<Map<String, Object>> listImages(int pageNum, int pageSize, Long eventId,
                                                 String sourceType, String startTime, String endTime);

    Map<String, Object> getImageDetail(Long id);

    boolean deleteImage(Long id);
}
