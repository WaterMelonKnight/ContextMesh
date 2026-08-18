package io.contextmesh.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.contextmesh.provider.adapter.fake.FakeModelProvider;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Availability semantics of the provider status model: registration is the readiness contract. */
class ProviderCatalogTest {
    private final ModelProvider external = stub("z-external");
    private final ModelProvider other = stub("a-external");

    @Test
    void describesEveryRegisteredProviderInDeterministicIdentifierOrder() {
        var registry = new ModelProviderRegistry(List.of(external, new FakeModelProvider(), other));

        assertThat(registry.describeRegistered()).extracting(ProviderDescriptor::id)
                .containsExactly("a-external", "fake", "z-external");
        // Bean discovery order must not change the published catalogue.
        assertThat(new ModelProviderRegistry(List.of(other, external, new FakeModelProvider())).describeRegistered())
                .isEqualTo(registry.describeRegistered());
    }

    @Test
    void describedProvidersAreExactlyTheResolvableOnes() {
        var registry = new ModelProviderRegistry(List.of(new FakeModelProvider(), external));

        for (var descriptor : registry.describeRegistered())
            assertThat(registry.resolve(descriptor.id()).providerId()).isEqualTo(descriptor.id());
        assertThatThrownBy(() -> registry.resolve("openai-compatible"))
                .isInstanceOf(UnknownModelProviderException.class);
        assertThat(registry.describeRegistered()).extracting(ProviderDescriptor::id)
                .doesNotContain("openai-compatible");
    }

    @Test
    void fakeProviderRemainsAvailableWithAUsableModelDefault() {
        assertThat(new ModelProviderRegistry(List.of(new FakeModelProvider())).describeRegistered())
                .containsExactly(new ProviderDescriptor("fake", "Fake (local, deterministic)",
                        ProviderKind.BUILT_IN, "fake-model"));
    }

    @Test
    void statusModelCannotCarryCredentials() {
        // Guards the contract itself: a future component named for a key, header, secret, token, or
        // endpoint would leak server configuration to every browser that reads the status API.
        assertThat(Arrays.stream(ProviderDescriptor.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "displayName", "kind", "defaultModel");
        assertThat(new ProviderDescriptor("id", null, ProviderKind.EXTERNAL, "  ").defaultModel()).isNull();
        assertThat(new ProviderDescriptor("id", "  ", ProviderKind.EXTERNAL, null).displayName()).isEqualTo("id");
    }

    private ModelProvider stub(String id) {
        return new ModelProvider() {
            @Override public String providerId() { return id; }
            @Override public GenerationStream generate(ModelGenerationRequest request) {
                return sink -> { };
            }
        };
    }
}
