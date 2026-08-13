package io.contextmesh.shared.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {
    private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
    private final HealthController controller = new HealthController(jdbcTemplate);

    @Test
    void reportsUpWhenDatabaseResponds() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);

        ResponseEntity<Map<String, String>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(
                Map.of("status", "UP", "application", "ContextMesh", "database", "UP"));
    }

    @Test
    void reportsDegradedWithoutLeakingDetailsWhenDatabaseIsUnavailable() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("sensitive database details"));

        ResponseEntity<Map<String, String>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(
                Map.of("status", "DEGRADED", "application", "ContextMesh", "database", "DOWN"));
        assertThat(response.toString()).doesNotContain("sensitive database details");
    }
}
