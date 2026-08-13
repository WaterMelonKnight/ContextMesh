package io.contextmesh.ingestion.adapter.chatgpt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.ConversationSourceType;
import io.contextmesh.conversation.domain.GenerationMetadata;
import io.contextmesh.conversation.domain.MessageContentPart;
import io.contextmesh.conversation.domain.MessageRole;
import io.contextmesh.conversation.domain.NormalizedConversation;
import io.contextmesh.conversation.domain.NormalizedMessage;
import io.contextmesh.conversation.domain.TextContentPart;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Parser for the conversations.json file included in a user-requested ChatGPT data export. */
public final class ChatGptExportParser {
    private static final int MAX_INPUT_BYTES = 5 * 1024 * 1024;
    private static final int MAX_CONVERSATIONS = 1_000;
    private static final int MAX_NODES = 10_000;
    private static final int MAX_TEXT_CHARACTERS = 1_000_000;
    private static final Set<String> INTERNAL_CONTENT_TYPES = Set.of(
            "user_editable_context", "execution_output");
    private final ObjectMapper mapper;

    public ChatGptExportParser(ObjectMapper mapper) { this.mapper = Objects.requireNonNull(mapper); }

    public List<NormalizedConversation> parse(String json) {
        if (json == null) throw error("$", "input is required");
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES)
            throw error("$", "input exceeds 5 MiB");
        final JsonNode root;
        try { root = mapper.readTree(json); }
        catch (JsonProcessingException exception) {
            throw new ChatGptExportException("$: malformed JSON: " + exception.getOriginalMessage(), exception);
        }
        if (root == null || !root.isArray()) throw error("$", "must be the conversations.json top-level array");
        if (root.size() > MAX_CONVERSATIONS) throw error("$", "exceeds 1000 conversations");
        var result = new ArrayList<NormalizedConversation>(root.size());
        for (int index = 0; index < root.size(); index++) result.add(conversation(root.get(index), index));
        return List.copyOf(result);
    }

    private NormalizedConversation conversation(JsonNode source, int index) {
        String path = "conversation[" + index + "]";
        if (!source.isObject()) throw error(path, "must be an object");
        String id = requiredText(source, "id", path + ".id");
        JsonNode mapping = required(source, "mapping", path + ".mapping");
        if (!mapping.isObject()) throw error(path + ".mapping", "must be an object");
        if (mapping.size() > MAX_NODES) throw error(path + ".mapping", "exceeds 10000 nodes");

        var nodes = new LinkedHashMap<String, Node>();
        mapping.fields().forEachRemaining(entry -> nodes.put(entry.getKey(), node(entry.getKey(), entry.getValue(), path)));
        validateRelationships(nodes, path);
        String currentNode = optionalText(source, "current_node", path + ".current_node");
        String endpoint = currentNode == null ? fallbackEndpoint(nodes) : currentNode;
        if (endpoint != null && !nodes.containsKey(endpoint))
            throw error(path + ".current_node", "references missing mapping entry '" + endpoint + "'");
        List<Node> branch = endpoint == null ? List.of() : ancestorChain(endpoint, nodes, path);
        var messages = new ArrayList<NormalizedMessage>();
        String parentExternalId = null;
        for (Node node : branch) {
            if (node.message() == null) continue;
            NormalizedMessage message = message(node, parentExternalId, path);
            if (message != null) {
                messages.add(message);
                parentExternalId = message.externalId();
            }
        }

        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("sourceFormat", "chatgpt-official-export");
        if (currentNode != null) metadata.put("currentNode", currentNode);
        metadata.put("canonicalNode", endpoint);
        metadata.put("mappingNodeCount", nodes.size());
        try {
            return new NormalizedConversation(id, optionalText(source, "title", path + ".title"),
                    ConversationSourceType.IMPORTED_CONVERSATION, "chatgpt",
                    timestamp(source.get("create_time"), path + ".create_time"),
                    timestamp(source.get("update_time"), path + ".update_time"), messages, metadata);
        } catch (ChatGptExportException exception) { throw exception; }
        catch (IllegalArgumentException exception) { throw error(path, exception.getMessage()); }
    }

    private Node node(String key, JsonNode source, String conversationPath) {
        String path = conversationPath + ".mapping[\"" + key + "\"]";
        if (!source.isObject()) throw error(path, "must be an object");
        String embeddedId = optionalText(source, "id", path + ".id");
        if (embeddedId != null && !key.equals(embeddedId)) throw error(path + ".id", "must match mapping key");
        String parent = optionalText(source, "parent", path + ".parent");
        JsonNode childrenNode = source.get("children");
        var children = new ArrayList<String>();
        if (childrenNode != null && !childrenNode.isNull()) {
            if (!childrenNode.isArray()) throw error(path + ".children", "must be an array");
            for (int i = 0; i < childrenNode.size(); i++) {
                JsonNode child = childrenNode.get(i);
                if (!child.isTextual() || child.textValue().isBlank())
                    throw error(path + ".children[" + i + "]", "must be a non-blank string");
                children.add(child.textValue());
            }
        }
        JsonNode message = source.get("message");
        return new Node(key, parent, List.copyOf(children), message == null || message.isNull() ? null : message);
    }

    private void validateRelationships(Map<String, Node> nodes, String path) {
        for (Node node : nodes.values()) {
            if (node.parent() != null && !nodes.containsKey(node.parent()))
                throw error(path + ".mapping[\"" + node.id() + "\"].parent",
                        "references missing mapping entry '" + node.parent() + "'");
            for (String child : node.children()) {
                Node target = nodes.get(child);
                if (target == null) throw error(path + ".mapping[\"" + node.id() + "\"].children",
                        "references missing mapping entry '" + child + "'");
                if (!node.id().equals(target.parent())) throw error(path + ".mapping[\"" + node.id() + "\"].children",
                        "child '" + child + "' does not reference this node as parent");
            }
        }
        for (Node start : nodes.values()) ancestorChain(start.id(), nodes, path);
    }

    private List<Node> ancestorChain(String endpoint, Map<String, Node> nodes, String path) {
        var reversed = new ArrayList<Node>();
        var visited = new HashSet<String>();
        String id = endpoint;
        while (id != null) {
            if (!visited.add(id)) throw error(path + ".mapping[\"" + id + "\"].parent", "cyclic parent relationship");
            Node node = nodes.get(id);
            if (node == null) throw error(path + ".mapping", "broken parent chain at '" + id + "'");
            reversed.add(node);
            id = node.parent();
        }
        return reversed.reversed();
    }

    private String fallbackEndpoint(Map<String, Node> nodes) {
        if (nodes.isEmpty()) return null;
        var depths = new HashMap<String, Integer>();
        for (Node node : nodes.values()) {
            int depth = 0;
            for (String id = node.id(); id != null; id = nodes.get(id).parent()) depth++;
            depths.put(node.id(), depth);
        }
        return nodes.values().stream().filter(node -> node.children().isEmpty())
                .max(Comparator.comparingInt((Node node) -> depths.get(node.id()))
                        .thenComparing(Node::id)).orElseThrow().id();
    }

    private NormalizedMessage message(Node node, String parentExternalId, String conversationPath) {
        String path = conversationPath + ".mapping[\"" + node.id() + "\"].message";
        JsonNode source = node.message();
        if (!source.isObject()) throw error(path, "must be an object");
        JsonNode author = required(source, "author", path + ".author");
        if (!author.isObject()) throw error(path + ".author", "must be an object");
        MessageRole role = role(requiredText(author, "role", path + ".author.role"), path + ".author.role");
        JsonNode content = required(source, "content", path + ".content");
        if (!content.isObject()) throw error(path + ".content", "must be an object");
        String contentType = requiredText(content, "content_type", path + ".content.content_type");
        if (INTERNAL_CONTENT_TYPES.contains(contentType)) return null;
        if (!"text".equals(contentType))
            throw error(path + ".content.content_type", "unsupported content type '" + contentType + "'; only text is supported");
        JsonNode partsNode = required(content, "parts", path + ".content.parts");
        if (!partsNode.isArray() || partsNode.isEmpty()) throw error(path + ".content.parts", "must be a non-empty array");
        var parts = new ArrayList<MessageContentPart>();
        int totalCharacters = 0;
        for (int i = 0; i < partsNode.size(); i++) {
            JsonNode part = partsNode.get(i);
            if (!part.isTextual()) throw error(path + ".content.parts[" + i + "]", "must be text");
            totalCharacters += part.textValue().length();
            if (totalCharacters > MAX_TEXT_CHARACTERS) throw error(path + ".content.parts", "exceeds 1000000 characters");
            if (!part.textValue().isEmpty()) parts.add(new TextContentPart(part.textValue()));
        }
        if (parts.isEmpty()) {
            if (role == MessageRole.SYSTEM) return null;
            throw error(path + ".content.parts", "must contain non-empty text for " + role.name().toLowerCase() + " message");
        }
        String messageId = optionalText(source, "id", path + ".id");
        if (messageId == null) messageId = node.id();
        GenerationMetadata generation = generation(role, source.get("metadata"), path + ".metadata");
        try {
            return new NormalizedMessage(messageId, role, parts,
                    timestamp(source.get("create_time"), path + ".create_time"), parentExternalId, generation,
                    sourceMetadata(contentType, node));
        } catch (IllegalArgumentException exception) { throw error(path, exception.getMessage()); }
    }

    private static Map<String, Object> sourceMetadata(String contentType, Node node) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("sourceContentType", contentType);
        metadata.put("sourceMappingNodeId", node.id());
        if (node.parent() != null) metadata.put("sourceParentMappingNodeId", node.parent());
        return metadata;
    }

    private GenerationMetadata generation(MessageRole role, JsonNode metadata, String path) {
        if (role != MessageRole.ASSISTANT || metadata == null || metadata.isNull()) return null;
        if (!metadata.isObject()) throw error(path, "must be an object");
        String model = optionalText(metadata, "model_slug", path + ".model_slug");
        return model == null ? null : new GenerationMetadata("openai", model);
    }

    private static MessageRole role(String value, String path) {
        return switch (value) {
            case "system" -> MessageRole.SYSTEM;
            case "user" -> MessageRole.USER;
            case "assistant" -> MessageRole.ASSISTANT;
            case "tool" -> MessageRole.TOOL;
            default -> throw error(path, "unknown meaningful role '" + value + "'");
        };
    }

    private static Instant timestamp(JsonNode value, String path) {
        if (value == null || value.isNull()) return null;
        if (!value.isNumber()) throw error(path, "must be Unix epoch seconds");
        try {
            BigDecimal seconds = value.decimalValue();
            long whole = seconds.longValueExact();
            return Instant.ofEpochSecond(whole);
        } catch (ArithmeticException ignored) {
            try {
                BigDecimal seconds = value.decimalValue();
                long whole = seconds.longValue();
                int nanos = seconds.subtract(BigDecimal.valueOf(whole)).movePointRight(9).intValueExact();
                return Instant.ofEpochSecond(whole, nanos);
            } catch (ArithmeticException | DateTimeException exception) {
                throw error(path, "must be valid Unix epoch seconds");
            }
        } catch (DateTimeException exception) { throw error(path, "must be valid Unix epoch seconds"); }
    }

    private static JsonNode required(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) throw error(path, "is required");
        return value;
    }
    private static String requiredText(JsonNode parent, String field, String path) {
        String value = optionalText(parent, field, path);
        if (value == null) throw error(path, "is required");
        return value;
    }
    private static String optionalText(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw error(path, "must be a string");
        if (value.textValue().isBlank()) throw error(path, "must not be blank");
        return value.textValue();
    }
    private static ChatGptExportException error(String path, String message) {
        return new ChatGptExportException(path + ": " + message);
    }
    private record Node(String id, String parent, List<String> children, JsonNode message) {}
}
