package com.equisplit.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            String dbTime = jdbcTemplate.queryForObject("SELECT NOW()::text", String.class);

            response.put("status", "OK");
            response.put("message", "ExpenseSplitter API is running");
            response.put("database", "Connected");
            response.put("time", dbTime);

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Database health check failed: {}", ex.getMessage());

            response.put("status", "ERROR");
            response.put("message", "Database connection failed");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
