package io.contextmesh.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.provider.application.GenerationStream;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelMessage;
import io.contextmesh.provider.application.ModelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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
class ConversationWorkflowSmokeIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapturingProvider provider;
    UUID workspace;

    @BeforeEach
    void setup() {
        jdbc.update("delete from messages");
        jdbc.update("delete from conversation_continuations");
        jdbc.update("delete from conversations");
        jdbc.update("delete from workspaces");
        jdbc.update("delete from users");
        var user = UUID.randomUUID();
        workspace = UUID.randomUUID();
        jdbc.update("insert into users(id,email,display_name) values (?,?,?)",
                user, user + "@test.invalid", "Smoke");
        jdbc.update("insert into workspaces(id,owner_user_id,name) values (?,?,?)",
                workspace, user, "Smoke");
        provider.requests.clear();
    }

    @Test
    void importsContinuesAtCutoffAndStreamsWithoutCopyingSource() throws Exception {
        String export = """
                [{"id":"workflow","title":"Workflow","current_node":"a2","mapping":{
                  "root":{"parent":null,"children":["s"],"message":null},
                  "s":{"parent":"root","children":["u1"],"message":{"id":"s","author":{"role":"system"},"content":{"content_type":"text","parts":["You are helpful."]}}},
                  "u1":{"parent":"s","children":["a1"],"message":{"id":"u1","author":{"role":"user"},"content":{"content_type":"text","parts":["What is ContextMesh?"]}}},
                  "a1":{"parent":"u1","children":["u2"],"message":{"id":"a1","author":{"role":"assistant"},"content":{"content_type":"text","parts":["ContextMesh preserves context across conversations."]}}},
                  "u2":{"parent":"a1","children":["a2"],"message":{"id":"u2","author":{"role":"user"},"content":{"content_type":"text","parts":["What should I build next?"]}}},
                  "a2":{"parent":"u2","children":[],"message":{"id":"a2","author":{"role":"assistant"},"content":{"content_type":"text","parts":["Build a continuation workflow."]}}}
                }}]
                """;

        mvc.perform(post(base() + "/imports/chatgpt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(export))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(1));

        var list = json.readTree(mvc.perform(get(base() + "/conversations"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        UUID source = UUID.fromString(list.get(0).path("id").asText());
        var sourceView = json.readTree(mvc.perform(get(base() + "/conversations/" + source))
                .andExpect(jsonPath("$.sourceType").value("IMPORTED_CONVERSATION"))
                .andExpect(jsonPath("$.messages.length()").value(5))
                .andReturn().getResponse().getContentAsString());
        UUID cutoff = UUID.fromString(sourceView.path("messages").get(2).path("id").asText());

        var continuation = json.readTree(mvc.perform(post(base() + "/conversations/" + source + "/continuations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"throughMessageId\":\"" + cutoff + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversation.sourceType").value("NATIVE_CONVERSATION"))
                .andExpect(jsonPath("$.conversation.messages.length()").value(0))
                .andExpect(jsonPath("$.origin.throughMessageId").value(cutoff.toString()))
                .andReturn().getResponse().getContentAsString());
        UUID target = UUID.fromString(continuation.path("conversation").path("id").asText());

        var pending = mvc.perform(post(base() + "/conversations/" + target + "/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"provider":"capturing","model":"smoke",
                                 "content":[{"type":"TEXT","text":"Continue from that point."}]}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();
        pending.getAsyncResult(5000);
        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:completed")));

        var targetView = json.readTree(mvc.perform(get(base() + "/conversations/" + target))
                .andReturn().getResponse().getContentAsString());
        assertThat(targetView.path("messages").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id=?",
                Integer.class, source)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from messages where conversation_id=?",
                Integer.class, target)).isEqualTo(2);

        assertThat(provider.requests).hasSize(1);
        assertThat(provider.requests.getFirst().messages())
                .extracting(ModelMessage::text)
                .containsExactly("You are helpful.", "What is ContextMesh?",
                        "ContextMesh preserves context across conversations.",
                        "Continue from that point.")
                .doesNotContain("What should I build next?", "Build a continuation workflow.");
    }

    String base() {
        return "/api/v1/workspaces/" + workspace;
    }

    @TestConfiguration
    static class Config {
        @Bean
        CapturingProvider capturingProvider() {
            return new CapturingProvider();
        }
    }

    static final class CapturingProvider implements ModelProvider {
        final List<ModelGenerationRequest> requests = new ArrayList<>();

        @Override
        public String providerId() {
            return "capturing";
        }

        @Override
        public GenerationStream generate(ModelGenerationRequest request) {
            requests.add(request);
            return sink -> {
                sink.accept(new GenerationEvent.Started("capturing", request.model()));
                sink.accept(new GenerationEvent.TextDelta("Deterministic response"));
                sink.accept(new GenerationEvent.Completed("capturing", request.model(), null));
            };
        }
    }
}
