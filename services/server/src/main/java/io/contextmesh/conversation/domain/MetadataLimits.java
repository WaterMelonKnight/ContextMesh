package io.contextmesh.conversation.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

final class MetadataLimits {
    private static final int MAX_ENTRIES = 50, MAX_DEPTH = 4, MAX_TEXT = 16_384;
    private MetadataLimits() {}
    static Map<String, Object> copyAndValidate(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Counter counter = new Counter();
        @SuppressWarnings("unchecked") var result = (Map<String, Object>) copy(input, 1, counter);
        return result;
    }
    private static Object copy(Object value, int depth, Counter counter) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("metadata exceeds maximum depth 4");
        if (value == null || value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof BigDecimal || value instanceof BigInteger) return value;
        if (value instanceof String text) {
            counter.text += text.length();
            if (counter.text > MAX_TEXT) throw new IllegalArgumentException("metadata text exceeds 16384 characters");
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("metadata keys must be strings");
                if (++counter.entries > MAX_ENTRIES) throw new IllegalArgumentException("metadata exceeds 50 entries");
                result.put(key, copy(entry.getValue(), depth + 1, counter));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return list.stream().map(item -> copy(item, depth + 1, counter)).toList();
        throw new IllegalArgumentException("metadata contains unsupported value type");
    }
    private static final class Counter { int entries; int text; }
}
