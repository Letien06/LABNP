package com.ronial.browser.tests;

import com.ronial.browser.model.*;
import com.ronial.browser.service.HttpService;
import com.ronial.browser.server.LabHttpServer;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class HttpServiceIntegrationTest {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("target/test-runtime"));
        Path root = Files.createTempDirectory(Path.of("target/test-runtime"), "http-service-");
        Path publicDir = root.resolve("public"), downloads = root.resolve("downloads");
        try (LabHttpServer server = new LabHttpServer("127.0.0.1", 0, publicDir, root.resolve("uploads"));
             HttpService service = new HttpService()) {
            server.start();
            URI base = server.baseUri();
            check(HttpRequestData.parseAddress("localhost:" + server.port()).getHost().equals("localhost"), "bare host");
            invalid(() -> HttpRequestData.parseAddress("file:///tmp/a"));
            invalid(() -> HttpRequestData.parseAddress("http://user:pass@localhost"));
            invalid(() -> new HttpRequestData(base, "DELETE", Map.of(), ""));
            invalid(() -> new HttpRequestData(base, "GET", Map.of("Host", "bad"), ""));
            invalid(() -> new HttpRequestData(base, "GET", Map.of("X-Test", "bad\r\nInjected: true"), ""));
            Map<String, String> mutable = new HashMap<>(); mutable.put("X-Lab-Test", "custom-header");
            HttpRequestData post = new HttpRequestData(base.resolve("api/echo"), "POST", mutable, "Xin chào UTF-8");
            mutable.clear();
            HttpResponseData echo = service.send(post).get(10, TimeUnit.SECONDS);
            check(echo.statusCode() == 200 && echo.body().contains("Xin chào UTF-8"), "UTF-8 POST body");
            check(echo.body().contains("custom-header"), "custom request header");
            check(service.send(new HttpRequestData(base.resolve("api/time"), "HEAD", Map.of(), "")).get(10, TimeUnit.SECONDS).body().isEmpty(), "HEAD is empty");
            check(service.send(new HttpRequestData(base.resolve("missing"), "GET", Map.of(), "")).get(10, TimeUnit.SECONDS).statusCode() == 404, "HTTP errors are responses");
            byte[] binary = new byte[256 * 1024]; new Random(19).nextBytes(binary);
            String name = "tài liệu.bin";
            Files.write(publicDir.resolve(name), binary);
            URI downloadUri = base.resolve("download/" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
            DownloadResult downloaded = service.download(downloadUri, downloads).get(10, TimeUnit.SECONDS);
            check(downloaded.path().getFileName().toString().equals(name), "UTF-8 filename");
            check(downloaded.bytes() == binary.length && Arrays.equals(Files.readAllBytes(downloaded.path()), binary), "binary download integrity");
            Files.writeString(downloaded.path(), "existing-file");
            DownloadResult second = service.download(downloadUri, downloads).get(10, TimeUnit.SECONDS);
            check(!second.path().equals(downloaded.path()) && Files.readString(downloaded.path()).equals("existing-file"), "existing file preserved");
            List<CompletableFuture<DownloadResult>> simultaneous = new ArrayList<>();
            for (int i = 0; i < 8; i++) simultaneous.add(service.download(downloadUri, downloads));
            CompletableFuture.allOf(simultaneous.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            Set<Path> distinctPaths = new HashSet<>();
            for (CompletableFuture<DownloadResult> future : simultaneous) {
                DownloadResult concurrent = future.join();
                check(distinctPaths.add(concurrent.path()), "concurrent downloads have distinct paths");
                check(Arrays.equals(Files.readAllBytes(concurrent.path()), binary), "concurrent download contents preserved");
            }
            check(Files.readString(downloaded.path()).equals("existing-file"), "concurrent downloads preserve existing fixture");
            try { service.download(base.resolve("download/missing.bin"), downloads).get(10, TimeUnit.SECONDS); throw new AssertionError("404 download accepted"); }
            catch (ExecutionException expected) { check(expected.getCause() != null, "failure cause"); }
            check(Files.readString(downloaded.path()).equals("existing-file"), "failure preserved destination");
            noParts(downloads);
            cancellation(service, downloads);
        }
        System.out.println("HttpServiceIntegrationTest passed");
    }

    private static void cancellation(HttpService service, Path downloads) throws Exception {
        CountDownLatch bodyStarted = new CountDownLatch(1), release = new CountDownLatch(1);
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/slow.bin", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 1024 * 1024);
                exchange.getResponseBody().write(new byte[1024]); exchange.getResponseBody().flush();
                bodyStarted.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        });
        slow.start();
        try {
            CompletableFuture<DownloadResult> task = service.download(URI.create("http://127.0.0.1:" + slow.getAddress().getPort() + "/slow.bin"), downloads);
            check(bodyStarted.await(5, TimeUnit.SECONDS), "slow body started");
            task.cancel(true); release.countDown();
            check(task.isCancelled(), "download cancelled");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                try (var files = Files.list(downloads)) {
                    if (files.noneMatch(path -> path.toString().endsWith(".part"))) break;
                }
                Thread.sleep(20);
            }
            noParts(downloads);
            check(!Files.exists(downloads.resolve("slow.bin")), "cancelled download not published");
        } finally { release.countDown(); slow.stop(0); }
    }
    private static void noParts(Path directory) throws Exception {
        try (var files = Files.list(directory)) { check(files.noneMatch(path -> path.toString().endsWith(".part")), "temporary files cleaned"); }
    }
    private static void invalid(Runnable operation) { try { operation.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("invalid request accepted"); }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
