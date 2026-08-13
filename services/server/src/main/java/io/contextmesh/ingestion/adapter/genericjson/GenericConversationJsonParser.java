package io.contextmesh.ingestion.adapter.genericjson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Strict adapter for the public Generic Conversation JSON version 1 format. */
public final class GenericConversationJsonParser {
    public static final String SCHEMA_VERSION = "1";
    private static final int MAX_INPUT_BYTES = 5 * 1024 * 1024;
    private final ObjectMapper mapper;

    public GenericConversationJsonParser(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public NormalizedConversation parse(String json) {
        var conversations = parseAll(json);
        if (conversations.size() != 1) throw error("$.conversations", "must contain exactly one conversation for this operation");
        return conversations.getFirst();
    }

    public List<NormalizedConversation> parseAll(String json) {
        if (json == null) throw error("$", "input is required");
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)
            throw error("$", "input exceeds 5 MiB");
        final JsonNode root;
        try { root = mapper.readTree(json); }
        catch (JsonProcessingException e) { throw new GenericConversationJsonException("$: malformed JSON: " + e.getOriginalMessage(), e); }
        object(root, "$", Set.of("schemaVersion", "conversation", "conversations"));
        String version = requiredText(root, "schemaVersion", "$.schemaVersion");
        if (!SCHEMA_VERSION.equals(version)) throw error("$.schemaVersion", "unsupported version '" + version + "'; supported version is '1'");
        boolean single = root.has("conversation"), batch = root.has("conversations");
        if (single == batch) throw error("$", "exactly one of conversation or conversations is required");
        if (single) return List.of(conversation(required(root, "conversation", "$.conversation"), "$.conversation"));
        JsonNode nodes = required(root, "conversations", "$.conversations");
        if (!nodes.isArray()) throw error("$.conversations", "must be an array");
        var result = new ArrayList<NormalizedConversation>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) result.add(conversation(nodes.get(i), "$.conversations[" + i + "]"));
        return List.copyOf(result);
    }

    private NormalizedConversation conversation(JsonNode conversation, String path) {
        object(conversation, path, Set.of("externalId", "title", "sourceType", "sourceProvider", "createdAt", "updatedAt", "messages", "metadata"));
        try {
            var messagesNode = required(conversation, "messages", path + ".messages");
            if (!messagesNode.isArray()) throw error(path + ".messages", "must be an array");
            var messages = new ArrayList<NormalizedMessage>();
            for (int i = 0; i < messagesNode.size(); i++) messages.add(message(messagesNode.get(i), i, path));
            return new NormalizedConversation(optionalText(conversation, "externalId", path + ".externalId"),
                    optionalText(conversation, "title", path + ".title"),
                    enumValue(requiredText(conversation, "sourceType", path + ".sourceType"), ConversationSourceType.class, path + ".sourceType"),
                    optionalText(conversation, "sourceProvider", path + ".sourceProvider"),
                    instant(conversation, "createdAt", path + ".createdAt"), instant(conversation, "updatedAt", path + ".updatedAt"),
                    messages, metadata(conversation, "metadata", path + ".metadata"));
        } catch (GenericConversationJsonException e) { throw e; }
        catch (IllegalArgumentException e) { throw error(path, e.getMessage()); }
    }

    private NormalizedMessage message(JsonNode node, int index, String conversationPath) {
        String path = conversationPath + ".messages[" + index + "]";
        object(node, path, Set.of("externalId", "role", "createdAt", "parentExternalId", "content", "generation", "metadata"));
        JsonNode content = required(node, "content", path + ".content");
        if (!content.isArray()) throw error(path + ".content", "must be an array");
        if (content.isEmpty()) throw error(path + ".content", "must contain at least one content part");
        var parts = new ArrayList<MessageContentPart>();
        for (int i = 0; i < content.size(); i++) {
            String partPath = path + ".content[" + i + "]";
            JsonNode part = content.get(i);
            object(part, partPath, Set.of("type", "text"));
            String type = requiredText(part, "type", partPath + ".type");
            if (!"TEXT".equals(type)) throw error(partPath + ".type", "unsupported content type '" + type + "'; v1 supports TEXT");
            parts.add(new TextContentPart(requiredTextAllowWhitespace(part, "text", partPath + ".text")));
        }
        GenerationMetadata generation = generation(node.get("generation"), path + ".generation");
        try {
            return new NormalizedMessage(optionalText(node, "externalId", path + ".externalId"),
                    enumValue(requiredText(node, "role", path + ".role"), MessageRole.class, path + ".role"), parts,
                    instant(node, "createdAt", path + ".createdAt"), optionalText(node, "parentExternalId", path + ".parentExternalId"),
                    generation, metadata(node, "metadata", path + ".metadata"));
        } catch (GenericConversationJsonException e) { throw e; }
        catch (IllegalArgumentException e) { throw error(path, e.getMessage()); }
    }

    private GenerationMetadata generation(JsonNode node, String path) {
        if (node == null) return null;
        if (node.isNull()) throw error(path, "must not be null");
        object(node, path, Set.of("provider", "model"));
        return new GenerationMetadata(requiredText(node, "provider", path + ".provider"), requiredText(node, "model", path + ".model"));
    }
    private Map<String,Object> metadata(JsonNode parent, String field, String path) {
        JsonNode node = parent.get(field);
        if (node == null) return Map.of();
        if (node.isNull()) throw error(path, "must not be null");
        if (!node.isObject()) throw error(path, "must be an object");
        @SuppressWarnings("unchecked") Map<String,Object> value = mapper.convertValue(node, LinkedHashMap.class);
        return value;
    }
    private Instant instant(JsonNode parent, String field, String path) {
        String value = optionalText(parent, field, path);
        if (value == null) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException e) { throw error(path, "must be an ISO-8601 instant"); }
    }
    private static <E extends Enum<E>> E enumValue(String value, Class<E> type, String path) {
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException e) { throw error(path, "unknown value '" + value + "'"); }
    }
    private static JsonNode required(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) throw error(path, "is required");
        return value;
    }
    private static String requiredText(JsonNode parent, String field, String path) {
        String value = requiredTextAllowWhitespace(parent, field, path);
        if (value.isBlank()) throw error(path, "must not be blank");
        return value;
    }
    private static String requiredTextAllowWhitespace(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.isTextual()) throw error(path, "must be a string");
        if (value.textValue().isEmpty()) throw error(path, "must not be empty");
        return value.textValue();
    }
    private static String optionalText(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null) return null;
        if (value.isNull()) throw error(path, "must not be null");
        if (!value.isTextual()) throw error(path, "must be a string");
        if (value.textValue().isBlank()) throw error(path, "must not be blank");
        return value.textValue();
    }
    private static void object(JsonNode node, String path, Set<String> allowed) {
        if (node == null || !node.isObject()) throw error(path, "must be an object");
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw error(path + "." + field, "unknown field"); });
    }
    private static GenericConversationJsonException error(String path, String message) {
        return new GenericConversationJsonException(path + ": " + message);
    }
}
