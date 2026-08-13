package io.contextmesh.ingestion.adapter.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.TextContentPart;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChatGptExportParserTest {
    private final ChatGptExportParser parser = new ChatGptExportParser(new ObjectMapper());

    @Test
    void normalizesRealisticBatchAndSelectsCurrentNodeAncestorBranch() {
        var conversations = parser.parse(fixture());
        assertThat(conversations).hasSize(2);
        var first = conversations.getFirst();
        assertThat(first.externalId()).isEqualTo("chatgpt-conversation-1");
        assertThat(first.sourceProvider()).isEqualTo("chatgpt");
        assertThat(first.createdAt()).isEqualTo(Instant.ofEpochSecond(1710000000, 250_000_000));
        assertThat(first.messages()).extracting(message -> message.externalId())
                .containsExactly("system-message", "user-message", "answer-message");
        assertThat(first.messages()).extracting(message -> message.role())
                .containsExactly(MessageRole.SYSTEM, MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(first.messages().get(1).content()).extracting(part -> ((TextContentPart) part).text())
                .containsExactly("你好，世界 🌍\n", "```java\nSystem.out.println(\"hi\");\n```");
        assertThat(first.messages().get(2).parentExternalId()).isEqualTo("user-1");
        assertThat(first.messages().get(2).generation().provider()).isEqualTo("openai");
        assertThat(first.messages().get(2).generation().model()).isEqualTo("gpt-4o");
        assertThat(first.metadata()).containsEntry("currentNode", "assistant-current")
                .containsEntry("mappingNodeCount", 6);
    }

    @Test
    void choosesDeepestThenLexicallyGreatestLeafWithoutCurrentNode() {
        var second = parser.parse(fixture()).get(1);
        assertThat(second.messages()).extracting(this::text).containsExactly("Question", "Deterministic branch Z");
    }

    @Test void rejectsMalformedParentWithLocation() {
        assertFailure(export(mapping("\"n\":{\"parent\":\"missing\",\"children\":[],\"message\":null}")),
                "conversation[0].mapping[\"n\"].parent: references missing mapping entry 'missing'");
    }

    @Test void rejectsCycleWithoutRecursion() {
        assertFailure(export(mapping("\"a\":{\"parent\":\"b\",\"children\":[\"b\"],\"message\":null},"
                        + "\"b\":{\"parent\":\"a\",\"children\":[\"a\"],\"message\":null}")),
                "cyclic parent relationship");
    }

    @Test void rejectsMissingCurrentNode() {
        assertFailure("[{\"id\":\"c\",\"current_node\":\"missing\",\"mapping\":{}}]",
                "conversation[0].current_node: references missing mapping entry 'missing'");
    }

    @Test void rejectsUnknownMeaningfulRole() {
        assertFailure(oneMessage("developer", "text", "[\"x\"]"),
                "conversation[0].mapping[\"n\"].message.author.role: unknown meaningful role 'developer'");
    }

    @Test void rejectsUnsupportedContentAndInvalidTimestamp() {
        assertFailure(oneMessage("user", "multimodal_text", "[\"x\"]"), "unsupported content type 'multimodal_text'");
        assertFailure(oneMessage("user", "text", "[{\"asset_pointer\":\"file://x\"}]"),
                "conversation[0].mapping[\"n\"].message.content.parts[0]: must be text");
        assertFailure(oneMessage("user", "text", "[\"x\"]").replace("\"author\"", "\"create_time\":\"today\",\"author\""),
                "conversation[0].mapping[\"n\"].message.create_time: must be Unix epoch seconds");
    }

    private void assertFailure(String json, String message) {
        assertThatThrownBy(() -> parser.parse(json)).isInstanceOf(ChatGptExportException.class).hasMessageContaining(message);
    }
    private String text(io.contextmesh.conversation.domain.NormalizedMessage message) {
        return ((TextContentPart) message.content().getFirst()).text();
    }
    private static String export(String mapping) { return "[{\"id\":\"c\",\"mapping\":" + mapping + "}]"; }
    private static String mapping(String nodes) { return "{" + nodes + "}"; }
    private static String oneMessage(String role, String type, String parts) {
        return export(mapping("\"n\":{\"parent\":null,\"children\":[],\"message\":{\"author\":{\"role\":\""
                + role + "\"},\"content\":{\"content_type\":\"" + type + "\",\"parts\":" + parts + "}}}"));
    }
    private String fixture() {
        try (var input = getClass().getResourceAsStream("/fixtures/chatgpt-export/conversations.json")) {
            if (input == null) throw new IllegalStateException("fixture missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }
}
