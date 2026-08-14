package io.contextmesh.conversation.application;

public final class ImportedConversationImmutableException extends RuntimeException {
    public ImportedConversationImmutableException() { super("Imported conversations are immutable"); }
}
