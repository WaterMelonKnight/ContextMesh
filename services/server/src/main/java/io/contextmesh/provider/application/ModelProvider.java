package io.contextmesh.provider.application;

public interface ModelProvider {
    String providerId();
    GenerationStream generate(ModelGenerationRequest request);
}
