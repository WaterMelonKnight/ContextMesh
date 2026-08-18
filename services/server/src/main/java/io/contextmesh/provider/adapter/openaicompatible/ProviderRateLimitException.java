package io.contextmesh.provider.adapter.openaicompatible;

import io.contextmesh.provider.application.ModelProviderException;

public final class ProviderRateLimitException extends ModelProviderException {
    public ProviderRateLimitException() { super(Reason.RATE_LIMIT, "Provider rate limit exceeded"); }
}
