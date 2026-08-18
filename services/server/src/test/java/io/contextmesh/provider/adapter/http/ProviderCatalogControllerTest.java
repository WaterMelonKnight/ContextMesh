package io.contextmesh.provider.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.contextmesh.provider.adapter.fake.FakeModelProvider;
import io.contextmesh.provider.application.GenerationStream;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelProvider;
import io.contextmesh.provider.application.ModelProviderRegistry;
import io.contextmesh.provider.application.ProviderDescriptor;
import io.contextmesh.provider.application.ProviderKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProviderCatalogControllerTest {
    private static final String SECRET = "sk-controller-secret";

    @Test
    void publishesRegisteredProvidersWithoutAnyCredential() throws Exception {
        var body = mvc(new FakeModelProvider(), external()).perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("fake"))
                .andExpect(jsonPath("$[0].kind").value("BUILT_IN"))
                .andExpect(jsonPath("$[0].defaultModel").value("fake-model"))
                .andExpect(jsonPath("$[1].id").value("openai-compatible"))
                .andExpect(jsonPath("$[1].displayName").value("OpenAI-compatible"))
                .andExpect(jsonPath("$[1].kind").value("EXTERNAL"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(SECRET).doesNotContain("apiKey").doesNotContain("Authorization")
                .doesNotContain("baseUrl").doesNotContain("defaultHeaders");
    }

    @Test
    void omitsProvidersThatAreNotRegistered() throws Exception {
        mvc(new FakeModelProvider()).perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("fake"));
    }

    private org.springframework.test.web.servlet.MockMvc mvc(ModelProvider... providers) {
        return MockMvcBuilders.standaloneSetup(
                new ProviderCatalogController(new ModelProviderRegistry(List.of(providers)))).build();
    }

    /** Stands in for a configured external adapter that holds a credential it must not publish. */
    private ModelProvider external() {
        return new ModelProvider() {
            @Override public String providerId() { return "openai-compatible"; }
            @Override public ProviderDescriptor describe() {
                return new ProviderDescriptor(providerId(), "OpenAI-compatible", ProviderKind.EXTERNAL, null);
            }
            @Override public GenerationStream generate(ModelGenerationRequest request) {
                return sink -> { throw new IllegalStateException(SECRET); };
            }
        };
    }
}
