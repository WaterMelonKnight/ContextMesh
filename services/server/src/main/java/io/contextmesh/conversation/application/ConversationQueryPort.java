package io.contextmesh.conversation.application;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ConversationQueryPort {
    Optional<ConversationView> find(UUID workspaceId, UUID conversationId);
    List<ConversationSummary> list(UUID workspaceId, int limit);
}
