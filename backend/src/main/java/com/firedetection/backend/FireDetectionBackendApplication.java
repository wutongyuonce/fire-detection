package com.firedetection.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FireDetectionBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireDetectionBackendApplication.class, args);
    }
}
