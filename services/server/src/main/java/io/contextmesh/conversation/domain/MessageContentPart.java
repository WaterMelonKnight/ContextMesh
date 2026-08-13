package io.contextmesh.conversation.domain;

public sealed interface MessageContentPart permits TextContentPart {
    ContentPartType type();
}
