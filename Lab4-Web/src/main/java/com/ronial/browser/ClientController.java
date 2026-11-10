package com.ronial.browser;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.web.*;
import javafx.stage.DirectoryChooser;

public class ClientController implements Initializable {
    @FXML private WebView webView;
    @FXML private TextField textField;
    @FXML private Label statusLabel;
    @FXML private Label connectionLabel;
    private WebEngine engine;
    private double webZoom = 1;

    @Override public void initialize(URL location, ResourceBundle resources) {
        engine = webView.getEngine();
        engine.getLoadWorker().stateProperty().addListener((o, previous, state) -> {
            if (state == Worker.State.RUNNING) statusLabel.setText("Loading…");
            if (state == Worker.State.SUCCEEDED) {
                String url = engine.getLocation();
                if (url.startsWith("http")) {
                    textField.setText(url);
                    connectionLabel.setText(url.startsWith("https:") ? "HTTPS" : "HTTP • local / unencrypted");
                }
                statusLabel.setText("Ready");
            } else if (state == Worker.State.FAILED) {
                statusLabel.setText("Could not load this address");
            }
        });
        home();
    }

    public void home() {
        textField.setText(System.getProperty("lab4.home", "http://127.0.0.1:8080/"));
        loadPage();
    }

    private String address() {
        String value = textField.getText() == null ? "" : textField.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter a web address");
        if (!value.contains("://")) value = "http://" + value;
        try {
            URL url = URI.create(value).toURL();
            if (!(url.getProtocol().equals("http") || url.getProtocol().equals("https")))
                throw new IllegalArgumentException("Use an HTTP or HTTPS address");
            return url.toString();
        } catch (Exception ex) { throw new IllegalArgumentException("Invalid web address", ex); }
    }

    public void loadPage() {
        try { engine.load(address()); }
        catch (IllegalArgumentException ex) { statusLabel.setText(ex.getMessage()); }
    }

    private static String escaped(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static HttpURLConnection connection(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(10_000);
        c.setReadTimeout(15_000);
        return c;
    }

    private void request(String method) {
        final String url;
        try { url = address(); }
        catch (IllegalArgumentException ex) { statusLabel.setText(ex.getMessage()); return; }
        statusLabel.setText(method + " request…");
        CompletableFuture.supplyAsync(() -> {
            HttpURLConnection c = null;
            try {
                c = connection(url, method);
                if (method.equals("POST")) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    try (OutputStream out = c.getOutputStream()) {
                        out.write("message=Lab4+POST+request".getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = c.getResponseCode();
                StringBuilder result = new StringBuilder();
                if (method.equals("HEAD")) {
                    c.getHeaderFields().forEach((key, values) -> result.append(key == null ? "Status" : key)
                        .append(": ").append(String.join(", ", values)).append('\n'));
                } else {
                    InputStream body = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (body != null) try (body) { result.append(new String(body.readAllBytes(), StandardCharsets.UTF_8)); }
                }
                return "<html><head><meta charset='utf-8'></head><body style='font:16px Segoe UI;padding:28px;color:#203656'>"
                    + "<h2>" + method + " · HTTP " + code + "</h2><p>" + escaped(url)
                    + "</p><pre style='padding:24px;background:#eef3fa;white-space:pre-wrap'>" + escaped(result.toString()) + "</pre></body></html>";
            } catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
            finally { if (c != null) c.disconnect(); }
        }).whenComplete((html, error) -> Platform.runLater(() -> {
            if (error == null) engine.loadContent(html, "text/html");
            else statusLabel.setText(method + " failed: " + error.getCause().getMessage());
        }));
    }

    public void headRequest() { request("HEAD"); }
    public void postRequest() { request("POST"); }
    public void refreshPage() { loadPage(); }
    public void zoomIn() { webZoom = Math.min(3, webZoom + .25); webView.setZoom(webZoom); }
    public void zoomOut() { webZoom = Math.max(.25, webZoom - .25); webView.setZoom(webZoom); }
    public void displayHistory() { new HistoryWindow(engine.getHistory()).show(); }
    public void back() { WebHistory h = engine.getHistory(); if (h.getCurrentIndex() > 0) h.go(-1); }
    public void forward() { WebHistory h = engine.getHistory(); if (h.getCurrentIndex() + 1 < h.getEntries().size()) h.go(1); }
    public boolean isValidUrl(String value) {
        try { String p = URI.create(value).toURL().getProtocol(); return p.equals("http") || p.equals("https"); }
        catch (Exception ex) { return false; }
    }

    /** Shared by the download button and functional test; keeps network I/O off the UI thread. */
    public CompletableFuture<Path> downloadTo(Path directory) {
        final String url = address();
        statusLabel.setText("Downloading…");
        CompletableFuture<Path> future = CompletableFuture.supplyAsync(() -> {
            HttpURLConnection c = null;
            try {
                c = connection(url, "GET");
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                String path = URI.create(url).getPath();
                String name = Path.of(path).getFileName() == null ? "download.bin" : Path.of(path).getFileName().toString();
                Files.createDirectories(directory);
                Path destination = directory.resolve(name);
                try (InputStream input = c.getInputStream()) { Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING); }
                return destination;
            } catch (IOException ex) { throw new java.util.concurrent.CompletionException(ex); }
            finally { if (c != null) c.disconnect(); }
        });
        future.whenComplete((destination, error) -> Platform.runLater(() -> statusLabel.setText(error == null
            ? "Downloaded: " + destination : "Download failed: " + error.getCause().getMessage())));
        return future;
    }

    public void downloadVideo() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose download folder");
        File directory = chooser.showDialog(textField.getScene().getWindow());
        if (directory != null) {
            try { downloadTo(directory.toPath()); }
            catch (IllegalArgumentException ex) { statusLabel.setText(ex.getMessage()); }
        }
    }
}
