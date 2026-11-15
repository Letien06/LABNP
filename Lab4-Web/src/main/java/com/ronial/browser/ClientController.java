package com.ronial.browser;

import com.ronial.browser.model.HttpRequestData;
import com.ronial.browser.service.AddressBar;
import com.ronial.browser.service.BrowsingHistory;
import com.ronial.browser.service.HttpService;
import com.ronial.browser.view.DownloadsWindow;
import com.ronial.browser.view.RequestComposer;
import com.ronial.browser.view.ResponsePane;
import com.ronial.browser.viewmodel.BrowserViewModel;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.events.EventTarget;

/** Connects browser tabs and controls to WebEngine and the HTTP services. */
public final class ClientController implements Initializable, AutoCloseable {
    @FXML private WebView webView;
    @FXML private TextField textField;
    @FXML private Label statusLabel, connectionLabel;
    @FXML private TabPane browserTabs;
    @FXML private Tab pageTab, responseTab;
    @FXML private Button backButton, forwardButton, reloadButton, stopPageButton, cancelRequest;

    private final HttpService http = new HttpService();
    private final BrowserViewModel viewModel = new BrowserViewModel(http);
    private final DownloadsWindow downloads = new DownloadsWindow(http);
    private final BrowsingHistory history = new BrowsingHistory();
    private final ResponsePane responsePane = new ResponsePane();
    private final Map<Tab, WebView> pages = new LinkedHashMap<>();
    private final List<RequestComposer> composers = new ArrayList<>();
    private Tab currentPage;
    private boolean closed;

