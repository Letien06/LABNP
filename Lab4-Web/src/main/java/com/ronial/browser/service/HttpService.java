package com.ronial.browser.service;

import com.ronial.browser.model.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class HttpService implements AutoCloseable {
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "browser-http");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).executor(executor).build();
    private final Set<CompletableFuture<?>> outstanding = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public CompletableFuture<HttpResponseData> send(HttpRequestData data) {
        Objects.requireNonNull(data);
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("HTTP service is closed"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(data.uri()).timeout(Duration.ofSeconds(20));
        data.headers().forEach(builder::header);
        builder.method(data.method(), data.body().isEmpty() ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(data.body(), StandardCharsets.UTF_8));
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> transport = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        CompletableFuture<HttpResponseData> result = transport.thenApply(response -> new HttpResponseData(
                response.uri(), data.method(), response.statusCode(), response.headers().map(),
                data.method().equals("HEAD") ? "" : new String(response.body(), charset(response)),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
        result.whenComplete((value, error) -> { if (result.isCancelled()) transport.cancel(true); });
        return track(result);
    }

    public CompletableFuture<DownloadResult> download(URI uri, Path directory) {
        HttpRequestData.validateUri(uri);
        Objects.requireNonNull(directory, "Download directory is required");
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("HTTP service is closed"));
        DownloadFuture result = new DownloadFuture();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build();
        CompletableFuture<HttpResponse<InputStream>> transport = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                transport.cancel(true);
                result.closeStream();
            }
        });
        // ofInputStream completes at headers; keep the same deadline for the body.
        CompletableFuture.delayedExecutor(20, TimeUnit.SECONDS).execute(() -> {
            synchronized (result) {
                if (!result.isDone()) {
                    result.completeExceptionally(new HttpTimeoutException("Download timed out"));
                    transport.cancel(true);
                    result.closeStream();
                }
            }
        });
        transport.whenComplete((response, failure) -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            result.stream = response.body();
            if (result.isDone()) { result.closeStream(); return; }
            try {
                executor.execute(() -> save(response, directory, result));
            } catch (RejectedExecutionException error) {
                result.closeStream();
                result.completeExceptionally(error);
            }
        });
        return track(result);
    }

    private void save(HttpResponse<InputStream> response, Path directory, DownloadFuture result) {
        Path temporary = null;
        try (InputStream input = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Download failed: HTTP " + response.statusCode());
            if (result.isDone()) return;
            Path root = directory.toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(filename(response)).normalize();
            if (!target.getParent().equals(root)) throw new IOException("Invalid download filename");
            temporary = Files.createTempFile(root, ".browser-", ".part");
            long bytes;
            try (OutputStream output = Files.newOutputStream(temporary)) {
                bytes = input.transferTo(output);
            }
            synchronized (result) {
                if (result.isDone()) return;
                // A non-replacing move is the publication guard, including concurrent downloads.
                Path destination = target;
                for (int n = 1; ; n++) {
                    try {
                        Files.move(temporary, destination);
                        break;
                    } catch (FileAlreadyExistsException occupied) {
                        destination = root.resolve(n + "-" + target.getFileName());
                    }
                }
                temporary = null;
                result.complete(new DownloadResult(destination, bytes));
            }
        } catch (Exception error) {
            result.completeExceptionally(error);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            result.closeStream();
        }
    }

    private static Charset charset(HttpResponse<?> response) {
        String type = response.headers().firstValue("Content-Type").orElse("");
        Matcher matcher = Pattern.compile("(?i)(?:^|;)\\s*charset\\s*=\\s*[\"']?([^;\\s\"']+)").matcher(type);
        if (matcher.find()) try { return Charset.forName(matcher.group(1)); } catch (IllegalArgumentException ignored) { }
        return StandardCharsets.UTF_8;
    }

    private static String filename(HttpResponse<?> response) {
        String disposition = response.headers().firstValue("Content-Disposition").orElse("");
        Matcher extended = Pattern.compile("(?i)(?:^|;)\\s*filename\\*\\s*=\\s*UTF-8'[^']*'([^;]+)").matcher(disposition);
        String name = null;
        if (extended.find()) try {
            name = URLDecoder.decode(extended.group(1).trim().replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) { }
        if (name == null) {
            Matcher basic = Pattern.compile("(?i)(?:^|;)\\s*filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))").matcher(disposition);
            if (basic.find()) name = basic.group(1) != null ? basic.group(1) : basic.group(2).trim();
        }
        if (name == null || name.isBlank()) {
            String path = response.uri().getPath();
            name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("^[. ]+|[. ]+$", "");
        if (name.isBlank()) name = "download.bin";
        if (name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")) name = "_" + name;
        return name.length() > 180 ? name.substring(0, 180) : name;
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        outstanding.add(future);
        future.whenComplete((value, error) -> outstanding.remove(future));
        if (closed) future.cancel(true);
        return future;
    }

    @Override public void close() {
        closed = true;
        outstanding.forEach(future -> future.cancel(true));
        executor.shutdownNow();
    }

    private static final class DownloadFuture extends CompletableFuture<DownloadResult> {
        volatile InputStream stream;
        @Override public synchronized boolean cancel(boolean interrupt) { return super.cancel(interrupt); }
        void closeStream() {
            InputStream current = stream;
            if (current != null) try { current.close(); } catch (IOException ignored) { }
        }
    }
}
