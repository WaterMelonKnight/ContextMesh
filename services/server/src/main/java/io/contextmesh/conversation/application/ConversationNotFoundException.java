package io.contextmesh.conversation.application;

public final class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException() { super("Conversation was not found in this workspace"); }
}
