package io.contextmesh.provider.application;

/**
 * A provider failure carrying a stable, provider-neutral reason.
 *
 * <p>The reason lets application code explain a failure without knowing which adapter produced it
 * and without forwarding upstream response bodies, headers, or credentials. Messages are fixed
 * adapter-authored strings; they must never embed upstream content.
 */
public abstract class ModelProviderException extends RuntimeException {
    public enum Reason {
        /** The endpoint rejected the configured credential. */
        AUTHENTICATION,
        /** The endpoint applied a rate or quota limit. */
        RATE_LIMIT,
        /** The endpoint could not be reached or failed to answer. */
        UNAVAILABLE,
        /** The endpoint answered, but not with a usable stream. */
        PROTOCOL
    }

    private final Reason reason;

    protected ModelProviderException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    protected ModelProviderException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
