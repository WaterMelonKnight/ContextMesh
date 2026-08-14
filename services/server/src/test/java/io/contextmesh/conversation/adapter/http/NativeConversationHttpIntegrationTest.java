package io.contextmesh.conversation.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.application.ConversationNotFoundException;
import io.contextmesh.conversation.application.NativeConversationService;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.TextContentPart;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
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
class NativeConversationHttpIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired NativeConversationService service;
    UUID workspaceId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from messages");
        jdbc.update("delete from conversations");
        jdbc.update("delete from workspaces");
        jdbc.update("delete from users");
        UUID user = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        jdbc.update("insert into users(id, email, display_name) values (?, ?, ?)",
                user, user + "@example.test", "Native test");
        jdbc.update("insert into workspaces(id, owner_user_id, name) values (?, ?, ?)",
                workspaceId, user, "Native workspace");
    }

    @Test
    void createsAppendsAndReadsNativeConversationThroughHttp() throws Exception {
        String created = mvc.perform(post(base()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Native Talk\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("NATIVE_CONVERSATION"))
                .andExpect(jsonPath("$.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.externalId").doesNotExist())
                .andExpect(jsonPath("$.title").value("Native Talk"))
                .andReturn().getResponse().getContentAsString();
        UUID conversationId = UUID.fromString(objectMapper.readTree(created).path("id").asText());

        String first = mvc.perform(post(base() + "/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"USER","content":[{"type":"TEXT","text":"Hello\\n世界"}]}
                                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sequenceNo").value(0))
                .andExpect(jsonPath("$.parentExternalId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String firstStableId = objectMapper.readTree(first).path("stableId").asText();
        assertThat(UUID.fromString(firstStableId)).isNotNull();

        mvc.perform(post(base() + "/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"role":"ASSISTANT","content":[{"type":"TEXT","text":"Answer"}],
                         "generation":{"provider":"openai","model":"future-model"}}"""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sequenceNo").value(1))
                .andExpect(jsonPath("$.parentExternalId").value(firstStableId));

        mvc.perform(get(base() + "/" + conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content[0].text").value("Hello\n世界"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].generation.provider").value("openai"))
                .andExpect(jsonPath("$.messages[1].generation.model").value("future-model"));

        assertThat(jdbc.queryForObject("select source_fingerprint from conversations where id = ?",
                String.class, conversationId)).isNull();
        assertThat(jdbc.queryForList("select sequence_no from messages where conversation_id = ? order by sequence_no",
                Integer.class, conversationId)).containsExactly(0, 1);
    }

    @Test
    void rejectsMissingCrossWorkspaceAndImportedConversationAppends() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(post(base() + "/" + missing + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(userMessage("x"))).andExpect(status().isNotFound());

        UUID nativeId = service.createConversation(workspaceId, null).id();
        mvc.perform(post("/api/v1/workspaces/" + UUID.randomUUID() + "/conversations/" + nativeId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content(userMessage("x")))
                .andExpect(status().isNotFound());

        UUID imported = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                insert into conversations(id, workspace_id, source_type, external_id,
                  source_fingerprint, metadata, imported_at, created_at, updated_at)
                values (?, ?, 'IMPORTED_CONVERSATION', 'external', ?, '{}'::jsonb, ?, ?, ?)
                """, imported, workspaceId, "a".repeat(64), now, now, now);
        mvc.perform(post(base() + "/" + imported + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content(userMessage("x"))).andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidRoleAndContent() throws Exception {
        UUID id = service.createConversation(workspaceId, null).id();
        mvc.perform(post(base() + "/" + id + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"HUMAN\",\"content\":[{\"type\":\"TEXT\",\"text\":\"x\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base() + "/" + id + "/messages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"content\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serializesConcurrentAppendsWithoutDuplicateSequencesOrLostMessages() throws Exception {
        UUID id = service.createConversation(workspaceId, "Concurrent").id();
        int count = 12;
        try (var executor = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i = 0; i < count; i++) {
                int number = i;
                tasks.add(() -> {
                    service.appendMessage(workspaceId, id, MessageRole.USER,
                            List.of(new TextContentPart("message " + number)), null);
                    return null;
                });
            }
            for (var future : executor.invokeAll(tasks)) future.get();
        }
        var view = service.getConversation(workspaceId, id);
        assertThat(view.messages()).hasSize(count);
        assertThat(view.messages()).extracting(message -> message.sequenceNo())
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, count).boxed().toList());
        for (int i = 1; i < count; i++)
            assertThat(view.messages().get(i).parentExternalId())
                    .isEqualTo(view.messages().get(i - 1).stableId());
    }

    @Test
    void serviceReadUsesWorkspaceIsolation() {
        UUID id = service.createConversation(workspaceId, "Private").id();
        assertThatThrownBy(() -> service.getConversation(UUID.randomUUID(), id))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void streamsFakeTurnThenPersistsUserAndCompletedAssistant() throws Exception {
        UUID id = service.createConversation(workspaceId, "Generated").id();
        var initial = mvc.perform(post(base() + "/" + id + "/turns")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"provider":"fake","model":"fake-model",
                                 "content":[{"type":"TEXT","text":"Hello"}]}
                                """))
                .andExpect(status().isOk()).andReturn();
        initial.getAsyncResult(5000);
        String body = mvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).containsSubsequence("event:started", "event:delta", "Fake ",
                "event:delta", "response", "event:completed");

        var persisted = service.getConversation(workspaceId, id);
        assertThat(persisted.messages()).hasSize(2);
        assertThat(persisted.messages()).extracting(message -> message.role())
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(persisted.messages().get(1).content()).containsExactly(new TextContentPart("Fake response"));
        assertThat(persisted.messages().get(1).generation().provider()).isEqualTo("fake");
        assertThat(persisted.messages().get(1).generation().model()).isEqualTo("fake-model");
        assertThat(persisted.messages().get(1).parentExternalId())
                .isEqualTo(persisted.messages().get(0).stableId());
    }

    private String base() { return "/api/v1/workspaces/" + workspaceId + "/conversations"; }
    private static String userMessage(String text) {
        return "{\"role\":\"USER\",\"content\":[{\"type\":\"TEXT\",\"text\":\"" + text + "\"}]}";
    }
}
