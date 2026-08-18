package io.contextmesh.provider.adapter.openaicompatible;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.provider.adapter.fake.FakeModelProvider;
import io.contextmesh.provider.application.ModelProviderRegistry;
import io.contextmesh.provider.application.ProviderDescriptor;
import io.contextmesh.provider.application.ProviderKind;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Provider status is derived from real bean registration, so these cases define what the status API
 * can report. The adapter rejects an enabled-but-incomplete configuration at startup, which means
 * "enabled but unconfigured" is not a reachable runtime state and needs no status value.
 */
class OpenAICompatibleProviderRegistrationTest {
    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withBean(ObjectMapper.class)
            .withUserConfiguration(FakeModelProvider.class, OpenAICompatibleModelProvider.class,
                    ModelProviderRegistry.class);

    @Test
    void isNotRegisteredWhileDisabled() {
        contexts.withPropertyValues("contextmesh.providers.openai-compatible.enabled=false")
                .run(context -> assertThat(descriptors(context)).extracting(ProviderDescriptor::id)
                        .containsExactly("fake"));
    }

    @Test
    void isRegisteredAsExternalWhenEnabledAndConfigured() {
        contexts.withPropertyValues(
                        "contextmesh.providers.openai-compatible.enabled=true",
                        "contextmesh.providers.openai-compatible.base-url=https://endpoint.invalid/v1",
                        "contextmesh.providers.openai-compatible.api-key=sk-registration-secret")
                .run(context -> assertThat(descriptors(context))
                        .containsExactly(new ProviderDescriptor("fake", "Fake (local, deterministic)",
                                        ProviderKind.BUILT_IN, "fake-model"),
                                new ProviderDescriptor("openai-compatible", "OpenAI-compatible",
                                        ProviderKind.EXTERNAL, null)));
    }

    @Test
    void publishesTheOptionalServerConfiguredModelDefault() {
        contexts.withPropertyValues(
                        "contextmesh.providers.openai-compatible.enabled=true",
                        "contextmesh.providers.openai-compatible.base-url=https://endpoint.invalid/v1",
                        "contextmesh.providers.openai-compatible.api-key=sk-registration-secret",
                        "contextmesh.providers.openai-compatible.default-model=configured-model")
                .run(context -> assertThat(descriptors(context))
                        .filteredOn(descriptor -> descriptor.id().equals("openai-compatible"))
                        .extracting(ProviderDescriptor::defaultModel).containsExactly("configured-model"));
    }

    @Test
    void stillRefusesToStartWhenEnabledWithoutCredentials() {
        contexts.withPropertyValues(
                        "contextmesh.providers.openai-compatible.enabled=true",
                        "contextmesh.providers.openai-compatible.base-url=https://endpoint.invalid/v1")
                .run(context -> assertThat(context).hasFailed());
    }

    private java.util.List<ProviderDescriptor> descriptors(
            org.springframework.context.ApplicationContext context) {
        return context.getBean(ModelProviderRegistry.class).describeRegistered();
    }
}
