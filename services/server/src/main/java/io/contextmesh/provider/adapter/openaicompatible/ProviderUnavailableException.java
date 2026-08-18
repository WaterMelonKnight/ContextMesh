package io.contextmesh.provider.adapter.openaicompatible;

import io.contextmesh.provider.application.ModelProviderException;

public final class ProviderUnavailableException extends ModelProviderException {
    public ProviderUnavailableException(String message) { super(Reason.UNAVAILABLE, message); }
    public ProviderUnavailableException(String message, Throwable cause) {
        super(Reason.UNAVAILABLE, message, cause);
    }
}
