package io.contextmesh.provider.adapter.openaicompatible;

public final class ProviderRateLimitException extends RuntimeException {
    public ProviderRateLimitException() { super("Provider rate limit exceeded"); }
}
