package com.ronial.browser.tests;

import com.ronial.browser.ClientController;
import com.ronial.browser.server.LabHttpServer;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javafx.application.*;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.web.WebView;
import javafx.stage.*;

/** Real JavaFX controls against real Java HTTP servers. No Python or mocks. */
public final class BrowserFunctionalTest extends Application {
    private static volatile Throwable failure;
    private Stage stage;
    private Parent root;
    private ClientController controller;
    private WebView browser;
    private TextField address;
    private LabHttpServer server;
    private String base;
    private Path runtime;
    private int checks;

    @Override public void start(Stage primaryStage) throws Exception {
        Platform.setImplicitExit(false);
        Files.createDirectories(Path.of("target/test-runtime"));
        runtime = Files.createTempDirectory(Path.of("target/test-runtime"), "browser-ui-");
        server = new LabHttpServer("127.0.0.1", 0, runtime.resolve("public"), runtime.resolve("uploads"));
        for (String name : List.of("sample.txt", "sample.pdf", "sample.mp3", "sample.mp4")) {
            Files.copy(Path.of("public", name), runtime.resolve("public").resolve(name));
        }
        server.start();
        base = server.baseUri().toString().replaceAll("/$", "");
        System.setProperty("lab4.home", base + "/");
        System.setProperty("lab4.history", runtime.resolve("history.tsv").toString());
        System.setProperty("lab4.download.dir", runtime.resolve("link-downloads").toString());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        root = loader.load(); controller = loader.getController();
        browser = (WebView) root.lookup("#webView");
        address = (TextField) root.lookup("#textField");
        stage = primaryStage; stage.setScene(new Scene(root, 1200, 800));
        stage.setTitle("Lab4 — Java browser verification"); stage.show();
        Thread worker = new Thread(() -> {
            try { runChecks(); }
            catch (Throwable error) { failure = error; error.printStackTrace(); }
            finally {
                try { fx(() -> { controller.close(); for (Window window : List.copyOf(Window.getWindows())) window.hide(); return null; }); }
                catch (Exception error) { if (failure == null) failure = error; }
                server.close(); Platform.exit();
            }
        }, "browser-functional-tests");
        worker.setDaemon(true); worker.start();
    }

