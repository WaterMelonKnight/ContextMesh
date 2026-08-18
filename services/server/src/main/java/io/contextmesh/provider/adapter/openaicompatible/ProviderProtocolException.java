package io.contextmesh.provider.adapter.openaicompatible;

import io.contextmesh.provider.application.ModelProviderException;

public final class ProviderProtocolException extends ModelProviderException {
    public ProviderProtocolException(String message) { super(Reason.PROTOCOL, message); }
    public ProviderProtocolException(String message, Throwable cause) {
        super(Reason.PROTOCOL, message, cause);
    }
}