    @Override public void initialize(URL location, ResourceBundle resources) {
        installPage(pageTab, webView);
        currentPage = pageTab;
        browserTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, next) -> {
            if (pages.containsKey(next) && next != currentPage) {
                if (viewModel.busyProperty().get()) viewModel.cancel();
                currentPage = next;
                showPageState(activeEngine());
            } else if (pages.containsKey(next) && previous == responseTab) {
                showPageState(activeEngine());
            }
            updateButtons();
        });
        statusLabel.textProperty().bind(viewModel.statusProperty());
        cancelRequest.disableProperty().bind(viewModel.busyProperty().not());
        responseTab.setContent(responsePane);
        responseTab.setClosable(false);
        viewModel.responseProperty().addListener((observable, before, result) -> {
            if (result != null) {
                responsePane.display(result);
                browserTabs.getSelectionModel().select(responseTab);
            }
        });
        textField.sceneProperty().addListener((p, oldScene, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN), this::newTab);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeSelectedTab);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> {
                textField.requestFocus();
                textField.selectAll();
            });
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::refreshPage);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), this::stopPage);
        });
        home();
    }

    private void installPage(Tab tab, WebView view) {
        pages.put(tab, view);
        WebEngine engine = view.getEngine();
        engine.titleProperty().addListener((obs, before, title) -> {
            tab.setText(title == null || title.isBlank() ? "New tab" : title);
        });
        engine.locationProperty().addListener((obs, before, address) -> {
            if (tab == currentPage && browserTabs.getSelectionModel().getSelectedItem() == tab) showAddress(address);
        });
        engine.getLoadWorker().stateProperty().addListener((obs, before, state) -> {
            if (closed || !pages.containsKey(tab)) return;
            if (state == Worker.State.SUCCEEDED && isValidUrl(engine.getLocation())) {
                history.record(engine.getTitle(), engine.getLocation());
            }
            if (tab != currentPage) return;
            if (state == Worker.State.RUNNING && browserTabs.getSelectionModel().getSelectedItem() == tab) {
                viewModel.navigationStarted();
            } else if (!viewModel.busyProperty().get() && browserTabs.getSelectionModel().getSelectedItem() == tab) {
                showPageState(engine);
            }
            updateButtons();
        });
        WebHistory tabHistory = engine.getHistory();
        tabHistory.getEntries().addListener((javafx.collections.ListChangeListener<WebHistory.Entry>) change -> {
            if (tab == currentPage) updateButtons();
        });
        tabHistory.currentIndexProperty().addListener((obs, before, after) -> {
            if (tab == currentPage) updateButtons();
        });

        // Arm one direct action after real input. Page scripts alone do not start downloads.
        final class Gesture {
            boolean armed;
            void arm() {
                armed = true;
                Platform.runLater(() -> armed = false);
            }
            boolean consume() {
                boolean triggered = armed;
                armed = false;
                return triggered;
            }
        }
        Gesture gesture = new Gesture();
        view.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) gesture.arm();
        });
        view.addEventFilter(KeyEvent.KEY_PRESSED, e -> { if (e.getCode() == KeyCode.ENTER) gesture.arm(); });
        engine.documentProperty().addListener((obs, before, doc) -> {
            if (!(doc instanceof EventTarget target)) return;
            target.addEventListener("click", event -> {
                if (!(event.getTarget() instanceof Node node)) return;
                Element link = anchor(node);
                if (link == null || !link.hasAttribute("href")) return;
                URI address = linkAddress(engine, link);
                if (address == null) return;
                boolean download = link.hasAttribute("download") || isDemoDownload(address);
                boolean openInTab = "_blank".equalsIgnoreCase(link.getAttribute("target"));
                if (event instanceof org.w3c.dom.events.MouseEvent mouse) {
                    openInTab |= mouse.getCtrlKey() || mouse.getMetaKey();
                }
                if ((!download && !openInTab) || !gesture.consume()) return;
                event.preventDefault();
                Platform.runLater(() -> {
                    if (closed || !pages.containsKey(tab)) return;
                    if (download) downloadLink(address);
                    else openNewPage(address.toString());
                });
            }, true);
        });
        tab.setOnClosed(e -> removePage(tab));
        updateCloseButtons();
    }

    private static Element anchor(Node node) {
        for (Node item = node; item != null; item = item.getParentNode()) {
            if (item instanceof Element element && "a".equalsIgnoreCase(element.getTagName())) return element;
        }
        return null;
    }

    private static URI linkAddress(WebEngine engine, Element link) {
        try {
            String base = link.getBaseURI();
            URI address = URI.create(base == null ? engine.getLocation() : base).resolve(link.getAttribute("href"));
            HttpRequestData.validateUri(address);
            return address;
        } catch (IllegalArgumentException ex) { return null; }
    }

    private boolean isDemoDownload(URI address) {
        if (!address.getPath().startsWith("/download/")) return false;
        try {
            URI home = HttpRequestData.parseAddress(homeUrl());
            return home.getScheme().equalsIgnoreCase(address.getScheme())
                    && home.getHost().equalsIgnoreCase(address.getHost())
                    && port(home) == port(address);
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static int port(URI address) {
        return address.getPort() >= 0 ? address.getPort() : "https".equalsIgnoreCase(address.getScheme()) ? 443 : 80;
    }

    private static String homeUrl() { return System.getProperty("lab4.home", "http://127.0.0.1:8080/"); }

    private WebEngine activeEngine() { return pages.get(currentPage).getEngine(); }

    private Window owner() { return textField.getScene().getWindow(); }

    private void selectPage() { browserTabs.getSelectionModel().select(currentPage); }

    private void showAddress(String address) {
        if (isValidUrl(address)) {
            textField.setText(address);
            connectionLabel.setText(address.regionMatches(true, 0, "https:", 0, 6) ? "HTTPS" : "HTTP · Unencrypted");
        } else {
            textField.clear();
            connectionLabel.setText("No page");
        }
    }

    private void showPageState(WebEngine engine) {
        showAddress(engine.getLocation());
        if (!viewModel.busyProperty().get()) {
            switch (engine.getLoadWorker().getState()) {
                case RUNNING, SCHEDULED -> viewModel.setStatus("Loading…");
                case FAILED -> viewModel.setStatus("Could not load this address");
                case CANCELLED -> viewModel.setStatus("Page loading stopped");
                default -> viewModel.setStatus("Ready");
            }
        }
    }

    private void updateButtons() {
        if (!pages.containsKey(currentPage)) return;
        WebEngine engine = activeEngine();
        WebHistory entries = engine.getHistory();
        backButton.setDisable(entries.getCurrentIndex() <= 0);
        forwardButton.setDisable(entries.getCurrentIndex() + 1 >= entries.getEntries().size());
        reloadButton.setDisable(!isValidUrl(engine.getLocation()));
        stopPageButton.setDisable(!isLoading(engine.getLoadWorker()));
    }

    private static boolean isLoading(Worker<?> worker) {
        return worker.getState() == Worker.State.SCHEDULED || worker.getState() == Worker.State.RUNNING;
    }

    private void updateCloseButtons() {
        pages.keySet().forEach(tab -> tab.setClosable(pages.size() > 1));
    }

    private void removePage(Tab tab) {
        WebView view = pages.remove(tab);
        if (view == null) return;
        view.getEngine().getLoadWorker().cancel();
        if (tab == currentPage) {
            currentPage = pages.keySet().iterator().next();
            selectPage();
            showPageState(activeEngine());
        }
        updateCloseButtons();
        updateButtons();
    }

    private void closeSelectedTab() {
        Tab selected = browserTabs.getSelectionModel().getSelectedItem();
        if (pages.size() > 1 && pages.containsKey(selected)) {
            browserTabs.getTabs().remove(selected);
            removePage(selected);
        }
    }

    private void openNewPage(String address) {
        Tab tab = new Tab("New tab");
        WebView view = new WebView();
        tab.setContent(view);
        installPage(tab, view);
        browserTabs.getTabs().add(browserTabs.getTabs().indexOf(responseTab), tab);
        browserTabs.getSelectionModel().select(tab);
        navigate(address);
    }

    public BrowserViewModel getViewModel() { return viewModel; }
    public void newTab() { openNewPage(homeUrl()); }
    public void home() { navigate(homeUrl()); }
    public void getPage() { navigate(textField.getText()); }

    public void loadPage() {
        try {
            navigate(AddressBar.resolve(textField.getText()));
        } catch (IllegalArgumentException ex) { viewModel.setStatus(ex.getMessage()); }
    }

    private void navigate(String input) {
        try {
            navigate(HttpRequestData.parseAddress(input));
        } catch (IllegalArgumentException ex) { viewModel.setStatus(ex.getMessage()); }
    }

    private void navigate(URI address) {
        selectPage();
        viewModel.navigationStarted();
        activeEngine().load(address.toASCIIString());
    }

    public void refreshPage() {
        if (!isValidUrl(activeEngine().getLocation())) return;
        selectPage();
        viewModel.navigationStarted();
        activeEngine().reload();
    }

    public void stopPage() {
        Worker<Void> pageLoad = activeEngine().getLoadWorker();
        if (isLoading(pageLoad)) pageLoad.cancel();
    }

    public void back() { go(-1); }
    public void forward() { go(1); }
    private void go(int offset) {
        WebHistory history = activeEngine().getHistory();
        int index = history.getCurrentIndex() + offset;
        if (index >= 0 && index < history.getEntries().size()) {
            selectPage();
            viewModel.navigationStarted();
            history.go(offset);
        }
    }

    public void zoomIn() { zoom(.25); }
    public void zoomOut() { zoom(-.25); }
    private void zoom(double amount) {
        WebView page = pages.get(currentPage);
        page.setZoom(Math.max(.25, Math.min(3, page.getZoom() + amount)));
    }

    public void displayHistory() { new HistoryWindow(history, owner(), this::navigate).show(); }

    public void headRequest() {
        try {
            var request = new HttpRequestData(HttpRequestData.parseAddress(textField.getText()), "HEAD", Map.of(), "");
            stopPage();
            viewModel.send(request);
        } catch (IllegalArgumentException ex) { viewModel.setStatus(ex.getMessage()); }
    }

    public void postRequest() {
        RequestComposer composer = new RequestComposer(owner(), textField.getText(), viewModel, this::stopPage);
        composer.setOnClosed(() -> composers.remove(composer));
        composers.add(composer);
        composer.show();
    }

    public void cancelRequest() { viewModel.cancel(); }
    public void showDownloads() { downloads.show(owner()); }

    public CompletableFuture<Path> downloadTo(Path directory) {
        try {
            return downloads.start(HttpRequestData.parseAddress(textField.getText()), directory);
        } catch (IllegalArgumentException ex) {
            viewModel.setStatus(ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }

    public void downloadVideo() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose download folder");
        File directory = chooser.showDialog(owner());
        if (directory != null) {
            downloadTo(directory.toPath());
            showDownloads();
        }
    }

    private void downloadLink(URI address) {
        try {
            Path directory = Path.of(System.getProperty("lab4.download.dir",
                    Path.of(System.getProperty("user.home"), "Downloads").toString()));
            downloads.start(address, directory);
            showDownloads();
        } catch (IllegalArgumentException ex) { viewModel.setStatus(ex.getMessage()); }
    }

    public boolean isValidUrl(String value) {
        try {
            HttpRequestData.validateUri(URI.create(value));
            return true;
        } catch (IllegalArgumentException | NullPointerException ex) { return false; }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        List.copyOf(composers).forEach(RequestComposer::close);
        composers.clear();
        pages.values().forEach(view -> view.getEngine().getLoadWorker().cancel());
        downloads.close();
        viewModel.close();
        history.close();
    }
}
