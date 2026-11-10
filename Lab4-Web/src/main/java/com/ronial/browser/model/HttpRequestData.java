package com.ronial.browser.model;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

public record HttpRequestData(URI uri, String method, Map<String, String> headers, String body) {
    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Set<String> RESTRICTED = Set.of("connection", "content-length", "expect", "host", "upgrade");

    public HttpRequestData {
        validateUri(uri);
        method = Objects.requireNonNull(method, "Method is required").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("GET", "HEAD", "POST").contains(method))
            throw new IllegalArgumentException("Supported methods: GET, HEAD, POST");
        Map<String, String> copy = new LinkedHashMap<>();
        Objects.requireNonNull(headers, "Headers are required").forEach((name, value) -> {
            if (name == null || !TOKEN.matcher(name).matches())
                throw new IllegalArgumentException("Invalid HTTP header name: " + name);
            if (RESTRICTED.contains(name.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Header is managed by the HTTP client: " + name);
            if (value == null || value.chars().anyMatch(c -> (c < 32 && c != 9) || c == 127 || c > 255))
                throw new IllegalArgumentException("Invalid HTTP header value for " + name);
            if (copy.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name)))
                throw new IllegalArgumentException("Duplicate HTTP header: " + name);
            copy.put(name, value);
        });
        headers = Collections.unmodifiableMap(copy);
        body = body == null ? "" : body;
    }

    public static URI parseAddress(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Enter an HTTP or HTTPS address");
        String address = value.trim();
        if (!address.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            if (address.matches("(?i)^[a-z][a-z0-9+.-]*:.*") && !address.matches("^[^/:]+:[0-9]+(?:[/?#].*)?$"))
                throw new IllegalArgumentException("Only HTTP and HTTPS addresses are supported");
            address = "http://" + address;
        }
        try {
            URI uri = URI.create(address);
            validateUri(uri);
            return uri;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid HTTP(S) address: " + error.getMessage(), error);
        }
    }

    public static void validateUri(URI uri) {
        if (uri == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null
                || uri.getPort() > 65535)
            throw new IllegalArgumentException("Address must have an HTTP(S) scheme and host, without user information");
    }
}
