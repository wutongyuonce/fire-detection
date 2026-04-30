package com.firedetection.backend.repository;

import com.firedetection.backend.entity.FireEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FireEventRepository extends JpaRepository<FireEvent, Long>, JpaSpecificationExecutor<FireEvent> {

    Optional<FireEvent> findTopByTaskIdOrderByEventTimeDesc(Long taskId);

    Optional<FireEvent> findTopByOrderByEventTimeDesc();

    Optional<FireEvent> findTopBySourceTypeOrderByEventTimeDesc(String sourceType);

    long countByEventTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    long countBySourceTypeAndEventTimeBetween(String sourceType, LocalDateTime startTime, LocalDateTime endTime);
}
