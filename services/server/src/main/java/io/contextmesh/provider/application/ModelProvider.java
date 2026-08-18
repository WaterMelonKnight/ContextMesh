package io.contextmesh.provider.application;

public interface ModelProvider {
    String providerId();

    /**
     * Non-secret metadata for the provider status API. A registered provider is callable, so the
     * default treats an unknown adapter conservatively as external with no server-side model
     * default.
     */
    default ProviderDescriptor describe() {
        return new ProviderDescriptor(providerId(), providerId(), ProviderKind.EXTERNAL, null);
    }

    GenerationStream generate(ModelGenerationRequest request);
}
