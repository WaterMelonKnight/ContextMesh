package io.contextmesh.provider.adapter.openaicompatible;

public final class ProviderProtocolException extends RuntimeException {
    public ProviderProtocolException(String message) { super(message); }
    public ProviderProtocolException(String message, Throwable cause) { super(message, cause); }
}
