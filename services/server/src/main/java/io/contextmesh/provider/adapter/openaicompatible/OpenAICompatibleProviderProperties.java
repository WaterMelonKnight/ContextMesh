package io.contextmesh.provider.adapter.openaicompatible;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("contextmesh.providers.openai-compatible")
public record OpenAICompatibleProviderProperties(boolean enabled, URI baseUrl, String apiKey,
        Duration connectionTimeout, Duration readTimeout, Map<String, String> defaultHeaders) {
    public OpenAICompatibleProviderProperties {
        connectionTimeout = connectionTimeout == null ? Duration.ofSeconds(10) : connectionTimeout;
        readTimeout = readTimeout == null ? Duration.ofMinutes(2) : readTimeout;
        defaultHeaders = defaultHeaders == null ? Map.of() : Map.copyOf(defaultHeaders);
        if (enabled) validate(baseUrl, apiKey, connectionTimeout, readTimeout, defaultHeaders);
    }

    private static void validate(URI baseUrl, String apiKey, Duration connectionTimeout,
            Duration readTimeout, Map<String, String> headers) {
        if (baseUrl == null || baseUrl.getHost() == null
                || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme())))
            throw new IllegalArgumentException("OpenAI-compatible provider base-url must be an absolute HTTP(S) URL");
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalArgumentException("OpenAI-compatible provider api-key must be configured when enabled");
        if (apiKey.indexOf('\r') >= 0 || apiKey.indexOf('\n') >= 0)
            throw new IllegalArgumentException("OpenAI-compatible provider api-key contains invalid characters");
        if (connectionTimeout.isZero() || connectionTimeout.isNegative())
            throw new IllegalArgumentException("OpenAI-compatible provider connection-timeout must be positive");
        if (readTimeout.isZero() || readTimeout.isNegative())
            throw new IllegalArgumentException("OpenAI-compatible provider read-timeout must be positive");
        headers.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null || name.equalsIgnoreCase("Authorization"))
                throw new IllegalArgumentException("OpenAI-compatible default headers must be non-null and cannot override Authorization");
        });
    }
}
