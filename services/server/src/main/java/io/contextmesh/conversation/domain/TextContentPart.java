package io.contextmesh.conversation.domain;

import java.util.Objects;

public record TextContentPart(String text) implements MessageContentPart {
    public TextContentPart {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) throw new IllegalArgumentException("text must not be empty");
    }

    @Override public ContentPartType type() { return ContentPartType.TEXT; }
}
