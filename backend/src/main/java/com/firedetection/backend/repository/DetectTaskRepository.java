package com.firedetection.backend.repository;

import com.firedetection.backend.entity.DetectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DetectTaskRepository extends JpaRepository<DetectTask, Long>, JpaSpecificationExecutor<DetectTask> {

    long countByStatus(String status);
}
