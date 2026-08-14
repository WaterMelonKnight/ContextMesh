package io.contextmesh.provider.adapter.openaicompatible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.contextmesh.provider.application.GenerationEvent;
import io.contextmesh.provider.application.ModelGenerationRequest;
import io.contextmesh.provider.application.ModelMessage;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAICompatibleModelProviderTest {
    private HttpServer server;

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test
    void streamsTextIncrementallyAndSkipsNonContentChunks() throws Exception {
        var requestBody = new AtomicReference<String>();
        start(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-secret");
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            write(exchange, "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n");
            write(exchange, "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n");
            write(exchange, "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}\n\n");
            write(exchange, "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n");
            write(exchange, "data: [DONE]\n\n");
            exchange.close();
        });

        var events = consume(provider(), request());

        assertThat(events).containsExactly(
                new GenerationEvent.Started("openai-compatible", "test-model"),
                new GenerationEvent.TextDelta("Hello"),
                new GenerationEvent.TextDelta(" world"),
                new GenerationEvent.Completed("openai-compatible", "test-model", null));
        assertThat(requestBody.get()).contains("\"model\":\"test-model\"", "\"role\":\"system\"",
                "\"role\":\"user\"", "\"role\":\"assistant\"", "\"stream\":true");
    }

    @Test void malformedJsonIsAProtocolFailure() throws Exception {
        start(exchange -> respond(exchange, 200, "data: {broken}\n\n"));
        assertThatThrownBy(() -> consume(provider(), request()))
                .isInstanceOf(ProviderProtocolException.class)
                .hasMessage("Provider returned malformed streaming data");
    }

    @Test void streamWithoutDoneIsAProtocolFailure() throws Exception {
        start(exchange -> respond(exchange, 200,
                "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"));
        assertThatThrownBy(() -> consume(provider(), request()))
                .isInstanceOf(ProviderProtocolException.class)
                .hasMessage("Provider stream ended without a completion signal");
    }

    @Test void mapsAuthenticationStatusesWithoutLeakingSecret() throws Exception {
        for (int status : List.of(401, 403)) {
            start(exchange -> respond(exchange, status, "secret test-secret details"));
            assertThatThrownBy(() -> consume(provider(), request()))
                    .isInstanceOf(ProviderAuthenticationException.class)
                    .hasMessageNotContaining("test-secret");
            server.stop(0);
        }
    }

    @Test void mapsRateLimitStatus() throws Exception {
        start(exchange -> respond(exchange, 429, "test-secret"));
        assertThatThrownBy(() -> consume(provider(), request()))
                .isInstanceOf(ProviderRateLimitException.class).hasMessageNotContaining("test-secret");
    }

    @Test void mapsServerFailure() throws Exception {
        start(exchange -> respond(exchange, 500, "test-secret"));
        assertThatThrownBy(() -> consume(provider(), request()))
                .isInstanceOf(ProviderUnavailableException.class).hasMessageNotContaining("test-secret");
    }

    @Test void mapsConnectionFailureWithoutLeakingSecret() throws Exception {
        var unavailable = HttpServer.create(new InetSocketAddress(0), 0);
        var port = unavailable.getAddress().getPort();
        unavailable.stop(0);
        var properties = properties(URI.create("http://127.0.0.1:" + port + "/v1"));
        assertThatThrownBy(() -> consume(new OpenAICompatibleModelProvider(properties, new ObjectMapper()), request()))
                .isInstanceOf(ProviderUnavailableException.class).hasMessageNotContaining("test-secret");
    }

    @Test void mapsResponseReadFailure() throws Exception {
        start(exchange -> {
            exchange.sendResponseHeaders(200, 1_000);
            write(exchange, "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n");
            exchange.close();
        });
        assertThatThrownBy(() -> consume(provider(), request()))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessage("Provider connection failed");
    }

    @Test void enabledConfigurationRequiresCredentials() {
        assertThatThrownBy(() -> new OpenAICompatibleProviderProperties(true, URI.create("http://localhost/v1"),
                "", Duration.ofSeconds(1), Duration.ofSeconds(1), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("api-key");
    }

    private List<GenerationEvent> consume(OpenAICompatibleModelProvider provider, ModelGenerationRequest request) {
        var events = new ArrayList<GenerationEvent>();
        provider.generate(request).consume(events::add);
        return events;
    }

    private ModelGenerationRequest request() {
        return new ModelGenerationRequest(UUID.randomUUID(), UUID.randomUUID(), "test-model", List.of(
                new ModelMessage(ModelMessage.Role.SYSTEM, "Be concise"),
                new ModelMessage(ModelMessage.Role.USER, "Hello"),
                new ModelMessage(ModelMessage.Role.ASSISTANT, "Earlier")));
    }

    private OpenAICompatibleModelProvider provider() {
        return new OpenAICompatibleModelProvider(properties(baseUrl()), new ObjectMapper());
    }

    private OpenAICompatibleProviderProperties properties(URI baseUrl) {
        return new OpenAICompatibleProviderProperties(true, baseUrl, "test-secret",
                Duration.ofSeconds(2), Duration.ofSeconds(2), Map.of("X-Test", "value"));
    }

    private URI baseUrl() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"); }

    private void start(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try { handler.handle(exchange); } catch (Throwable failure) { exchange.close(); }
        });
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void write(HttpExchange exchange, String data) throws IOException {
        exchange.getResponseBody().write(data.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }

    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws Exception; }
}
