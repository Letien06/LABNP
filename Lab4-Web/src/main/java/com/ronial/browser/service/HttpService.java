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
import java.util.function.Consumer;
import java.util.regex.*;

public final class HttpService implements AutoCloseable {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long DOWNLOAD_IDLE_NANOS = TimeUnit.SECONDS.toNanos(20);

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "browser-http");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService downloadWatchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "browser-download-timeout");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).executor(executor).build();
    private final Set<CompletableFuture<?>> outstanding = ConcurrentHashMap.newKeySet();
    private final Object downloadPublication = new Object();
    private volatile boolean closed;

    public CompletableFuture<HttpResponseData> send(HttpRequestData data) {
        Objects.requireNonNull(data);
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("HTTP service is closed"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(data.uri()).timeout(REQUEST_TIMEOUT);
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
        return download(uri, directory, progress -> { });
    }

    public CompletableFuture<DownloadResult> download(URI uri, Path directory, Consumer<DownloadProgress> onProgress) {
        HttpRequestData.validateUri(uri);
        Objects.requireNonNull(directory, "Download directory is required");
        Objects.requireNonNull(onProgress, "Download progress callback is required");
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("HTTP service is closed"));
        DownloadFuture result = new DownloadFuture();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
        CompletableFuture<HttpResponse<InputStream>> transport = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) transport.cancel(true);
            if (result.isCancelled() || error instanceof HttpTimeoutException) result.closeStream();
        });
        transport.whenComplete((response, failure) -> {
            if (failure != null) { result.completeExceptionally(failure); return; }
            result.stream = response.body();
            if (result.isDone()) { result.closeStream(); return; }
            try {
                executor.execute(() -> save(response, directory, result, onProgress));
            } catch (RejectedExecutionException error) {
                result.closeStream();
                result.completeExceptionally(error);
            }
        });
        return track(result);
    }

    private void save(HttpResponse<InputStream> response, Path directory, DownloadFuture result,
                      Consumer<DownloadProgress> onProgress) {
        Path temporary = null;
        Exception failure = null;
        ScheduledFuture<?> idleCheck = null;
        try (InputStream input = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Download failed: HTTP " + response.statusCode());
            if (result.isDone()) return;
            Path root = directory.toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(filename(response)).normalize();
            if (!target.getParent().equals(root)) throw new IOException("Invalid download filename");
            temporary = Files.createTempFile(root, ".browser-", ".part");
            OptionalLong total = response.headers().firstValueAsLong("Content-Length");
            long bytes = 0;
            long lastUpdate = System.nanoTime();
            long lastReportedBytes = 0;
            onProgress.accept(new DownloadProgress(0, total));
            try (OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                result.lastRead = System.nanoTime();
                idleCheck = downloadWatchdog.scheduleWithFixedDelay(result::checkIdle, 1, 1, TimeUnit.SECONDS);
                while (!result.isDone()) {
                    int count = input.read(buffer);
                    if (count == -1) break;
                    result.lastRead = System.nanoTime();
                    output.write(buffer, 0, count);
                    bytes += count;
                    long now = System.nanoTime();
                    if (now - lastUpdate >= TimeUnit.MILLISECONDS.toNanos(100)) {
                        onProgress.accept(new DownloadProgress(bytes, total));
                        lastUpdate = now;
                        lastReportedBytes = bytes;
                    }
                }
                idleCheck.cancel(false);
            }
            if (!result.isDone() && bytes != lastReportedBytes)
                onProgress.accept(new DownloadProgress(bytes, total));
            synchronized (result) {
                if (result.isDone()) return;
                // A non-replacing move is the publication guard, including concurrent downloads.
                Path destination = target;
                synchronized (downloadPublication) {
                    for (int n = 1; ; n++) {
                        try {
                            Files.move(temporary, destination);
                            break;
                        } catch (FileAlreadyExistsException occupied) {
                            destination = root.resolve(n + "-" + target.getFileName());
                        }
                    }
                }
                temporary = null;
                result.complete(new DownloadResult(destination, bytes));
            }
        } catch (Exception error) {
            failure = error;
        } finally {
            if (idleCheck != null) idleCheck.cancel(false);
            if (temporary != null) try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                if (failure == null) failure = cleanupError;
                else failure.addSuppressed(cleanupError);
            }
            result.closeStream();
            if (failure != null) result.completeExceptionally(failure);
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
        downloadWatchdog.shutdownNow();
        executor.shutdownNow();
    }

    private static final class DownloadFuture extends CompletableFuture<DownloadResult> {
        volatile InputStream stream;
        volatile long lastRead;

        @Override public synchronized boolean cancel(boolean interrupt) { return super.cancel(interrupt); }

        synchronized void checkIdle() {
            if (!isDone() && System.nanoTime() - lastRead >= DOWNLOAD_IDLE_NANOS) {
                completeExceptionally(new HttpTimeoutException("Download stopped receiving data"));
            }
        }

        void closeStream() {
            InputStream current = stream;
            if (current != null) try { current.close(); } catch (IOException ignored) { }
        }
    }
}
