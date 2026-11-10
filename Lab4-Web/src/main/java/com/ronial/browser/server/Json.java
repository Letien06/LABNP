package com.ronial.browser.server;

import java.math.BigDecimal;
import java.util.*;

/** Small strict JSON codec for the lab's dependency-free HTTP service. */
final class Json {
    private final String source;
    private int position;
    private Json(String source) { this.source = source; }
    static Object parse(String source) {
        Json parser = new Json(source);
        Object value = parser.value(0);
        parser.space();
        if (parser.position != source.length()) throw new IllegalArgumentException("Trailing JSON data");
        return value;
    }
    private void space() { while (position < source.length() && " \r\n\t".indexOf(source.charAt(position)) >= 0) position++; }
    private boolean take(char c) { space(); if (position < source.length() && source.charAt(position) == c) { position++; return true; } return false; }
    private void require(char c) { if (!take(c)) throw new IllegalArgumentException("Invalid JSON"); }
    private Object value(int depth) {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting too deep");
        space();
        if (position == source.length()) throw new IllegalArgumentException("Missing JSON value");
        char c = source.charAt(position);
        if (c == '"') return string();
        if (take('{')) {
            Map<String, Object> result = new LinkedHashMap<>();
            if (take('}')) return result;
            do { space(); if (position == source.length() || source.charAt(position) != '"') throw new IllegalArgumentException("Invalid object key"); String key = string(); require(':'); result.put(key, value(depth + 1)); } while (take(','));
            require('}'); return result;
        }
        if (take('[')) { List<Object> result = new ArrayList<>(); if (take(']')) return result; do { result.add(value(depth + 1)); } while (take(',')); require(']'); return result; }
        for (String literal : List.of("true", "false", "null")) {
            if (source.startsWith(literal, position)) { position += literal.length(); return literal.equals("null") ? null : Boolean.valueOf(literal); }
        }
        int start = position;
        if (position < source.length() && source.charAt(position) == '-') position++;
        if (position < source.length() && source.charAt(position) == '0') position++;
        else { int digits = position; while (position < source.length() && digit(source.charAt(position))) position++; if (digits == position) throw new IllegalArgumentException("Invalid JSON number"); }
        if (position < source.length() && source.charAt(position) == '.') { position++; int digits = position; while (position < source.length() && digit(source.charAt(position))) position++; if (digits == position) throw new IllegalArgumentException("Invalid fraction"); }
        if (position < source.length() && "eE".indexOf(source.charAt(position)) >= 0) { position++; if (position < source.length() && "+-".indexOf(source.charAt(position)) >= 0) position++; int digits = position; while (position < source.length() && digit(source.charAt(position))) position++; if (digits == position) throw new IllegalArgumentException("Invalid exponent"); }
        return new BigDecimal(source.substring(start, position));
    }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private String string() {
        require('"'); StringBuilder out = new StringBuilder();
        while (position < source.length()) {
            char c = source.charAt(position++);
            if (c == '"') return out.toString();
            if (c < 32) throw new IllegalArgumentException("Control character in JSON string");
            if (c == '\\') {
                if (position == source.length()) throw new IllegalArgumentException("Incomplete escape");
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b'); case 'f' -> out.append('\f'); case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                    case 'u' -> { if (position + 4 > source.length()) throw new IllegalArgumentException("Invalid Unicode escape"); try { out.append((char) Integer.parseInt(source.substring(position, position + 4), 16)); } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid Unicode escape"); } position += 4; }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            } else out.append(c);
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }
    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) { StringJoiner out = new StringJoiner(",", "{", "}"); map.forEach((key, item) -> out.add(write(key.toString()) + ":" + write(item))); return out.toString(); }
        if (value instanceof Iterable<?> list) { StringJoiner out = new StringJoiner(",", "[", "]"); list.forEach(item -> out.add(write(item))); return out.toString(); }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) { switch (c) { case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t"); default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); } } }
        return out.append('"').toString();
    }
}
