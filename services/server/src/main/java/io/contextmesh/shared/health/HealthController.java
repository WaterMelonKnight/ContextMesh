package io.contextmesh.shared.health;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "${contextmesh.web-origin:http://localhost:3000}")
final class HealthController {
    private final JdbcTemplate jdbcTemplate;

    HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/v1/health")
    Map<String, String> health() {
        jdbcTemplate.queryForObject("select 1", Integer.class);
        return Map.of("status", "UP", "application", "ContextMesh", "database", "UP");
    }
}
