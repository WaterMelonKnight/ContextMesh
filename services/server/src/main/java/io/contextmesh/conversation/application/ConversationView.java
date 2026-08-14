package io.contextmesh.conversation.application;

import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageContentPart;
import io.contextmesh.conversation.domain.MessageRole;
import java.time.Instant;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ConversationView(UUID id, UUID workspaceId, ConversationSourceType sourceType,
        String sourceProvider, String externalId, String title, Instant sourceCreatedAt,
        Instant sourceUpdatedAt, Instant createdAt, Instant updatedAt, Map<String, Object> metadata,
        List<MessageView> messages) {
    public ConversationView {
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        messages = List.copyOf(messages);
    }

    public record MessageView(UUID id, String stableId, int sequenceNo, MessageRole role,
            List<MessageContentPart> content, Instant sourceCreatedAt, Instant createdAt,
            String parentExternalId, GenerationMetadata generation, Map<String, Object> metadata) {
        public MessageView {
            content = List.copyOf(content);
            metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }
    }
}
