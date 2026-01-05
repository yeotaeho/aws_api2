package com.labzang.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 루트 경로(/) 컨트롤러
 * API 서버의 기본 정보를 제공합니다.
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "Labzang API");
        response.put("version", "1.0.0");
        response.put("status", "running");
        response.put("docs", "/docs");
        response.put("health", "/health");
        return ResponseEntity.ok(response);
    }
}

