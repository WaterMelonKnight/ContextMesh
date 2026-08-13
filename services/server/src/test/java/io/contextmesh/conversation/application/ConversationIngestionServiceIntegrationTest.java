package io.contextmesh.conversation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.NormalizedConversation;
import io.contextmesh.conversation.domain.NormalizedMessage;
import io.contextmesh.conversation.domain.TextContentPart;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class ConversationIngestionServiceIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired ConversationIngestionService service;
    @Autowired JdbcTemplate jdbc;
    private UUID workspaceA;
    private UUID workspaceB;

    @BeforeEach
    void setUpWorkspaces() {
        jdbc.update("truncate table messages, conversations, workspaces, users cascade");
        UUID user = UUID.randomUUID();
        jdbc.update("insert into users(id, email, display_name) values (?, ?, ?)", user, user + "@test.invalid", "Test");
        workspaceA = addWorkspace(user, "A");
        workspaceB = addWorkspace(user, "B");
    }

    @Test
    void persistsOrderedMessagesAndAllNormalizedFields() {
        var result = service.ingest(workspaceA, conversation("source-1", "Original"));

        assertThat(result.status()).isEqualTo(IngestionStatus.IMPORTED);
        assertThat(result.messagesInserted()).isEqualTo(2);
        assertThat(result.fingerprint()).matches("[0-9a-f]{64}");
        var rows = jdbc.queryForList("""
                select sequence_no, external_id, role, parent_external_id, content::text,
                       generation_provider, generation_model, metadata::text
                from messages where workspace_id = ? and conversation_id = ? order by sequence_no
                """, workspaceA, result.conversationId());
        assertThat(rows).extracting(row -> row.get("external_id")).containsExactly("m-second", "m-first");
        assertThat(rows).extracting(row -> row.get("sequence_no")).containsExactly(0, 1);
        assertThat(rows.get(1).get("parent_external_id")).isEqualTo("m-second");
        assertThat(rows.get(1).get("generation_provider")).isEqualTo("openai");
        assertThat(rows.get(1).get("generation_model")).isEqualTo("model-x");
        assertThat(rows.get(0).get("content").toString()).contains("Second chronologically, first in source");
        assertThat(rows.get(1).get("metadata").toString()).contains("answer");
        assertThat(jdbc.queryForObject("select metadata::text from conversations where id = ?",
                String.class, result.conversationId())).contains("origin");
    }

    @Test
    void duplicateConflictAndIdentityAreWorkspaceScoped() {
        var original = conversation("source-1", "Original");
        var importedA = service.ingest(workspaceA, original);
        var duplicateA = service.ingest(workspaceA, original);
        var conflictA = service.ingest(workspaceA, conversation("source-1", "Changed"));
        var importedB = service.ingest(workspaceB, original);

        assertThat(importedA.status()).isEqualTo(IngestionStatus.IMPORTED);
        assertThat(duplicateA.status()).isEqualTo(IngestionStatus.SKIPPED_DUPLICATE);
        assertThat(duplicateA.conversationId()).isEqualTo(importedA.conversationId());
        assertThat(conflictA.status()).isEqualTo(IngestionStatus.CONFLICT);
        assertThat(conflictA.conversationId()).isEqualTo(importedA.conversationId());
        assertThat(importedB.status()).isEqualTo(IngestionStatus.IMPORTED);
        assertThat(importedB.conversationId()).isNotEqualTo(importedA.conversationId());
        assertThat(jdbc.queryForObject("select count(*) from conversations", Integer.class)).isEqualTo(2);
    }

    @Test
    void fingerprintIsFallbackIdentityAndCanonicalizesMetadataMapOrder() {
        var first = conversation(null, "No provider id", Map.of("z", 1, "a", Map.of("two", 2, "one", 1)));
        var second = conversation(null, "No provider id", Map.of("a", Map.of("one", 1, "two", 2), "z", 1));

        var imported = service.ingest(workspaceA, first);
        var duplicate = service.ingest(workspaceA, second);

        assertThat(duplicate.status()).isEqualTo(IngestionStatus.SKIPPED_DUPLICATE);
        assertThat(duplicate.fingerprint()).isEqualTo(imported.fingerprint());
    }

    @Test
    void nullableProviderIsPartOfDatabaseEnforcedExternalIdentity() {
        var source = conversation("nullable-provider", "No provider", Map.of());
        source = new NormalizedConversation(source.externalId(), source.title(), source.sourceType(), null,
                source.createdAt(), source.updatedAt(), source.messages(), source.metadata());

        var imported = service.ingest(workspaceA, source);
        var duplicate = service.ingest(workspaceA, source);

        assertThat(imported.status()).isEqualTo(IngestionStatus.IMPORTED);
        assertThat(duplicate.status()).isEqualTo(IngestionStatus.SKIPPED_DUPLICATE);
        assertThat(jdbc.queryForObject("""
                select count(*) from conversations
                where workspace_id = ? and source_provider is null and external_id = ?
                """, Integer.class, workspaceA, source.externalId())).isEqualTo(1);
    }

    @Test
    void concurrentImportsOfSameSourceCreateOneConversation() throws Exception {
        var source = conversation("concurrent-source", "Concurrent");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> ingestAfterBarrier(source, ready, start));
            var second = executor.submit(() -> ingestAfterBarrier(source, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(20, TimeUnit.SECONDS).status(),
                    second.get(20, TimeUnit.SECONDS).status()))
                    .containsExactlyInAnyOrder(IngestionStatus.IMPORTED, IngestionStatus.SKIPPED_DUPLICATE);
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from conversations where workspace_id = ? and external_id = ?
                """, Integer.class, workspaceA, source.externalId())).isEqualTo(1);
    }

    @Test
    void messageFailureRollsBackConversationAndEarlierMessages() {
        var valid = conversation("rollback-source", "Rollback");
        var oversized = new NormalizedMessage("x".repeat(501), MessageRole.USER,
                List.of(new TextContentPart("fails varchar constraint")), Instant.parse("2025-01-01T00:00:00Z"),
                null, null, Map.of());
        var invalid = new NormalizedConversation(valid.externalId(), valid.title(), valid.sourceType(),
                valid.sourceProvider(), valid.createdAt(), valid.updatedAt(),
                List.of(valid.messages().getFirst(), oversized), valid.metadata());

        assertThatThrownBy(() -> service.ingest(workspaceA, invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from conversations where workspace_id = ?",
                Integer.class, workspaceA)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from messages where workspace_id = ?",
                Integer.class, workspaceA)).isZero();
    }

    private UUID addWorkspace(UUID user, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workspaces(id, owner_user_id, name) values (?, ?, ?)", id, user, name);
        return id;
    }

    private IngestionResult ingestAfterBarrier(NormalizedConversation source, CountDownLatch ready,
                                                CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start barrier timed out");
        return service.ingest(workspaceA, source);
    }

    private static NormalizedConversation conversation(String externalId, String title) {
        return conversation(externalId, title, Map.of("origin", "fixture"));
    }

    private static NormalizedConversation conversation(String externalId, String title, Map<String, Object> metadata) {
        var firstInSource = new NormalizedMessage("m-second", MessageRole.USER,
                List.of(new TextContentPart("Second chronologically, first in source")),
                Instant.parse("2025-02-01T00:00:00Z"), null, null, Map.of("kind", "question"));
        var secondInSource = new NormalizedMessage("m-first", MessageRole.ASSISTANT,
                List.of(new TextContentPart("Earlier timestamp, second in source")),
                Instant.parse("2025-01-01T00:00:00Z"), "m-second",
                new GenerationMetadata("openai", "model-x"), Map.of("kind", "answer"));
        return new NormalizedConversation(externalId, title, ConversationSourceType.IMPORTED_CONVERSATION,
                "generic-json", Instant.parse("2024-12-01T00:00:00Z"),
                Instant.parse("2025-02-02T00:00:00Z"), List.of(firstInSource, secondInSource), metadata);
    }
}
