package io.contextmesh.provider.application;

import java.util.UUID;

public sealed interface GenerationEvent permits GenerationEvent.Started, GenerationEvent.TextDelta,
        GenerationEvent.Completed, GenerationEvent.Failed {
    record Started(String provider, String model) implements GenerationEvent {
        public Started { require(provider, "provider"); require(model, "model"); }
    }
    record TextDelta(String text) implements GenerationEvent {
        public TextDelta { if (text == null || text.isEmpty()) throw new IllegalArgumentException("delta text must not be empty"); }
    }
    record Completed(String provider, String model, UUID assistantMessageId) implements GenerationEvent {
        public Completed { require(provider, "provider"); require(model, "model"); }
    }
    record Failed(String code, String message) implements GenerationEvent {
        public Failed { require(code, "code"); require(message, "message"); }
    }
    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
