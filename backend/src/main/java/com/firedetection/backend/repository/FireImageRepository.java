package com.firedetection.backend.repository;

import com.firedetection.backend.entity.FireImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface FireImageRepository extends JpaRepository<FireImage, Long>, JpaSpecificationExecutor<FireImage> {

    List<FireImage> findByEventIdOrderByCaptureTimeDesc(Long eventId);
}
