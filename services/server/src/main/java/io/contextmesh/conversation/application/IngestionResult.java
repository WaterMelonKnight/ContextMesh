package io.contextmesh.conversation.application;

import java.util.UUID;

public record IngestionResult(IngestionStatus status, UUID conversationId,
                              int messagesInserted, String fingerprint) {
}