    private void runChecks() throws Exception {
        until(() -> pageText().contains("Lab4 Web services"));
        check(fx(() -> pageText().contains("JSON time service")), "GET renders Java server HTML");
        navigate("/index.html");
        fx(() -> { controller.back(); return null; });
        until(() -> browser.getEngine().getLocation().equals(base + "/"));
        fx(() -> { controller.forward(); return null; });
        until(() -> browser.getEngine().getLocation().equals(base + "/index.html"));
        check(true, "Back and forward preserve browser history");
        awaitPage(browser, base + "/index.html");
        fx(() -> { controller.displayHistory(); return null; });
        check(fx(() -> Window.getWindows().stream().filter(Window::isShowing).count()) == 2, "History window opens");
        fx(() -> {
            ListView<?> visits = (ListView<?>) find("historyList");
            int index = -1;
            for (int i = 0; i < visits.getItems().size(); i++) {
                var entry = (com.ronial.browser.service.BrowsingHistory.Entry) visits.getItems().get(i);
                if (entry.url().equals(base + "/")) { index = i; break; }
            }
            if (index < 0) throw new AssertionError("Expected a homepage visit");
            visits.getSelectionModel().select(index);
            ((Button) find("openHistoryEntry")).fire();
            return null;
        });
        awaitPage(browser, base + "/");
        check(fx(() -> find("historyList") == null), "History opens the selected URL");

        fx(() -> { address.setText(base + "/api/time"); controller.headRequest(); return null; });
        until(() -> responseSummary().contains("HEAD") && responseSummary().contains("200"));
        check(fx(() -> responseHeaders().toLowerCase().contains("content-length") && responseBody().isEmpty()), "HEAD shows real headers and empty body in native controls");

        fx(() -> { address.setText(base + "/api/echo"); controller.postRequest(); return null; });
        until(() -> find("requestBody") != null);
        fx(() -> {
            ((TextArea) find("requestHeaders")).setText("Invalid header without colon");
            ((TextArea) find("requestBody")).setText("draft retained after validation");
            ((Button) find("sendRequest")).fire(); return null;
        });
        check(fx(() -> !((Label) find("requestValidation")).getText().isBlank()
                && ((TextArea) find("requestBody")).getText().equals("draft retained after validation")), "Invalid headers show validation and preserve draft");

        compose(base + "/api/echo", "Content-Type: application/json; charset=UTF-8\nX-Lab-Test: gui-custom-header",
                "{\"message\":\"Xin chào giảng viên\",\"count\":42}");
        until(() -> responseSummary().contains("POST") && responseBody().contains("Xin chào giảng viên"));
        check(fx(() -> responseBody().contains("gui-custom-header") && responseBody().contains("42")), "POST sends edited JSON body and custom header over HTTP");
        check(fx(() -> root.lookup("#responseBody") instanceof TextArea), "Response uses native text controls");
        snapshot(stage, "browser-response.png");
        fx(() -> { snapshotOnFx(find("requestBody").getScene().getWindow(), "request-composer.png"); return null; });

        compose(base + "/api/echo", "Content-Type: application/x-www-form-urlencoded; charset=UTF-8", "message=custom+form&count=7");
        until(() -> responseBody().contains("custom form"));
        check(fx(() -> responseSummary().contains("200")), "POST sends user-authored form data");
        compose(base + "/api/echo", "Content-Type: text/plain; charset=UTF-8", "arbitrary plain text — thứ hai");
        until(() -> responseBody().contains("arbitrary plain text"));
        check(fx(() -> responseBody().contains("thứ hai")), "POST sends Unicode plain text");
        compose(base + "/api/echo", "Content-Type: application/json", "{malformed json");
        until(() -> responseSummary().contains("400"));
        check(fx(() -> !((Button) find("sendRequest")).isDisabled()), "HTTP errors are visible and composer remains usable");
        compose(base + "/api/echo", "Content-Type: text/plain", "recovered-after-error");
        until(() -> responseBody().contains("recovered-after-error"));
        check(fx(() -> responseSummary().contains("200")), "Composer recovers after failed request");

        staleAndCancellation();
        fx(() -> { for (Window window : List.copyOf(Window.getWindows())) if (window != stage) window.hide(); return null; });
        fx(() -> { address.setText(base + "/download/sample.txt"); return null; });
        CompletableFuture<Path> task = fx(() -> controller.downloadTo(runtime.resolve("client-downloads")));
        Path file = task.get(15, TimeUnit.SECONDS);
        until(() -> !controller.getViewModel().busyProperty().get());
        check(Files.mismatch(file, Path.of("public/sample.txt")) == -1, "Browser download saves identical bytes");
        fx(() -> { controller.zoomIn(); return null; });
        check(fx(() -> browser.getZoom()) == 1.25, "Zoom in");
        fx(() -> { controller.zoomOut(); return null; });
        check(fx(() -> browser.getZoom()) == 1, "Zoom out");
        browserNavigation();
        linkDownloads();
        System.out.println("BrowserFunctionalTest: PASS (" + checks + " checks)");
    }

