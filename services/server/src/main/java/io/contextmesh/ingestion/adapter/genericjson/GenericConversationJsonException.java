package io.contextmesh.ingestion.adapter.genericjson;

public final class GenericConversationJsonException extends IllegalArgumentException {
    public GenericConversationJsonException(String message) { super(message); }
    public GenericConversationJsonException(String message, Throwable cause) { super(message, cause); }
}
