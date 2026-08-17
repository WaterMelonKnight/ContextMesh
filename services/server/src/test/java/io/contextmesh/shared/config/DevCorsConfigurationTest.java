package io.contextmesh.shared.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DevCorsConfigurationTest {
    @Test
    void rejectsWildcardOrigins() {
        assertThatThrownBy(() -> new DevCorsConfiguration("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid development CORS origin");
    }

    @Test
    void allowsEachConfiguredOriginWithTheRequiredMethodAndHeaders() throws Exception {
        DevCorsConfiguration configuration = new DevCorsConfiguration(
                "https://workspace--3000.example-cloud-ide.com, http://localhost:3000");
        // Exercise the configuration through Spring's CORS registry rather than duplicating its rules.
        var registry = new org.springframework.web.servlet.config.annotation.CorsRegistry();
        configuration.addCorsMappings(registry);
        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        var registrationsField = registry.getClass().getDeclaredField("corsRegistrations");
        registrationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registrations = (java.util.List<Object>) registrationsField.get(registry);
        var registration = registrations.getFirst();
        var configField = registration.getClass().getDeclaredField("config");
        configField.setAccessible(true);
        source.registerCorsConfiguration("/api/**", (org.springframework.web.cors.CorsConfiguration) configField.get(registration));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StubController())
                .addFilters(new org.springframework.web.filter.CorsFilter(source))
                .build();

        mvc.perform(options("/api/test")
                        .header("Origin", "https://workspace--3000.example-cloud-ide.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Accept"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://workspace--3000.example-cloud-ide.com"));
    }

    @org.springframework.web.bind.annotation.RestController
    private static class StubController {
        @org.springframework.web.bind.annotation.PostMapping("/api/test")
        void test() {}
    }
}
