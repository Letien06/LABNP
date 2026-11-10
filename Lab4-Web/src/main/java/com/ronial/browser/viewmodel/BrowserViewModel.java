package com.ronial.browser.viewmodel;

import com.ronial.browser.model.HttpRequestData;
import com.ronial.browser.model.HttpResponseData;
import com.ronial.browser.service.HttpService;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javafx.application.Platform;
import javafx.beans.property.*;

/** Owns asynchronous HTTP work and discards results superseded by navigation. */
public final class BrowserViewModel implements AutoCloseable {
    private final HttpService service;
    private final StringProperty status = new SimpleStringProperty("Ready");
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final ObjectProperty<HttpResponseData> response = new SimpleObjectProperty<>();
    private CompletableFuture<?> active;
    private long generation;
    private boolean closed;
    public BrowserViewModel() { this(new HttpService()); }
    public BrowserViewModel(HttpService service) { this.service = service; }
    public ReadOnlyStringProperty statusProperty() { return status; }
    public ReadOnlyBooleanProperty busyProperty() { return busy; }
    public ReadOnlyObjectProperty<HttpResponseData> responseProperty() { return response; }
    public void setStatus(String text) { status.set(text); }
    public void cancel() {
        generation++;
        if (active != null) active.cancel(true);
        active = null;
        busy.set(false);
        status.set("Request cancelled");
    }
    public void navigationStarted() { cancel(); status.set("Loading…"); }
    public CompletableFuture<HttpResponseData> send(HttpRequestData request) {
        cancel();
        long ticket = generation;
        busy.set(true);
        status.set(request.method() + " request…");
        CompletableFuture<HttpResponseData> future;
        try { future = service.send(request); }
        catch (RuntimeException ex) { busy.set(false); status.set(message(ex)); return CompletableFuture.failedFuture(ex); }
        active = future;
        future.whenComplete((result, error) -> Platform.runLater(() -> {
            if (closed || ticket != generation) return;
            active = null; busy.set(false);
            if (error == null) {
                response.set(result);
                status.set(result.method() + " · HTTP " + result.statusCode() + " · " + result.elapsedMillis() + " ms");
            } else status.set("Request failed: " + message(error));
        }));
        return future;
    }
    public CompletableFuture<Path> download(String address, Path directory) {
        final java.net.URI uri;
        try { uri = HttpRequestData.parseAddress(address); }
        catch (RuntimeException ex) { status.set(message(ex)); return CompletableFuture.failedFuture(ex); }
        cancel(); long ticket = generation;
        busy.set(true); status.set("Downloading…");
        CompletableFuture<Path> future;
        try {
            var transport = service.download(uri, directory);
            future = transport.thenApply(result -> result.path());
            future.whenComplete((path, failure) -> { if (futureCancelled(transport, failure)) transport.cancel(true); });
            active = transport;
        }
        catch (RuntimeException ex) { busy.set(false); status.set(message(ex)); return CompletableFuture.failedFuture(ex); }
        future.whenComplete((path, error) -> Platform.runLater(() -> {
            if (closed || ticket != generation) return;
            active = null; busy.set(false);
            status.set(error == null ? "Downloaded: " + path : "Download failed: " + message(error));
        }));
        return future;
    }
    private static boolean futureCancelled(CompletableFuture<?> transport, Throwable error) {
        return error instanceof java.util.concurrent.CancellationException && !transport.isDone();
    }
    public static String message(Throwable error) {
        while (error.getCause() != null && (error instanceof CompletionException || error instanceof java.util.concurrent.ExecutionException)) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    @Override public void close() { if (!closed) { closed = true; cancel(); service.close(); } }
}
