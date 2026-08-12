package io.contextmesh;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class DatabaseIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void migrationsCreateFoundationAndPgvectorIsAvailable() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
        assertThat(jdbcTemplate.queryForObject("select extname from pg_extension where extname = 'vector'", String.class)).isEqualTo("vector");
        assertThat(jdbcTemplate.queryForObject("select count(*) from workspaces", Integer.class)).isZero();
    }
}
