package io.contextmesh.conversation.application;

import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageContentPart;
import io.contextmesh.conversation.domain.MessageRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NativeConversationPersistencePort {
    void create(UUID workspaceId, UUID conversationId, String title, Instant now);

    AppendResult append(UUID workspaceId, UUID conversationId, UUID messageId, MessageRole role,
            List<MessageContentPart> content, GenerationMetadata generation,
            Map<String, Object> metadata, Instant now);

    record AppendResult(int sequenceNo, String parentExternalId) {}
}
