package io.contextmesh.conversation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.NormalizedConversation;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationIngestionService {
    private final ConversationPersistencePort persistence;
    private final ConversationFingerprint fingerprint;
    private final Clock clock;

    public ConversationIngestionService(ConversationPersistencePort persistence, ObjectMapper objectMapper) {
        this(persistence, objectMapper, Clock.systemUTC());
    }

    ConversationIngestionService(ConversationPersistencePort persistence, ObjectMapper objectMapper, Clock clock) {
        this.persistence = persistence;
        this.fingerprint = new ConversationFingerprint(objectMapper);
        this.clock = clock;
    }

    @Transactional
    public IngestionResult ingest(UUID workspaceId, NormalizedConversation conversation) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(conversation, "conversation");
        String hash = fingerprint.calculate(conversation);
        var existing = persistence.findBySourceIdentity(workspaceId, conversation, hash);
        if (existing.isPresent()) {
            var stored = existing.orElseThrow();
            var status = stored.fingerprint().equals(hash)
                    ? IngestionStatus.SKIPPED_DUPLICATE : IngestionStatus.CONFLICT;
            return new IngestionResult(status, stored.id(), 0, hash);
        }
        UUID id = UUID.randomUUID();
        persistence.insert(workspaceId, id, conversation, hash, clock.instant());
        return new IngestionResult(IngestionStatus.IMPORTED, id, conversation.messages().size(), hash);
    }
}
