package io.contextmesh.conversation.adapter.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.application.ConversationPersistencePort;
import io.contextmesh.conversation.application.ConversationQueryPort;
import io.contextmesh.conversation.application.ConversationView;
import io.contextmesh.conversation.application.ConversationNotFoundException;
import io.contextmesh.conversation.application.ContinuationOrigin;
import io.contextmesh.conversation.application.ContinuationPersistencePort;
import io.contextmesh.conversation.application.ImportedConversationImmutableException;
import io.contextmesh.conversation.application.NativeConversationPersistencePort;
import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageContentPart;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.NormalizedConversation;
import io.contextmesh.conversation.domain.TextContentPart;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConversationPersistenceAdapter implements ConversationPersistencePort,
        NativeConversationPersistencePort, ConversationQueryPort, ContinuationPersistencePort {
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

    @Override
    public void create(UUID workspaceId, UUID conversationId, String title, Instant now) {
        jdbc.update("""
                insert into conversations
                  (id, workspace_id, source_type, title, metadata, created_at, updated_at)
                values (?, ?, 'NATIVE_CONVERSATION', ?, '{}'::jsonb, ?, ?)
                """, conversationId, workspaceId, title, timestamp(now), timestamp(now));
    }

    @Override
    public void create(UUID workspaceId, UUID targetConversationId, UUID sourceConversationId,
            UUID throughMessageId, Instant createdAt) {
        jdbc.update("""
                insert into conversation_continuations
                  (target_conversation_id, workspace_id, source_conversation_id, through_message_id, created_at)
                values (?, ?, ?, ?, ?)
                """, targetConversationId, workspaceId, sourceConversationId, throughMessageId,
                timestamp(createdAt));
    }

    @Override
    public Optional<ContinuationOrigin> findOrigin(UUID workspaceId, UUID targetConversationId) {
        return Optional.ofNullable(DataAccessUtils.singleResult(jdbc.query("""
                select source_conversation_id, through_message_id from conversation_continuations
                where workspace_id = ? and target_conversation_id = ?
                """, (rs, row) -> new ContinuationOrigin(
                        rs.getObject("source_conversation_id", UUID.class),
                        rs.getObject("through_message_id", UUID.class)),
                workspaceId, targetConversationId)));
    }

    @Override
    public AppendResult append(UUID workspaceId, UUID conversationId, UUID messageId,
            MessageRole role, List<MessageContentPart> content, GenerationMetadata generation,
            Map<String, Object> metadata, Instant now) {
        var header = jdbc.query("""
                select source_type from conversations
                where workspace_id = ? and id = ? for update
                """, (rs, row) -> ConversationSourceType.valueOf(rs.getString(1)),
                workspaceId, conversationId);
        if (header.isEmpty()) throw new ConversationNotFoundException();
        if (header.getFirst() != ConversationSourceType.NATIVE_CONVERSATION)
            throw new ImportedConversationImmutableException();

        var previous = jdbc.query("""
                select external_id, sequence_no from messages
                where workspace_id = ? and conversation_id = ? order by sequence_no desc limit 1
                """, (rs, row) -> new PreviousMessage(rs.getString("external_id"),
                        rs.getInt("sequence_no")), workspaceId, conversationId);
        int sequence = previous.isEmpty() ? 0 : previous.getFirst().sequenceNo() + 1;
        String parent = previous.isEmpty() ? null : previous.getFirst().stableId();
        String stableId = messageId.toString();
        jdbc.update("""
                insert into messages
                  (id, workspace_id, conversation_id, external_id, sequence_no, role,
                   parent_external_id, content, generation_provider, generation_model,
                   metadata, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?)
                """, messageId, workspaceId, conversationId, stableId, sequence, role.name(),
                parent, contentJson(content), generation == null ? null : generation.provider(),
                generation == null ? null : generation.model(), json(metadata), timestamp(now));
        jdbc.update("update conversations set updated_at = ? where workspace_id = ? and id = ?",
                timestamp(now), workspaceId, conversationId);
        return new AppendResult(sequence, parent);
    }

    @Override
    public Optional<ConversationView> find(UUID workspaceId, UUID conversationId) {
        var conversations = jdbc.query("""
                select * from conversations where workspace_id = ? and id = ?
                """, (rs, row) -> new ConversationView(
                        rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                        ConversationSourceType.valueOf(rs.getString("source_type")),
                        rs.getString("source_provider"), rs.getString("external_id"),
                        rs.getString("title"), instant(rs.getTimestamp("source_created_at")),
                        instant(rs.getTimestamp("source_updated_at")), instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("updated_at")), jsonMap(rs.getString("metadata")), List.of()),
                workspaceId, conversationId);
        if (conversations.isEmpty()) return Optional.empty();
        var header = conversations.getFirst();
        var messages = jdbc.query("""
                select * from messages where workspace_id = ? and conversation_id = ? order by sequence_no
                """, (rs, row) -> {
                    String provider = rs.getString("generation_provider");
                    String model = rs.getString("generation_model");
                    return new ConversationView.MessageView(rs.getObject("id", UUID.class),
                            rs.getString("external_id") == null
                                    ? rs.getObject("id", UUID.class).toString() : rs.getString("external_id"),
                            rs.getInt("sequence_no"), MessageRole.valueOf(rs.getString("role")),
                            content(rs.getString("content")), instant(rs.getTimestamp("source_created_at")),
                            instant(rs.getTimestamp("created_at")), rs.getString("parent_external_id"),
                            provider == null ? null : new GenerationMetadata(provider, model),
                            jsonMap(rs.getString("metadata")));
                }, workspaceId, conversationId);
        return Optional.of(new ConversationView(header.id(), header.workspaceId(), header.sourceType(),
                header.sourceProvider(), header.externalId(), header.title(), header.sourceCreatedAt(),
                header.sourceUpdatedAt(), header.createdAt(), header.updatedAt(), header.metadata(), messages));
    }

    private String contentJson(io.contextmesh.conversation.domain.NormalizedMessage message) {
        return contentJson(message.content());
    }

    private String contentJson(List<MessageContentPart> content) {
        return json(content.stream().map(part -> {
            var value = new LinkedHashMap<String, Object>();
            value.put("type", part.type().name());
            if (part instanceof TextContentPart text) value.put("text", text.text());
            return value;
        }).toList());
    }

    private List<MessageContentPart> content(String value) {
        try {
            var root = objectMapper.readTree(value);
            var result = new java.util.ArrayList<MessageContentPart>();
            for (var part : root) {
                if (!"TEXT".equals(part.path("type").asText()))
                    throw new IllegalStateException("Unsupported stored content part type");
                result.add(new TextContentPart(part.path("text").asText()));
            }
            return List.copyOf(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored message content is invalid", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(String value) {
        try {
            return objectMapper.readValue(value, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored metadata is invalid", exception);
        }
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

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record PreviousMessage(String stableId, int sequenceNo) {}
}
