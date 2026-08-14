package io.contextmesh.provider.adapter.fake;

import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.provider.application.GenerationStream;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelProvider;
import org.springframework.stereotype.Component;

@Component
public final class FakeModelProvider implements ModelProvider {
    public static final String PROVIDER_ID = "fake";

    @Override public String providerId() { return PROVIDER_ID; }

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
