package io.contextmesh.shared.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Exercises the dev CORS policy through the real Spring MVC request pipeline, so the assertions
 * cover the behaviour a browser observes rather than the configuration object's internals.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = DevCorsConfigurationTest.TestMvcConfiguration.class)
@ActiveProfiles("dev")
@TestPropertySource(properties =
        "contextmesh.dev.allowed-origins=https://workspace--3000.example-cloud-ide.com, http://localhost:3000")
class DevCorsConfigurationTest {
    private static final String REMOTE_ORIGIN = "https://workspace--3000.example-cloud-ide.com";
    private static final String LOCAL_ORIGIN = "http://localhost:3000";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void allowsConfiguredRemoteOriginWithTheRequiredMethodAndHeaders() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, REMOTE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Accept"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REMOTE_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Accept")));
    }

    @Test
    void allowsEveryCommaSeparatedOrigin() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, LOCAL_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCAL_ORIGIN));
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void rejectsMethodsOutsideTheAllowedSet() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, REMOTE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void rejectsWildcardOrigins() {
        assertThatThrownBy(() -> new DevCorsConfiguration("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid development CORS origin");
    }

    @Test
    void rejectsMalformedOrigins() {
        assertThatThrownBy(() -> new DevCorsConfiguration("http://localhost:3000/api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid development CORS origin");
    }

    @Test
    void requiresAtLeastOneOrigin() {
        assertThatThrownBy(() -> new DevCorsConfiguration(" , "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one development CORS origin is required");
    }

    @Configuration
    @EnableWebMvc
    @Import(DevCorsConfiguration.class)
    static class TestMvcConfiguration {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        StubController stubController() {
            return new StubController();
        }
    }

    @RestController
    static class StubController {
        @GetMapping("/api/test")
        void get() {}

        @PostMapping("/api/test")
        void post() {}

        // Mapped so the DELETE preflight reaches the CORS processor instead of failing to route,
        // which keeps that test about the allowed-method policy rather than about handler lookup.
        @DeleteMapping("/api/test")
        void delete() {}
    }
}
