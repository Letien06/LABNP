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
import java.util.regex.Pattern;

/** HTTP services for Lab 4, running entirely on JDK 17. */
public final class LabHttpServer implements AutoCloseable {
    public static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final Map<String, String> MEDIA_TYPES = Map.of("pdf", "application/pdf", "mp3", "audio/mpeg", "mp4", "video/mp4");
    private static final Pattern SINGLE_BYTE_RANGE = Pattern.compile("bytes=([0-9]*)-([0-9]*)", Pattern.CASE_INSENSITIVE);
    private static final String HOME_PAGE = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>Lab4 Web</title></head>
            <body>
            <h1>Lab4 Web services</h1>
            <ul>
              <li><a href="/api/time">JSON time service</a></li>
              <li><a href="/api/echo?message=hello">JSON echo service</a></li>
              <li><a href="/download/sample.txt" download>Download sample</a></li>
            </ul>
            <h2>Media samples</h2>
            <ul>
              <li><a href="/files/sample.pdf">Open sample PDF</a> · <a href="/download/sample.pdf" download>Download PDF</a></li>
              <li><a href="/files/sample.mp3">Open sample MP3</a> · <a href="/download/sample.mp3" download>Download MP3</a></li>
              <li><a href="/files/sample.mp4">Open sample MP4</a> · <a href="/download/sample.mp4" download>Download MP4</a></li>
            </ul>
            <p>Use the client to send GET, HEAD and POST requests.</p>
            </body></html>
            """;
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
        boolean inline = path.startsWith("/files/");
        boolean known = Set.of("/", "/index.html", "/api/time", "/api/echo", "/api/upload").contains(path) || download || inline;
        if (!known) { json(x, 404, Map.of("error", "route not found")); return; }
        String allow = path.equals("/api/upload") ? "POST" : path.equals("/api/echo") ? "GET, HEAD, POST" : "GET, HEAD";
        if (!Arrays.asList(allow.split(", ")).contains(method)) { x.getResponseHeaders().set("Allow", allow); json(x, 405, Map.of("error", "method not allowed")); return; }
        Map<String, List<String>> query = query(x.getRequestURI().getRawQuery());
        if (path.equals("/") || path.equals("/index.html")) {
            send(x, 200, "text/html; charset=utf-8", HOME_PAGE.getBytes(StandardCharsets.UTF_8)); return;
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
        String name = path.substring((download ? "/download/" : "/files/").length());
        validateName(name);
        int extensionIndex = name.lastIndexOf('.');
        String extension = extensionIndex < 0 ? "" : name.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        String type = MEDIA_TYPES.get(extension);
        if (inline && (type == null || !name.equals("sample." + extension))) {
            json(x, 404, Map.of("error", "file not found"));
            return;
        }
        Path file = download ? uploadDirectory.resolve(name) : publicDirectory.resolve(name);
        if (download && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) file = publicDirectory.resolve(name);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { json(x, 404, Map.of("error", "file not found")); return; }
        if (type == null) type = Files.probeContentType(file);
        if (type == null) type = "application/octet-stream";
        String fallback = name.replaceAll("[^A-Za-z0-9._-]", "_");
        x.getResponseHeaders().set("Content-Disposition", (download ? "attachment" : "inline") + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        serveFile(x, file, type);
    }

    private static void serveFile(HttpExchange x, Path file, String type) throws IOException {
        try (var input = Files.newByteChannel(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = input.size();
            long start = 0;
            long length = size;
            int status = 200;
            boolean head = x.getRequestMethod().equals("HEAD");
            x.getResponseHeaders().set("Accept-Ranges", "bytes");
            String range = x.getRequestHeaders().getFirst("Range");
            if (!head && range != null && x.getRequestHeaders().getFirst("If-Range") == null) {
                var match = SINGLE_BYTE_RANGE.matcher(range.trim());
                if (match.matches() && (match.start(1) != match.end(1) || match.start(2) != match.end(2))) {
                    String first = match.group(1);
                    String last = match.group(2);
                    long end = last.isEmpty() || first.isEmpty() ? size - 1 : Math.min(parseRangeNumber(last), size - 1);
                    start = first.isEmpty() ? size - Math.min(parseRangeNumber(last), size) : parseRangeNumber(first);
                    if (start > end) {
                        x.getResponseHeaders().set("Content-Range", "bytes */" + size);
                        x.getResponseHeaders().remove("Content-Disposition");
                        headers(x, type, 0);
                        x.sendResponseHeaders(416, -1);
                        return;
                    }
                    status = 206;
                    length = end - start + 1;
                    x.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
                }
            }
            headers(x, type, length);
            x.sendResponseHeaders(status, head || length == 0 ? -1 : length);
            if (!head && length > 0) {
                input.position(start);
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                OutputStream body = x.getResponseBody();
                while (length > 0) {
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), length));
                    int count = input.read(buffer);
                    if (count < 0) break;
                    body.write(buffer.array(), 0, count);
                    length -= count;
                }
            }
        }
    }

    private static long parseRangeNumber(String value) {
        if (value.isEmpty()) return 0;
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) { return Long.MAX_VALUE; }
    }
    private static void validateName(String name) { if (name.isBlank() || name.equals(".") || name.equals("..") || name.length() > 180 || name.chars().anyMatch(c -> c < 32 || c == 127 || "/\\:<>\"|?*".indexOf(c) >= 0) || name.endsWith(".") || name.endsWith(" ")) throw new IllegalArgumentException("unsafe filename"); }
    private static Map<String, List<String>> query(String raw) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (raw != null && !raw.isEmpty()) for (String pair : raw.split("&")) { String[] pieces = pair.split("=", 2); String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8); values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(URLDecoder.decode(pieces.length == 2 ? pieces[1] : "", StandardCharsets.UTF_8)); }
        return values;
    }
    private static byte[] readBody(HttpExchange x) throws IOException {
        String length = x.getRequestHeaders().getFirst("Content-Length");
        long declared = length == null ? -1 : Long.parseLong(length);
        if (declared > MAX_BODY_BYTES) {
            x.getResponseHeaders().set("Connection", "close");
            // Consume small overruns so Windows does not reset the response while the client is still writing.
            if (declared <= MAX_BODY_BYTES + 64 * 1024) {
                x.getRequestBody().readNBytes((int) declared + 1);
            }
            throw new TooLarge();
        }
        byte[] bytes = x.getRequestBody().readNBytes(MAX_BODY_BYTES + 1); if (bytes.length > MAX_BODY_BYTES) throw new TooLarge(); return bytes;
    }
    private static void headers(HttpExchange x, String type, long length) { x.getResponseHeaders().set("Content-Type", type); x.getResponseHeaders().set("Content-Length", Long.toString(length)); x.getResponseHeaders().set("Cache-Control", "no-store"); }
    private static void json(HttpExchange x, int status, Object body) throws IOException { send(x, status, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8)); }
    private static void send(HttpExchange x, int status, String type, byte[] body) throws IOException { headers(x, type, body.length); boolean head = x.getRequestMethod().equals("HEAD"); x.sendResponseHeaders(status, head || body.length == 0 ? -1 : body.length); if (!head && body.length != 0) x.getResponseBody().write(body); }
    private static final class TooLarge extends IOException { }
}
