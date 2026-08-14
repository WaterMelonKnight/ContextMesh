package io.contextmesh.conversation.application;

import java.util.List;
import java.util.UUID;

public interface ContinuationContextResolver {
    List<ConversationView.MessageView> resolve(UUID workspaceId, UUID nativeConversationId);
}
