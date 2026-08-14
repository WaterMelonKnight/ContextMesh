package io.contextmesh.conversation.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContinuationPersistencePort {
    void create(UUID workspaceId, UUID targetConversationId, UUID sourceConversationId,
            UUID throughMessageId, Instant createdAt);

    Optional<ContinuationOrigin> findOrigin(UUID workspaceId, UUID targetConversationId);
}
