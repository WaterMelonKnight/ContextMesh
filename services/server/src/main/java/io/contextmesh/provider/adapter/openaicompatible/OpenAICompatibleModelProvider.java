package io.contextmesh.provider.adapter.openaicompatible;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.provider.application.GenerationStream;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelMessage;
import io.contextmesh.provider.application.ModelProvider;
import io.contextmesh.provider.application.ProviderDescriptor;
import io.contextmesh.provider.application.ProviderKind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "contextmesh.providers.openai-compatible", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OpenAICompatibleProviderProperties.class)
public final class OpenAICompatibleModelProvider implements ModelProvider {
    public static final String PROVIDER_ID = "openai-compatible";

    private final OpenAICompatibleProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAICompatibleModelProvider(OpenAICompatibleProviderProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(properties.connectionTimeout()).build());
    }

    OpenAICompatibleModelProvider(OpenAICompatibleProviderProperties properties, ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override public String providerId() { return PROVIDER_ID; }

    /**
     * The bean only exists when the endpoint is enabled and validly configured, so registration
     * itself reports readiness. Only the optional model default is published; the base URL, API key,
     * and default headers stay server-side.
     */
    @Override
    public ProviderDescriptor describe() {
        return new ProviderDescriptor(PROVIDER_ID, "OpenAI-compatible", ProviderKind.EXTERNAL,
                properties.defaultModel());
    }

    @Override
    public GenerationStream generate(ModelGenerationRequest request) {
        return sink -> stream(request, sink);
    }

    private void stream(ModelGenerationRequest generation, Consumer<GenerationEvent> sink) {
        HttpRequest request;
        try {
            var payload = new ChatCompletionRequest(generation.model(), generation.messages().stream()
                    .map(message -> new ChatMessage(role(message.role()), message.text())).toList(), true);
            var builder = HttpRequest.newBuilder(completionUri()).timeout(properties.readTimeout())
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            properties.defaultHeaders().forEach(builder::header);
            request = builder.build();
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProviderProtocolException("Could not create provider request", exception);
        }

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            checkStatus(response.statusCode(), response.body());
            sink.accept(new GenerationEvent.Started(PROVIDER_ID, generation.model()));
            parseEvents(response.body(), generation.model(), sink);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ProviderUnavailableException("Provider request timed out", exception);
        } catch (IOException exception) {
            throw new ProviderUnavailableException("Provider connection failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailableException("Provider request interrupted", exception);
        }
    }

    private void parseEvents(InputStream body, String model, Consumer<GenerationEvent> sink) throws IOException {
        var completed = false;
        try (body; var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            var data = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        completed = processData(data.toString(), model, sink);
                        data.setLength(0);
                        if (completed) break;
                    }
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) data.append('\n');
                    data.append(line.substring(5).stripLeading());
                }
            }
            if (!completed && !data.isEmpty()) completed = processData(data.toString(), model, sink);
        }
        if (!completed) throw new ProviderProtocolException("Provider stream ended without a completion signal");
    }

    private boolean processData(String data, String model, Consumer<GenerationEvent> sink) {
        if ("[DONE]".equals(data.strip())) {
            sink.accept(new GenerationEvent.Completed(PROVIDER_ID, model, null));
            return true;
        }
        final JsonNode chunk;
        try {
            chunk = objectMapper.readTree(data);
        } catch (IOException exception) {
            throw new ProviderProtocolException("Provider returned malformed streaming data", exception);
        }
        if (chunk == null || !chunk.isObject())
            throw new ProviderProtocolException("Provider returned malformed streaming data");
        var choices = chunk.path("choices");
        if (!choices.isArray()) return false;
        for (var choice : choices) {
            var content = choice.path("delta").path("content");
            if (content.isTextual() && !content.textValue().isEmpty())
                sink.accept(new GenerationEvent.TextDelta(content.textValue()));
        }
        return false;
    }

    private void checkStatus(int status, InputStream body) throws IOException {
        if (status >= 200 && status < 300) return;
        body.close();
        if (status == 401 || status == 403) throw new ProviderAuthenticationException();
        if (status == 429) throw new ProviderRateLimitException();
        if (status >= 500) throw new ProviderUnavailableException("Upstream provider is unavailable");
        throw new ProviderProtocolException("Provider rejected the request with HTTP status " + status);
    }

    private URI completionUri() {
        var base = properties.baseUrl().toString();
        return URI.create((base.endsWith("/") ? base : base + "/") + "chat/completions");
    }

    private String role(ModelMessage.Role role) { return role.name().toLowerCase(Locale.ROOT); }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages, boolean stream) {}
    private record ChatMessage(String role, String content) {}
}
