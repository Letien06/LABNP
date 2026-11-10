package com.ronial.browser.model;

import java.net.URI;
import java.util.*;

public record HttpResponseData(URI uri, String method, int statusCode,
                               Map<String, List<String>> headers, String body, long elapsedMillis) {
    public HttpResponseData {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        Objects.requireNonNull(headers).forEach((key, values) -> copy.put(key, List.copyOf(values)));
        headers = Collections.unmodifiableMap(copy);
        body = body == null ? "" : body;
    }
}
