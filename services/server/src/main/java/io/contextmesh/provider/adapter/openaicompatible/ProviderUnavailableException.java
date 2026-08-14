package io.contextmesh.provider.adapter.openaicompatible;

public final class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) { super(message); }
    public ProviderUnavailableException(String message, Throwable cause) { super(message, cause); }
}
