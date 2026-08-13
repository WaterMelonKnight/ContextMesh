package io.contextmesh.conversation.application;

import io.contextmesh.conversation.domain.NormalizedConversation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversationPersistencePort {
    Optional<StoredConversation> findBySourceIdentity(UUID workspaceId, NormalizedConversation conversation,
                                                       String fingerprint);
    void insert(UUID workspaceId, UUID conversationId, NormalizedConversation conversation,
                String fingerprint, Instant now);

    record StoredConversation(UUID id, String fingerprint) {}
}
