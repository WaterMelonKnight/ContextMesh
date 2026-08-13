package io.contextmesh.ingestion.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ChatGptImportHttpIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID workspaceId;

    @BeforeEach
    void createWorkspace() {
        jdbc.update("delete from messages"); jdbc.update("delete from conversations");
        jdbc.update("delete from workspaces"); jdbc.update("delete from users");
        UUID userId = UUID.randomUUID(); workspaceId = UUID.randomUUID();
        jdbc.update("insert into users(id, email, display_name) values (?, ?, ?)",
                userId, userId + "@example.test", "ChatGPT importer test");
        jdbc.update("insert into workspaces(id, owner_user_id, name) values (?, ?, ?)",
                workspaceId, userId, "ChatGPT import workspace");
    }

    @Test
    void importsPersistsCanonicalOrderAndReportsDuplicateAndConflict() throws Exception {
        String original = fixture();
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalReceived").value(2))
                .andExpect(jsonPath("$.importedCount").value(2));
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skippedDuplicateCount").value(2));
        String changed = original.replace("Canonical answer", "Changed canonical answer");
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.skippedDuplicateCount").value(1));

        assertThat(jdbc.queryForObject("select count(*) from conversations where workspace_id = ?", Integer.class, workspaceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select source_provider from conversations where workspace_id = ? and external_id = 'chatgpt-conversation-1'", String.class, workspaceId)).isEqualTo("chatgpt");
        assertThat(jdbc.queryForList("select external_id from messages where workspace_id = ? and conversation_id = "
                + "(select id from conversations where workspace_id = ? and external_id = 'chatgpt-conversation-1') order by sequence_no",
                String.class, workspaceId, workspaceId)).containsExactly("system-message", "user-message", "answer-message");
        assertThat(jdbc.queryForObject("select generation_model from messages where workspace_id = ? and external_id = 'answer-message'", String.class, workspaceId)).isEqualTo("gpt-4o");
    }

    @Test
    void returnsProviderProblemDetailWithoutPersistence() throws Exception {
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid ChatGPT Export"))
                .andExpect(jsonPath("$.detail").value("$: must be the conversations.json top-level array"));
        assertThat(jdbc.queryForObject("select count(*) from conversations", Integer.class)).isZero();
    }

    private String endpoint() { return "/api/v1/workspaces/" + workspaceId + "/imports/chatgpt"; }
    private String fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/fixtures/chatgpt-export/conversations.json")) {
            if (input == null) throw new IllegalStateException("fixture missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
