package com.ronial.browser.service;

import com.ronial.browser.model.HttpRequestData;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Resolves text entered in the browser's address bar. */
public final class AddressBar {
    private static final Pattern EXPLICIT_ADDRESS = Pattern.compile("(?i)^(https?:|[a-z][a-z0-9+.-]*:/)");
    private static final Pattern SCHEME = Pattern.compile("(?i)^[a-z][a-z0-9+.-]*:");
    private static final Pattern SPACE = Pattern.compile("(?U)\\s");

    private AddressBar() {}

    public static URI resolve(String input) {
        String text = input.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Enter a search term or web address");
        if (text.startsWith("//")) return HttpRequestData.parseAddress("https:" + text);
        if (EXPLICIT_ADDRESS.matcher(text).find()) return HttpRequestData.parseAddress(text);

        if (!SPACE.matcher(text).find()) {
            URI address = bareAddress(text);
            if (address != null) return address;
            if (SCHEME.matcher(text).find()) return HttpRequestData.parseAddress(text);
        }
        return search(text);
    }

    private static URI bareAddress(String text) {
        URI address;
        try {
            address = URI.create("http://" + text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String host = address.getHost();
        if (host == null || address.getRawUserInfo() != null) return null;

        boolean local = "localhost".equalsIgnoreCase(host) || host.startsWith("[") || host.matches("[0-9.]+");
        boolean hasPort = address.getPort() >= 0;
        if (!(local || hasPort || host.contains("."))) return null;
        HttpRequestData.validateUri(address);
        return local || hasPort ? address : URI.create("https://" + text);
    }

    private static URI search(String keywords) {
        URI endpoint = URI.create(System.getProperty("lab4.searchUrl", "https://www.google.com/search"));
        HttpRequestData.validateUri(endpoint);
        if (endpoint.getRawFragment() != null) throw new IllegalArgumentException("Search address cannot contain a fragment");
        String separator = endpoint.getRawQuery() == null ? "?" : "&";
        return URI.create(endpoint.toASCIIString() + separator + "q="
                + URLEncoder.encode(keywords, StandardCharsets.UTF_8));
    }
}
