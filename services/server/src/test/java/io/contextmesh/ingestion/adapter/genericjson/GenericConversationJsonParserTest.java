package io.contextmesh.ingestion.adapter.genericjson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.TextContentPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class GenericConversationJsonParserTest {
    private final GenericConversationJsonParser parser = new GenericConversationJsonParser(new ObjectMapper());

    @Test void parsesMinimalConversation() {
        var conversation = parse("minimal-valid.json");
        assertThat(conversation.messages()).hasSize(1);
        assertThat(((TextContentPart) conversation.messages().getFirst().content().getFirst()).text()).isEqualTo("Hello");
        assertThat(conversation.metadata()).isEmpty();
    }

    @Test void preservesOrderAndSourceMetadata() {
        var conversation = parse("multi-message.json");
        assertThat(conversation.externalId()).isEqualTo("c1");
        assertThat(conversation.sourceProvider()).isEqualTo("example");
        assertThat(conversation.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(conversation.messages()).extracting(m -> m.externalId()).containsExactly("m1", "m2");
        assertThat(conversation.metadata()).containsEntry("language", "en");
        assertThatThrownBy(() -> conversation.metadata().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void keepsGenerationOnEachAssistantMessage() {
        var one = parse("assistant-generation.json").messages().getFirst().generation();
        assertThat(one.provider()).isEqualTo("openai");
        assertThat(one.model()).isEqualTo("gpt-example");
        var messages = parse("multi-model.json").messages();
        assertThat(messages.get(0).generation().provider()).isEqualTo("openai");
        assertThat(messages.get(2).generation().provider()).isEqualTo("anthropic");
    }

    @Test void retainsParentUnicodeAndMultilineText() {
        assertThat(parse("parent-external-id.json").messages().get(1).parentExternalId()).isEqualTo("m1");
        assertThat(text(parse("unicode-chinese.json"))).isEqualTo("你好，世界 🌍");
        assertThat(text(parse("multiline-code-like.json"))).contains("\n```java\n");
    }

    @ParameterizedTest
    @CsvSource({
        "invalid-schema-version.json, '$.schemaVersion: unsupported version ''2''; supported version is ''1'''",
        "unknown-role.json, '$.conversation.messages[0].role: unknown value ''HUMAN'''",
        "empty-content.json, '$.conversation.messages[0].content: must contain at least one content part'",
        "invalid-timestamp.json, '$.conversation.messages[0].createdAt: must be an ISO-8601 instant'",
        "duplicate-message-ids.json, '$.conversation: duplicate message externalId: same'",
        "unsupported-content-type.json, '$.conversation.messages[0].content[0].type: unsupported content type ''IMAGE_REF''; v1 supports TEXT'"
    })
    void rejectsInvalidFixturesWithDeterministicPath(String fixture, String message) {
        assertThatThrownBy(() -> parse(fixture)).isInstanceOf(GenericConversationJsonException.class).hasMessage(message);
    }

    @Test void rejectsUnknownFieldsAndBoundedMetadata() {
        assertThatThrownBy(() -> parser.parse("{\"schemaVersion\":\"1\",\"extra\":true,\"conversation\":{}}"))
                .hasMessage("$.extra: unknown field");
        String entries = java.util.stream.IntStream.range(0, 51).mapToObj(i -> "\"k"+i+"\":"+i).collect(java.util.stream.Collectors.joining(","));
        assertThatThrownBy(() -> parser.parse("{\"schemaVersion\":\"1\",\"conversation\":{\"sourceType\":\"IMPORTED_CONVERSATION\",\"messages\":[],\"metadata\":{"+entries+"}}}"))
                .hasMessage("$.conversation: metadata exceeds 50 entries");
    }

    private String text(io.contextmesh.conversation.domain.NormalizedConversation c) { return ((TextContentPart)c.messages().getFirst().content().getFirst()).text(); }
    private io.contextmesh.conversation.domain.NormalizedConversation parse(String name) {
        try (var in = getClass().getResourceAsStream("/fixtures/generic-conversation/" + name)) {
            if (in == null) throw new IllegalArgumentException(name);
            return parser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
