package io.contextmesh.conversation.application;

import java.util.Optional;
import java.util.UUID;

public interface ConversationQueryPort {
    Optional<ConversationView> find(UUID workspaceId, UUID conversationId);
}