    private void staleAndCancellation() throws Exception {
        CountDownLatch arrived = new CountDownLatch(1), release = new CountDownLatch(1);
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/slow", exchange -> {
            try (exchange) {
                arrived.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                byte[] bytes = "late-response-must-not-replace-page".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes);
            }
        });
        slow.start();
        try {
            compose("http://127.0.0.1:" + slow.getAddress().getPort() + "/slow", "Content-Type: text/plain", "slow body");
            check(arrived.await(5, TimeUnit.SECONDS), "Slow request reaches server");
            check(fx(() -> controller.getViewModel().busyProperty().get() && ((Button) find("sendRequest")).isDisabled()), "UI remains responsive and prevents duplicate submit");
            fx(() -> { ((Button) root.lookup("#cancelRequest")).fire(); return null; });
            check(fx(() -> !controller.getViewModel().busyProperty().get()), "Cancel clears active request");
            navigate("/"); release.countDown();
            Thread.sleep(250);
            check(fx(() -> ((TabPane) root.lookup("#browserTabs")).getSelectionModel().getSelectedItem().getContent() == browser
                    && pageText().contains("Lab4 Web services") && !controller.getViewModel().statusProperty().get().contains("failed")), "Late completion cannot replace a newer page or status");
        } finally { release.countDown(); slow.stop(0); }
    }

    private void browserNavigation() throws Exception {
        navigate("/index.html");
        CountDownLatch reloaded = new CountDownLatch(1);
        fx(() -> {
            browser.getEngine().getLoadWorker().stateProperty().addListener((obs, before, state) -> {
                if (state == Worker.State.SUCCEEDED) reloaded.countDown();
            });
            address.setText(base + "/api/time"); // Deliberately unsubmitted address.
            ((Button) root.lookup("#reloadButton")).fire();
            return null;
        });
        check(reloaded.await(6, TimeUnit.SECONDS)
                && fx(() -> browser.getEngine().getLocation().equals(base + "/index.html")), "Reload uses the committed page");

        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/page", exchange -> {
            started.countDown();
            try (exchange) {
                if (release.await(8, TimeUnit.SECONDS)) exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        });
        slow.start();
        try {
            fx(() -> { address.setText("http://127.0.0.1:" + slow.getAddress().getPort() + "/page"); controller.loadPage(); return null; });
            check(started.await(5, TimeUnit.SECONDS), "Slow page reaches the real server");
            until(() -> !((Button) root.lookup("#stopPageButton")).isDisabled());
            fx(() -> { ((Button) root.lookup("#stopPageButton")).fire(); return null; });
            until(() -> !browser.getEngine().getLoadWorker().isRunning());
            check(fx(() -> browser.getEngine().getLoadWorker().getState() == Worker.State.CANCELLED
                    && ((Button) root.lookup("#stopPageButton")).isDisabled()), "Stop cancels an ordinary page load");
        } finally { release.countDown(); slow.stop(0); }

        navigate("/index.html");
        TabPane tabs = fx(() -> (TabPane) root.lookup("#browserTabs"));
        Tab first = fx(() -> tabs.getSelectionModel().getSelectedItem());
        int size = fx(() -> browser.getEngine().getHistory().getEntries().size());
        WebView second = fx(() -> {
            ((Button) root.lookup("#newTabButton")).fire();
            return (WebView) tabs.getSelectionModel().getSelectedItem().getContent();
        });
        awaitPage(second, base + "/");
        fx(() -> { address.setText(base + "/api/time"); controller.loadPage(); return null; });
        awaitPage(second, base + "/api/time");
        check(fx(() -> second != browser && browser.getEngine().getLocation().equals(base + "/index.html")
                && browser.getEngine().getHistory().getEntries().size() == size), "New tab has an independent address and history");
        fx(() -> { controller.back(); return null; });
        awaitPage(second, base + "/");
        check(fx(() -> browser.getEngine().getLocation().equals(base + "/index.html")), "Back affects the selected tab only");
        fx(() -> {
            root.getScene().getAccelerators().get(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN)).run();
            return null;
        });
        check(fx(() -> tabs.getTabs().size() == 2 && tabs.getSelectionModel().getSelectedItem() == first), "Closing a tab restores the remaining tab");
    }

    private void linkDownloads() throws Exception {
        navigate("/");
        check(fx(() -> pageText().contains("Download PDF") && pageText().contains("Download MP3")
                && pageText().contains("Download MP4")), "Homepage exposes all three demo media files");
        for (String name : List.of("sample.pdf", "sample.mp3", "sample.mp4")) {
            fx(() -> {
                clickLink("/download/" + name);
                return null;
            });
            Path saved = runtime.resolve("link-downloads").resolve(name);
            until(() -> Files.exists(saved));
            check(Files.mismatch(saved, Path.of("public", name)) == -1, "Download link saves " + name + " byte for byte");
        }
        check(fx(() -> find("downloadsList") != null && browser.getEngine().getLocation().equals(base + "/")),
                "Downloads list opens while the current page stays in place");
        snapshot(stage, "browser-media.png");
    }

    private void clickLink(String href) {
        String rect = "(function(){ var a = document.querySelector('a[href=\"" + href
                + "\"]'); a.scrollIntoView({block:'nearest'}); var r = a.getBoundingClientRect();"
                + "return [r.left+r.width/2,r.top+r.height/2].join(','); })()";
        String[] point = browser.getEngine().executeScript(rect).toString().split(",");
        double x = Double.parseDouble(point[0]), y = Double.parseDouble(point[1]);
        var scene = browser.localToScene(x, y);
        var screen = browser.localToScreen(x, y);
        for (var type : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
            browser.fireEvent(new MouseEvent(type, scene.getX(), scene.getY(), screen.getX(), screen.getY(),
                    MouseButton.PRIMARY, 1, false, false, false, false, type == MouseEvent.MOUSE_PRESSED,
                    false, false, false, false, true, new PickResult(browser, scene.getX(), scene.getY())));
        }
    }

    private void awaitPage(WebView page, String url) throws Exception {
        until(() -> page.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED && page.getEngine().getLocation().equals(url));
    }

    private void compose(String url, String headers, String body) throws Exception {
        until(() -> !controller.getViewModel().busyProperty().get());
        fx(() -> {
            ((TextField) find("requestUrl")).setText(url);
            ((TextArea) find("requestHeaders")).setText(headers);
            ((TextArea) find("requestBody")).setText(body);
            ((Button) find("sendRequest")).fire(); return null;
        });
    }
    private Node find(String id) {
        for (Window window : Window.getWindows()) if (window.isShowing() && window.getScene() != null) {
            Node node = window.getScene().lookup("#" + id); if (node != null) return node;
        }
        return null;
    }
    private String responseSummary() { Node n = root.lookup("#responseSummary"); return n == null ? "" : ((Label) n).getText(); }
    private String responseHeaders() { Node n = root.lookup("#responseHeaders"); return n == null ? "" : ((TextArea) n).getText(); }
    private String responseBody() { Node n = root.lookup("#responseBody"); return n == null ? "" : ((TextArea) n).getText(); }
    private String pageText() { return String.valueOf(browser.getEngine().executeScript("document.body ? document.body.innerText : ''")); }
    private void navigate(String path) throws Exception {
        fx(() -> { address.setText(base + path); controller.loadPage(); return null; });
        until(() -> browser.getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED && browser.getEngine().getLocation().equals(base + path));
    }
    private void snapshot(Window window, String name) throws Exception { fx(() -> { snapshotOnFx(window, name); return null; }); }
    private void snapshotOnFx(Window window, String name) throws Exception {
        window.getScene().getRoot().applyCss(); window.getScene().getRoot().layout();
        var image = window.getScene().snapshot(null);
        BufferedImage output = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        ImageIO.write(output, "png", Path.of("target", name).toFile());
    }
    private void until(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!fx(condition)) { if (System.nanoTime() > deadline) throw new AssertionError("Timed out waiting for JavaFX state"); Thread.sleep(40); }
    }
    private static <T> T fx(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation); Platform.runLater(task); return task.get(20, TimeUnit.SECONDS);
    }
    private void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; System.out.println("PASS " + label); }
    public static void main(String[] args) { launch(args); if (failure != null) throw new AssertionError("Browser functional check failed", failure); }
}
