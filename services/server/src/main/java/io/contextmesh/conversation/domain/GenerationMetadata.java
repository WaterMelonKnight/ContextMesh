package io.contextmesh.conversation.domain;

public record GenerationMetadata(String provider, String model) {
    public GenerationMetadata {
        provider = required(provider, "provider");
        model = required(model, "model");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
