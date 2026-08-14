package io.contextmesh.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.contextmesh.provider.adapter.fake.FakeModelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelProviderTest {
    @Test
    void fakeProviderEmitsDeterministicMultipleChunks() {
        var events = new ArrayList<GenerationEvent>();
        new FakeModelProvider().generate(new ModelGenerationRequest(UUID.randomUUID(), UUID.randomUUID(),
                "fake-model", List.of(new ModelMessage(ModelMessage.Role.USER, "Hello"))))
                .consume(events::add);
        assertThat(events).containsExactly(
                new GenerationEvent.Started("fake", "fake-model"),
                new GenerationEvent.TextDelta("Fake "),
                new GenerationEvent.TextDelta("response"),
                new GenerationEvent.Completed("fake", "fake-model", null));
    }

    @Test
    void registryResolvesAndRejectsUnknownOrDuplicateProviders() {
        var fake = new FakeModelProvider();
        assertThat(new ModelProviderRegistry(List.of(fake)).resolve("fake")).isSameAs(fake);
        assertThatThrownBy(() -> new ModelProviderRegistry(List.of(fake)).resolve("missing"))
                .isInstanceOf(UnknownModelProviderException.class)
                .hasMessage("Unknown model provider: missing");
        assertThatThrownBy(() -> new ModelProviderRegistry(List.of(fake, fake)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Duplicate model provider: fake");
    }
}
