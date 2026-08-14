package io.contextmesh.provider.application;

public final class UnknownModelProviderException extends RuntimeException {
    public UnknownModelProviderException(String provider) { super("Unknown model provider: " + provider); }
}
