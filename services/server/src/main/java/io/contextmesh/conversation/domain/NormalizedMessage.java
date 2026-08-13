package io.contextmesh.conversation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record NormalizedMessage(String externalId, MessageRole role,
                                List<MessageContentPart> content, Instant createdAt,
                                String parentExternalId, GenerationMetadata generation,
                                Map<String, Object> metadata) {
    public NormalizedMessage {
        externalId = optionalId(externalId, "externalId");
        parentExternalId = optionalId(parentExternalId, "parentExternalId");
        Objects.requireNonNull(role, "role");
        content = List.copyOf(Objects.requireNonNull(content, "content"));
        if (content.isEmpty()) throw new IllegalArgumentException("content must not be empty");
        metadata = MetadataLimits.copyAndValidate(metadata);
    }
    static String optionalId(String value, String field) {
        if (value != null && value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
