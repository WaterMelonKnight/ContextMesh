package io.contextmesh.provider.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ModelGenerationRequest(UUID workspaceId, UUID conversationId, String model,
        List<ModelMessage> messages) {
    public ModelGenerationRequest {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(conversationId, "conversationId");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) throw new IllegalArgumentException("messages must not be empty");
    }
}
