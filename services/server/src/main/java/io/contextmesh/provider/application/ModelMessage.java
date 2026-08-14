package io.contextmesh.provider.application;

import java.util.Objects;

public record ModelMessage(Role role, String text) {
    public ModelMessage {
        Objects.requireNonNull(role, "role");
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("message text must not be empty");
    }

    public enum Role { SYSTEM, USER, ASSISTANT }
}
