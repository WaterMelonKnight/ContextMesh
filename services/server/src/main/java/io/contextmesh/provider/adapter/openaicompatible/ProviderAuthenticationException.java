package io.contextmesh.provider.adapter.openaicompatible;

public final class ProviderAuthenticationException extends RuntimeException {
    public ProviderAuthenticationException() { super("Provider authentication or authorization failed"); }
}
