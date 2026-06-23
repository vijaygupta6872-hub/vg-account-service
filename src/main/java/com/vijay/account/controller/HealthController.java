package com.vijay.account.controller;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes operational health information for the Account
 * Service.
 *
 * <p>The health endpoint verifies both application availability and database
 * connectivity. Infrastructure, deployment tooling, and local developers can use
 * this endpoint to determine whether the service is ready to serve account
 * requests.</p>
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Returns service and database health status.
     *
     * <p>A simple database query is executed to verify connectivity. A successful
     * query returns HTTP 200 with {@code UP} status. Database access failures
     * return HTTP 503 with {@code DOWN} status.</p>
     *
     * @return a response entity containing service status, database status, and
     *         the timestamp of the health check
     * @throws RuntimeException if an unexpected non-database failure occurs
     *         while building the health response
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "database", "UP",
                    "timestamp", Instant.now()
            ));
        } catch (DataAccessException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "DOWN",
                    "database", "DOWN",
                    "timestamp", Instant.now()
            ));
        }
    }
}
