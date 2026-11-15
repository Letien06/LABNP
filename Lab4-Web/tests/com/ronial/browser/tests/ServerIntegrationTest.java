package com.ronial.browser.tests;

import com.ronial.browser.server.LabHttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Real socket integration tests; any failed assertion terminates with a nonzero exit. */
public final class ServerIntegrationTest {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static URI base;
    private static HttpResponse<byte[]> request(String method, String path, byte[] body, String type) throws Exception {
        return request(method, path, body, type, null);
    }
    private static HttpResponse<byte[]> request(String method, String path, byte[] body, String type, String range) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).header("X-Lab-Test", "composer-header");
        if (type != null) request.header("Content-Type", type);
        if (range != null) request.header("Range", range);
        return CLIENT.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private static String text(HttpResponse<byte[]> response) { return new String(response.body(), StandardCharsets.UTF_8); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void status(HttpResponse<byte[]> response, int expected) { check(response.statusCode() == expected, "Expected " + expected + ", got " + response.statusCode() + ": " + text(response)); }
    public static void main(String[] args) throws Exception {
        Path runtime = Path.of("target", "test-runtime"); Files.createDirectories(runtime);
        Path root = Files.createTempDirectory(runtime, "server-");
        try {
            Path publicDir = Files.createDirectory(root.resolve("public")), uploads = Files.createDirectory(root.resolve("uploads"));
            byte[] sample = "Mẫu tải xuống — tiếng Việt\n".getBytes(StandardCharsets.UTF_8); Files.write(publicDir.resolve("sample.txt"), sample);
            Map<String, String> mediaTypes = Map.of("sample.pdf", "application/pdf", "sample.mp3", "audio/mpeg", "sample.mp4", "video/mp4");
            for (String name : mediaTypes.keySet()) Files.copy(Path.of("public", name), publicDir.resolve(name));
            try (LabHttpServer server = new LabHttpServer("127.0.0.1", 0, publicDir, uploads)) {
                server.start(); base = server.baseUri(); check(server.port() > 0, "Bound port");
                HttpResponse<byte[]> page = request("GET", "/", null, null); status(page, 200); check(text(page).contains("/api/time") && text(page).contains("/download/sample.txt"), "HTML service links");
                for (var media : mediaTypes.entrySet()) {
                    String name = media.getKey();
                    check(text(page).contains("/files/" + name) && text(page).contains("/download/" + name), "HTML media links: " + name);
                    checkMedia(publicDir, name, media.getValue());
                }
                HttpResponse<byte[]> head = request("HEAD", "/index.html", null, null); status(head, 200); check(head.body().length == 0, "HEAD body must be empty"); check(head.headers().firstValueAsLong("Content-Length").orElse(-1) == page.body().length, "HEAD HTML length");
                HttpResponse<byte[]> time = request("GET", "/api/time", null, null); status(time, 200); check(text(time).contains("\"utc\""), "JSON time");
                status(request("HEAD", "/api/time", null, null), 200);
                HttpResponse<byte[]> getEcho = request("GET", "/api/echo?message=" + URLEncoder.encode("Xin chào 🌍", StandardCharsets.UTF_8), null, null); status(getEcho, 200); check(text(getEcho).contains("Xin chào 🌍"), "Unicode query");
                byte[] json = "{\"message\":\"Tiếng Việt 🌍\",\"nested\":[1,true,null]}".getBytes(StandardCharsets.UTF_8);
                HttpResponse<byte[]> echo = request("POST", "/api/echo", json, "application/json; charset=utf-8"); status(echo, 200); check(text(echo).contains("Tiếng Việt 🌍") && text(echo).contains("\"bytes\":" + json.length) && text(echo).contains("\"x-lab-test\":[\"composer-header\"]"), "JSON echo and custom request header");
                status(request("POST", "/api/echo", "{\"bad\":}".getBytes(StandardCharsets.UTF_8), "application/json"), 400);
                status(request("POST", "/api/echo", "[1,]".getBytes(StandardCharsets.UTF_8), "application/json"), 400);
                HttpResponse<byte[]> form = request("POST", "/api/echo", "message=ch%C3%A0o&value=1&value=2".getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded"); status(form, 200); check(text(form).contains("chào") && text(form).contains("\"value\":[\"1\",\"2\"]"), "Form echo");
                HttpResponse<byte[]> plain = request("POST", "/api/echo", "Nội dung thường".getBytes(StandardCharsets.UTF_8), "text/plain"); status(plain, 200); check(text(plain).contains("Nội dung thường"), "Plain UTF-8 echo");
                byte[] binary = new byte[65537]; new Random(42).nextBytes(binary);
                HttpResponse<byte[]> uploaded = request("POST", "/api/upload?name=binary.bin", binary, "application/octet-stream"); status(uploaded, 201); check(text(uploaded).contains("/download/binary.bin"), "Upload download path");
                HttpResponse<byte[]> downloaded = request("GET", "/download/binary.bin", null, null); status(downloaded, 200);
                MessageDigest sha = MessageDigest.getInstance("SHA-256"); check(Arrays.equals(sha.digest(binary), sha.digest(downloaded.body())), "Binary SHA-256 roundtrip"); check(Arrays.equals(binary, Files.readAllBytes(uploads.resolve("binary.bin"))), "Exact persisted bytes");
                head = request("HEAD", "/download/binary.bin", null, null); status(head, 200); check(head.body().length == 0 && head.headers().firstValueAsLong("Content-Length").orElse(-1) == binary.length, "Binary HEAD length"); check(head.headers().firstValue("Content-Disposition").orElse("").contains("attachment"), "Download disposition");
                HttpResponse<byte[]> fixture = request("GET", "/download/sample.txt", null, null); status(fixture, 200); check(Arrays.equals(sample, fixture.body()), "Public fixture");
                status(request("POST", "/api/upload?name=..%2Foutside.txt", new byte[1], null), 400);
                status(request("GET", "/download/%2e%2e%2foutside.txt", null, null), 400);
                status(request("POST", "/api/upload?name=large.bin", new byte[LabHttpServer.MAX_BODY_BYTES + 1], null), 413); check(!Files.exists(uploads.resolve("large.bin")), "Oversized upload must not persist");
                status(request("GET", "/missing", null, null), 404); status(request("GET", "/download/missing.bin", null, null), 404);
                HttpResponse<byte[]> wrongMethod = request("PUT", "/api/echo", null, null); status(wrongMethod, 405); check(wrongMethod.headers().firstValue("Allow").orElse("").contains("POST"), "Allow header");
                status(request("GET", "/api/upload", null, null), 405);
                head = request("HEAD", "/missing", null, null); status(head, 404); check(head.body().length == 0 && head.headers().firstValueAsLong("Content-Length").orElse(0) > 0, "Error HEAD semantics");
            }
            System.out.println("PASS: ServerIntegrationTest (real HTTP, UTF-8, HEAD, binary SHA-256, media and ranges, limits, routing)");
        } finally {
            try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
    }

    private static void checkMedia(Path publicDir, String name, String mime) throws Exception {
        String path = "/files/" + name;
        byte[] expected = Files.readAllBytes(publicDir.resolve(name));
        HttpResponse<byte[]> inline = request("GET", path, null, null);
        status(inline, 200);
        check(Arrays.equals(inline.body(), expected), "Exact inline bytes: " + name);
        check(inline.headers().firstValue("Content-Type").orElse("").equals(mime), "Media MIME: " + name);
        check(inline.headers().firstValue("Content-Disposition").orElse("").startsWith("inline;"), "Inline disposition: " + name);
        check(inline.headers().firstValue("Accept-Ranges").orElse("").equals("bytes"), "Range support: " + name);

        HttpResponse<byte[]> attachment = request("GET", "/download/" + name, null, null);
        status(attachment, 200);
        check(Arrays.equals(attachment.body(), expected), "Exact attachment bytes: " + name);
        check(attachment.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment;"), "Attachment disposition: " + name);

        HttpResponse<byte[]> head = request("HEAD", path, null, null, "bytes=0-7");
        status(head, 200);
        check(head.body().length == 0 && head.headers().firstValueAsLong("Content-Length").orElse(-1) == expected.length, "Media HEAD length: " + name);

        HttpResponse<byte[]> segment = request("GET", path, null, null, "bytes=3-10");
        status(segment, 206);
        check(segment.headers().firstValue("Content-Range").orElse("").equals("bytes 3-10/" + expected.length), "Content-Range: " + name);
        check(Arrays.equals(segment.body(), Arrays.copyOfRange(expected, 3, 11)), "Range bytes: " + name);

        HttpResponse<byte[]> suffix = request("GET", path, null, null, "bytes=-12");
        status(suffix, 206);
        check(Arrays.equals(suffix.body(), Arrays.copyOfRange(expected, expected.length - 12, expected.length)), "Suffix range: " + name);

        HttpResponse<byte[]> tail = request("GET", path, null, null, "bytes=12-");
        status(tail, 206);
        check(Arrays.equals(tail.body(), Arrays.copyOfRange(expected, 12, expected.length)), "Open range: " + name);

        HttpResponse<byte[]> invalid = request("GET", path, null, null, "bytes=" + expected.length + "-");
        status(invalid, 416);
        check(invalid.headers().firstValue("Content-Range").orElse("").equals("bytes */" + expected.length), "Unsatisfiable Content-Range: " + name);
    }
}
