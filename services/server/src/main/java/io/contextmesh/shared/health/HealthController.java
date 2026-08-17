package io.contextmesh.shared.health;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class HealthController {
    private final JdbcTemplate jdbcTemplate;

    HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/v1/health")
    ResponseEntity<Map<String, String>> health() {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return ResponseEntity.ok(status("UP", "UP"));
        } catch (DataAccessException ignored) {
            return ResponseEntity.ok(status("DEGRADED", "DOWN"));
        }
    }

    private Map<String, String> status(String status, String database) {
        return Map.of("status", status, "application", "ContextMesh", "database", database);
    }
}
