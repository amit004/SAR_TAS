package com.sar.anaylze.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JSON and text helpers for LLM response parsing and previews.
 */
public final class CommonUtils {

    private CommonUtils() {
    }

    /**
     * Strips markdown fences and extracts the outermost JSON object substring.
     */
    public static String sanitizeJson(String content) {
        String sanitized = content.trim();
        if (sanitized.startsWith("```")) {
            sanitized = sanitized.replaceFirst("^```json\\s*", "")
                    .replaceFirst("^```\\s*", "")
                    .replaceFirst("```\\s*$", "")
                    .trim();
        }
        int start = sanitized.indexOf('{');
        int end = sanitized.lastIndexOf('}');
        if (start >= 0 && end > start) {
            sanitized = sanitized.substring(start, end + 1);
        }
        return sanitized;
    }

    public static String readText(JsonNode node, String key, String defaultValue) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? defaultValue : value.asText(defaultValue);
    }

    /**
     * Reads a JSON array of string-like values (text, numbers, or nested values as string).
     */
    public static List<String> readStringArray(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(value.size());
        for (JsonNode n : value) {
            if (n.isTextual()) {
                out.add(n.asText());
            } else if (n.isNumber()) {
                out.add(n.asText());
            } else if (!n.isNull()) {
                out.add(n.toString());
            }
        }
        return List.copyOf(out);
    }

    /**
     * Normalized, length-limited excerpt for {@code searchable_text} defaults and fallbacks.
     */
    public static String searchableTextPreview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    /**
     * Parses an int from {@code key}, clamped to [{@code min}, {@code max}]. Missing or invalid → {@code min}.
     */
    public static int readIntClamped(JsonNode root, String key, int min, int max) {
        JsonNode n = root.get(key);
        if (n == null || n.isNull()) {
            return min;
        }
        int v;
        if (n.isInt() || n.isLong()) {
            v = n.asInt();
        } else if (n.isNumber()) {
            v = (int) Math.round(n.asDouble());
        } else if (n.isTextual()) {
            try {
                v = Integer.parseInt(n.asText().trim());
            } catch (NumberFormatException ignored) {
                return min;
            }
        } else {
            return min;
        }
        return Math.max(min, Math.min(max, v));
    }

    public static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n… [truncated]";
    }
}
