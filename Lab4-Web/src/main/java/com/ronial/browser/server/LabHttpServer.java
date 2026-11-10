package com.ronial.browser.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** HTTP services for Lab 4, running entirely on JDK 17. */
public final class LabHttpServer implements AutoCloseable {
    public static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final Path publicDirectory, uploadDirectory;
    public LabHttpServer(String host, int port, Path publicDirectory, Path uploadDirectory) throws IOException {
        this.publicDirectory = publicDirectory.toAbsolutePath().normalize();
        this.uploadDirectory = uploadDirectory.toAbsolutePath().normalize();
        Files.createDirectories(this.publicDirectory); Files.createDirectories(this.uploadDirectory);
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(executor); server.createContext("/", this::handle);
    }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    public URI baseUri() { String host = server.getAddress().getAddress().getHostAddress(); return URI.create("http://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port() + "/"); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try { route(exchange); }
            catch (TooLarge e) { json(exchange, 413, Map.of("error", "request body exceeds 2 MiB")); }
            catch (IllegalArgumentException | CharacterCodingException e) { json(exchange, 400, Map.of("error", "invalid request: " + e.getMessage())); }
            catch (IOException e) { json(exchange, 500, Map.of("error", "file or network operation failed")); }
        }
    }
    private void route(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath(), method = x.getRequestMethod();
        boolean download = path.startsWith("/download/");
        boolean known = Set.of("/", "/index.html", "/api/time", "/api/echo", "/api/upload").contains(path) || download;
        if (!known) { json(x, 404, Map.of("error", "route not found")); return; }
        String allow = path.equals("/api/upload") ? "POST" : path.equals("/api/echo") ? "GET, HEAD, POST" : "GET, HEAD";
        if (!Arrays.asList(allow.split(", ")).contains(method)) { x.getResponseHeaders().set("Allow", allow); json(x, 405, Map.of("error", "method not allowed")); return; }
        Map<String, List<String>> query = query(x.getRequestURI().getRawQuery());
        if (path.equals("/") || path.equals("/index.html")) {
            send(x, 200, "text/html; charset=utf-8", ("<!doctype html><html><head><meta charset='utf-8'><title>Lab4 Web</title></head><body><h1>Lab4 Web services</h1><ul><li><a href='/api/time'>JSON time service</a></li><li><a href='/api/echo?message=hello'>JSON echo service</a></li><li><a href='/download/sample.txt'>Download sample</a></li></ul><p>Use the client to send GET, HEAD and POST requests.</p></body></html>").getBytes(StandardCharsets.UTF_8)); return;
        }
        if (path.equals("/api/time")) { json(x, 200, Map.of("service", "time", "utc", Instant.now().toString())); return; }
        if (path.equals("/api/echo")) {
            Object payload; byte[] body = new byte[0];
            if (method.equals("POST")) {
                body = readBody(x);
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
                String type = x.getRequestHeaders().getFirst("Content-Type"); type = type == null ? "" : type.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
                payload = type.equals("application/json") || type.endsWith("+json") ? Json.parse(text) : type.equals("application/x-www-form-urlencoded") ? query(text) : text;
            } else payload = query.getOrDefault("message", List.of("")).get(0);
            Map<String, Object> response = new LinkedHashMap<>(); response.put("service", "echo"); response.put("method", method); response.put("payload", payload); response.put("bytes", body.length); response.put("query", query);
            Map<String, Object> headers = new TreeMap<>(); x.getRequestHeaders().forEach((key, values) -> headers.put(key.toLowerCase(Locale.ROOT), values)); response.put("headers", headers);
            json(x, 200, response); return;
        }
        if (path.equals("/api/upload")) {
            String name = query.getOrDefault("name", List.of("upload.bin")).get(0); validateName(name);
            byte[] bytes = readBody(x); Path destination = uploadDirectory.resolve(name);
            if (Files.isSymbolicLink(destination)) throw new IllegalArgumentException("symbolic links are not allowed");
            try (OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) { output.write(bytes); }
            String location = "/download/" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"); x.getResponseHeaders().set("Location", location);
            json(x, 201, Map.of("service", "upload", "name", name, "bytes", bytes.length, "download", location)); return;
        }
        String name = path.substring("/download/".length()); validateName(name);
        Path file = uploadDirectory.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) file = publicDirectory.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { json(x, 404, Map.of("error", "file not found")); return; }
        String type = Files.probeContentType(file); if (type == null) type = "application/octet-stream";
        String fallback = name.replaceAll("[^A-Za-z0-9._-]", "_");
        x.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        long length = Files.size(file); headers(x, type, length);
        x.sendResponseHeaders(200, method.equals("HEAD") || length == 0 ? -1 : length);
        if (!method.equals("HEAD") && length != 0) try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { input.transferTo(x.getResponseBody()); }
    }
    private static void validateName(String name) { if (name.isBlank() || name.equals(".") || name.equals("..") || name.length() > 180 || name.chars().anyMatch(c -> c < 32 || c == 127 || "/\\:<>\"|?*".indexOf(c) >= 0) || name.endsWith(".") || name.endsWith(" ")) throw new IllegalArgumentException("unsafe filename"); }
    private static Map<String, List<String>> query(String raw) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (raw != null && !raw.isEmpty()) for (String pair : raw.split("&")) { String[] pieces = pair.split("=", 2); String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8); values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(URLDecoder.decode(pieces.length == 2 ? pieces[1] : "", StandardCharsets.UTF_8)); }
        return values;
    }
    private static byte[] readBody(HttpExchange x) throws IOException {
        String length = x.getRequestHeaders().getFirst("Content-Length");
        if (length != null && Long.parseLong(length) > MAX_BODY_BYTES) throw new TooLarge();
        byte[] bytes = x.getRequestBody().readNBytes(MAX_BODY_BYTES + 1); if (bytes.length > MAX_BODY_BYTES) throw new TooLarge(); return bytes;
    }
    private static void headers(HttpExchange x, String type, long length) { x.getResponseHeaders().set("Content-Type", type); x.getResponseHeaders().set("Content-Length", Long.toString(length)); x.getResponseHeaders().set("Cache-Control", "no-store"); }
    private static void json(HttpExchange x, int status, Object body) throws IOException { send(x, status, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8)); }
    private static void send(HttpExchange x, int status, String type, byte[] body) throws IOException { headers(x, type, body.length); boolean head = x.getRequestMethod().equals("HEAD"); x.sendResponseHeaders(status, head || body.length == 0 ? -1 : body.length); if (!head && body.length != 0) x.getResponseBody().write(body); }
    private static final class TooLarge extends IOException { }
}
