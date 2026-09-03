package com.aipack.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Retire les secrets des métadonnées d'audit (PRD §15).
 */
final class MetadataSanitizer {

    static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_FRAGMENTS = Set.of(
            "password",
            "secret",
            "apikey",
            "accesstoken",
            "refreshtoken",
            "authorization",
            "clientsecret",
            "jwt");

    private MetadataSanitizer() {}

    static Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return sanitizeMap(metadata);
    }

    private static Map<String, Object> sanitizeMap(Map<?, ?> source) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            if (isSensitiveKey(key)) {
                clean.put(key, REDACTED);
            } else {
                clean.put(key, sanitizeValue(entry.getValue()));
            }
        }
        return clean;
    }

    private static Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(sanitizeValue(item));
            }
            return copy;
        }
        if (value instanceof String text && looksLikeJwt(text)) {
            return REDACTED;
        }
        return value;
    }

    static boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.isEmpty()) {
            return false;
        }
        for (String fragment : SENSITIVE_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeJwt(String value) {
        if (!value.startsWith("eyJ")) {
            return false;
        }
        int dots = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '.') {
                dots++;
            }
        }
        return dots == 2;
    }
}
