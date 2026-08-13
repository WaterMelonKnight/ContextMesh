package io.contextmesh.ingestion.adapter.chatgpt;

public final class ChatGptExportException extends IllegalArgumentException {
    public ChatGptExportException(String message) { super(message); }
    public ChatGptExportException(String message, Throwable cause) { super(message, cause); }
}
