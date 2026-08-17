package io.contextmesh.shared.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("dev")
class DevCorsConfiguration implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    DevCorsConfiguration(
            @Value("${contextmesh.dev.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(DevCorsConfiguration::validateOrigin)
                .toArray(String[]::new);
        if (this.allowedOrigins.length == 0) {
            throw new IllegalArgumentException("At least one development CORS origin is required");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Accept");
    }

    private static String validateOrigin(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid development CORS origin: " + origin, exception);
        }
        boolean validScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        boolean rootPath = uri.getPath().isEmpty() || "/".equals(uri.getPath());
        if (!validScheme || uri.getHost() == null || uri.getUserInfo() != null || !rootPath
                || uri.getQuery() != null || uri.getFragment() != null || origin.contains("*")) {
            throw new IllegalArgumentException("Invalid development CORS origin: " + origin);
        }
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
}
