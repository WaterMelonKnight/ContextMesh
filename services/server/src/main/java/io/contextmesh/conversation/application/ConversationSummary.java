package io.contextmesh.conversation.application;

import io.contextmesh.conversation.domain.ConversationSourceType;
import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(UUID id, ConversationSourceType sourceType, String sourceProvider,
        String title, Instant createdAt, Instant updatedAt, ContinuationOrigin origin) {}
