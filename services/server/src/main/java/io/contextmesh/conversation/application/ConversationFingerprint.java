package io.contextmesh.conversation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.contextmesh.conversation.domain.NormalizedConversation;
import io.contextmesh.conversation.domain.TextContentPart;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class ConversationFingerprint {
    private final ObjectMapper objectMapper;

    ConversationFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String calculate(NormalizedConversation conversation) {
        var value = new LinkedHashMap<String, Object>();
        value.put("externalId", conversation.externalId());
        value.put("title", conversation.title());
        value.put("sourceType", conversation.sourceType().name());
        value.put("sourceProvider", conversation.sourceProvider());
        value.put("createdAt", string(conversation.createdAt()));
        value.put("updatedAt", string(conversation.updatedAt()));
        value.put("messages", conversation.messages().stream().map(message -> {
            var canonical = new LinkedHashMap<String, Object>();
            canonical.put("externalId", message.externalId());
            canonical.put("role", message.role().name());
            canonical.put("content", message.content().stream().map(part -> {
                var content = new LinkedHashMap<String, Object>();
                content.put("type", part.type().name());
                if (part instanceof TextContentPart text) content.put("text", text.text());
                return content;
            }).toList());
            canonical.put("createdAt", string(message.createdAt()));
            canonical.put("parentExternalId", message.parentExternalId());
            if (message.generation() == null) {
                canonical.put("generation", null);
            } else {
                var generation = new LinkedHashMap<String, Object>();
                generation.put("provider", message.generation().provider());
                generation.put("model", message.generation().model());
                canonical.put("generation", generation);
            }
            canonical.put("metadata", canonicalMap(message.metadata()));
            return canonical;
        }).toList());
        value.put("metadata", canonicalMap(conversation.metadata()));
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint normalized conversation", exception);
        }
    }

    private static String string(Object value) { return value == null ? null : value.toString(); }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, nested) -> sorted.put(String.valueOf(key), canonicalValue(nested)));
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(ConversationFingerprint::canonicalValue).toList();
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> canonicalMap(Map<String, Object> map) {
        return (Map<String, Object>) canonicalValue(map);
    }
}
