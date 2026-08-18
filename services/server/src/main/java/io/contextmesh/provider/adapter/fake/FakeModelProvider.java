package io.contextmesh.provider.adapter.fake;

import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.provider.application.GenerationStream;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelProvider;
import io.contextmesh.provider.application.ProviderDescriptor;
import io.contextmesh.provider.application.ProviderKind;
import org.springframework.stereotype.Component;

@Component
public final class FakeModelProvider implements ModelProvider {
    public static final String PROVIDER_ID = "fake";
    /** The provider ignores the requested model; this is only a usable default for clients. */
    public static final String DEFAULT_MODEL = "fake-model";

    @Override public String providerId() { return PROVIDER_ID; }

    @Override
    public ProviderDescriptor describe() {
        return new ProviderDescriptor(PROVIDER_ID, "Fake (local, deterministic)",
                ProviderKind.BUILT_IN, DEFAULT_MODEL);
    }

    @Override
    public GenerationStream generate(ModelGenerationRequest request) {
        return sink -> {
            sink.accept(new GenerationEvent.Started(PROVIDER_ID, request.model()));
            sink.accept(new GenerationEvent.TextDelta("Fake "));
            sink.accept(new GenerationEvent.TextDelta("response"));
            sink.accept(new GenerationEvent.Completed(PROVIDER_ID, request.model(), null));
        };
    }
}
