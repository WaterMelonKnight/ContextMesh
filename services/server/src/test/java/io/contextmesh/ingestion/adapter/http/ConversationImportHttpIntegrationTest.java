package io.contextmesh.ingestion.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ConversationImportHttpIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID workspaceId;

    @BeforeEach
    void createWorkspace() {
        jdbc.update("delete from messages");
        jdbc.update("delete from conversations");
        jdbc.update("delete from workspaces");
        jdbc.update("delete from users");
        UUID userId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        jdbc.update("insert into users(id, email, display_name) values (?, ?, ?)",
                userId, userId + "@example.test", "Importer test");
        jdbc.update("insert into workspaces(id, owner_user_id, name) values (?, ?, ?)",
                workspaceId, userId, "Import workspace");
    }

    @Test
    void importsGenericJsonThroughHttpAndReportsDuplicateAndConflict() throws Exception {
        String original = batch("Original text", "second conversation");
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").value(2))
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.results[0].ingestionResult.status").value("IMPORTED"));

        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON).content(original))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skippedDuplicateCount").value(2));

        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON)
                        .content(batch("Changed text", "second conversation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.skippedDuplicateCount").value(1));

        assertThat(jdbc.queryForObject("select count(*) from conversations where workspace_id = ?",
                Integer.class, workspaceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from messages where workspace_id = ?",
                Integer.class, workspaceId)).isEqualTo(3);
        assertThat(jdbc.queryForList("select sequence_no from messages where workspace_id = ? order by conversation_id, sequence_no",
                Integer.class, workspaceId)).contains(0, 1);
    }

    @Test
    void returnsProblemDetailsBeforePersistingMalformedBatch() throws Exception {
        mvc.perform(post(endpoint()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaVersion\":\"1\",\"conversations\":[{\"sourceType\":\"IMPORTED_CONVERSATION\",\"messages\":[{\"role\":\"HUMAN\",\"content\":[{\"type\":\"TEXT\",\"text\":\"x\"}]}]}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Generic Conversation JSON"))
                .andExpect(jsonPath("$.detail").value("$.conversations[0].messages[0].role: unknown value 'HUMAN'"));
        assertThat(jdbc.queryForObject("select count(*) from conversations", Integer.class)).isZero();
    }

    private String endpoint() {
        return "/api/v1/workspaces/" + workspaceId + "/imports/conversations";
    }

    private static String batch(String firstText, String secondText) {
        return """
                {"schemaVersion":"1","conversations":[
                  {"externalId":"conversation-one","title":"One","sourceType":"IMPORTED_CONVERSATION","sourceProvider":"generic-test",
                   "metadata":{"origin":"integration"},"messages":[
                    {"externalId":"m1","role":"USER","content":[{"type":"TEXT","text":"%s"}]},
                    {"externalId":"m2","role":"ASSISTANT","parentExternalId":"m1","content":[{"type":"TEXT","text":"answer"}],
                     "generation":{"provider":"test-provider","model":"test-model"}}]},
                  {"externalId":"conversation-two","sourceType":"IMPORTED_CONVERSATION","messages":[
                    {"role":"USER","content":[{"type":"TEXT","text":"%s"}]}]}
                ]}
                """.formatted(firstText, secondText);
    }
}
