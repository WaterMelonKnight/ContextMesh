package io.contextmesh.conversation.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.application.ConversationPersistencePort;
import io.contextmesh.conversation.domain.NormalizedConversation;
import io.contextmesh.conversation.domain.TextContentPart;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConversationPersistenceAdapter implements ConversationPersistencePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcConversationPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredConversation> findBySourceIdentity(UUID workspaceId,
            NormalizedConversation conversation, String fingerprint) {
        // Serialize concurrent attempts for one workspace-local source identity. This keeps the
        // check-and-insert decision deterministic without taking locks on unrelated sources.
        String identity = conversation.externalId() == null
                ? "fingerprint:" + fingerprint
                : "external:" + conversation.sourceType().name() + ":" + conversation.sourceProvider()
                    + ":" + conversation.externalId();
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, workspaceId + ":" + identity);
                statement.execute();
                return null;
            }
        });
        String sql;
        Object[] arguments;
        if (conversation.externalId() != null) {
            sql = """
                    select id, source_fingerprint from conversations
                    where workspace_id = ? and source_type = ?
                      and source_provider is not distinct from ? and external_id = ?
                    """;
            arguments = new Object[]{workspaceId, conversation.sourceType().name(),
                    conversation.sourceProvider(), conversation.externalId()};
        } else {
            sql = """
                    select id, source_fingerprint from conversations
                    where workspace_id = ? and external_id is null and source_fingerprint = ?
                    """;
            arguments = new Object[]{workspaceId, fingerprint};
        }
        return Optional.ofNullable(DataAccessUtils.singleResult(jdbc.query(sql,
                (resultSet, row) -> new StoredConversation(resultSet.getObject("id", UUID.class),
                        resultSet.getString("source_fingerprint")), arguments)));
    }

    @Override
    public void insert(UUID workspaceId, UUID conversationId, NormalizedConversation conversation,
                       String fingerprint, Instant now) {
        jdbc.update("""
                insert into conversations
                  (id, workspace_id, source_type, source_provider, external_id, title,
                   source_created_at, source_updated_at, source_fingerprint, metadata,
                   imported_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                """, conversationId, workspaceId, conversation.sourceType().name(),
                conversation.sourceProvider(), conversation.externalId(), conversation.title(),
                timestamp(conversation.createdAt()), timestamp(conversation.updatedAt()), fingerprint,
                json(conversation.metadata()), timestamp(now), timestamp(now), timestamp(now));

        for (int sequence = 0; sequence < conversation.messages().size(); sequence++) {
            var message = conversation.messages().get(sequence);
            var generation = message.generation();
            jdbc.update("""
                    insert into messages
                      (id, workspace_id, conversation_id, external_id, sequence_no, role,
                       source_created_at, parent_external_id, content, generation_provider,
                       generation_model, metadata, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?)
                    """, UUID.randomUUID(), workspaceId, conversationId, message.externalId(), sequence,
                    message.role().name(), timestamp(message.createdAt()), message.parentExternalId(),
                    contentJson(message), generation == null ? null : generation.provider(),
                    generation == null ? null : generation.model(), json(message.metadata()), timestamp(now));
        }
    }

    private String contentJson(io.contextmesh.conversation.domain.NormalizedMessage message) {
        return json(message.content().stream().map(part -> {
            var value = new LinkedHashMap<String, Object>();
            value.put("type", part.type().name());
            if (part instanceof TextContentPart text) value.put("text", text.text());
            return value;
        }).toList());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Normalized value cannot be serialized", exception);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
