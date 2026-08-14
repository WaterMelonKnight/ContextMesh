package io.contextmesh.conversation.application;

import java.util.UUID;

public record ContinuationOrigin(UUID sourceConversationId, UUID throughMessageId) {}
