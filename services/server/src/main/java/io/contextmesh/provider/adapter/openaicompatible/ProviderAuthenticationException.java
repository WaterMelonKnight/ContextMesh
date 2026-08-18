package io.contextmesh.provider.adapter.openaicompatible;

import io.contextmesh.provider.application.ModelProviderException;

public final class ProviderAuthenticationException extends ModelProviderException {
    public ProviderAuthenticationException() {
        super(Reason.AUTHENTICATION, "Provider authentication or authorization failed");
    }
}
