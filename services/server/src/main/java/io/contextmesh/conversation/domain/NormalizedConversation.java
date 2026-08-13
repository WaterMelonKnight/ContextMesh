package io.contextmesh.conversation.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NormalizedConversation(String externalId, String title,
                                     ConversationSourceType sourceType, String sourceProvider,
                                     Instant createdAt, Instant updatedAt,
                                     List<NormalizedMessage> messages, Map<String, Object> metadata) {
    public NormalizedConversation {
        externalId = NormalizedMessage.optionalId(externalId, "externalId");
        if (title != null && title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        if (sourceProvider != null && sourceProvider.isBlank()) throw new IllegalArgumentException("sourceProvider must not be blank");
        Objects.requireNonNull(sourceType, "sourceType");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        metadata = MetadataLimits.copyAndValidate(metadata);
        var ids = new HashSet<String>();
        for (var message : messages) {
            if (message.externalId() != null && !ids.add(message.externalId()))
                throw new IllegalArgumentException("duplicate message externalId: " + message.externalId());
        }
    }
}
