package com.ronial.browser;

import com.ronial.browser.model.HttpRequestData;
import com.ronial.browser.view.RequestComposer;
import com.ronial.browser.view.ResponsePane;
import com.ronial.browser.viewmodel.BrowserViewModel;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import javafx.concurrent.Worker;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.web.*;
import javafx.stage.DirectoryChooser;

/** Connects browser controls to WebEngine and the HTTP view model. */
public class ClientController implements Initializable, AutoCloseable {
    @FXML private WebView webView;
    @FXML private TextField textField;
    @FXML private Label statusLabel;
    @FXML private Label connectionLabel;
    @FXML private TabPane browserTabs;
    @FXML private Tab pageTab;
    @FXML private Tab responseTab;
    @FXML private Button cancelRequest;
    private final BrowserViewModel viewModel = new BrowserViewModel();
    private final ResponsePane responsePane = new ResponsePane();
    private WebEngine engine;
    private double webZoom = 1;
    private boolean closed;
    private final java.util.List<RequestComposer> composers = new java.util.ArrayList<>();
    @Override public void initialize(URL location, ResourceBundle resources) {
        engine = webView.getEngine();
        statusLabel.textProperty().bind(viewModel.statusProperty());
        cancelRequest.disableProperty().bind(viewModel.busyProperty().not());
        responseTab.setContent(responsePane);
        viewModel.responseProperty().addListener((observable, before, result) -> {
            if (result != null) { responsePane.display(result); browserTabs.getSelectionModel().select(responseTab); }
        });
        engine.getLoadWorker().stateProperty().addListener((o, previous, state) -> {
            if (state == Worker.State.RUNNING) {
                viewModel.navigationStarted(); browserTabs.getSelectionModel().select(pageTab);
            }
            if (state == Worker.State.SUCCEEDED) {
                String url = engine.getLocation();
                if (url.startsWith("http")) {
                    textField.setText(url);
                    connectionLabel.setText(url.startsWith("https:") ? "HTTPS" : "HTTP • local / unencrypted");
                }
                if (!viewModel.busyProperty().get()) viewModel.setStatus("Ready");
            } else if (state == Worker.State.FAILED && !viewModel.busyProperty().get()) viewModel.setStatus("Could not load this address");
        });
        home();
    }
    public BrowserViewModel getViewModel() { return viewModel; }
    public void home() { textField.setText(System.getProperty("lab4.home", "http://127.0.0.1:8080/")); loadPage(); }
    public void loadPage() {
        try {
            String address = HttpRequestData.parseAddress(textField.getText()).toString();
            viewModel.navigationStarted(); browserTabs.getSelectionModel().select(pageTab); engine.load(address);
        } catch (IllegalArgumentException ex) { viewModel.setStatus(ex.getMessage()); }
    }
    public void headRequest() {
        try {
            var request = new HttpRequestData(HttpRequestData.parseAddress(textField.getText()), "HEAD", Map.of(), "");
            engine.getLoadWorker().cancel(); viewModel.send(request);
        } catch (IllegalArgumentException ex) { viewModel.setStatus(ex.getMessage()); }
    }
    public void postRequest() {
        RequestComposer composer = new RequestComposer(textField.getScene().getWindow(), textField.getText(), viewModel,
                () -> engine.getLoadWorker().cancel());
        composer.setOnClosed(() -> composers.remove(composer));
        composers.add(composer); composer.show();
    }
    public void cancelRequest() { viewModel.cancel(); engine.getLoadWorker().cancel(); }
    public void refreshPage() { loadPage(); }
    public void zoomIn() { webZoom = Math.min(3, webZoom + .25); webView.setZoom(webZoom); }
    public void zoomOut() { webZoom = Math.max(.25, webZoom - .25); webView.setZoom(webZoom); }
    public void displayHistory() { new HistoryWindow(engine.getHistory(), textField.getScene().getWindow()).show(); }
    public void back() { WebHistory h = engine.getHistory(); if (h.getCurrentIndex() > 0) { viewModel.navigationStarted(); h.go(-1); } }
    public void forward() { WebHistory h = engine.getHistory(); if (h.getCurrentIndex() + 1 < h.getEntries().size()) { viewModel.navigationStarted(); h.go(1); } }
    public boolean isValidUrl(String value) { try { HttpRequestData.parseAddress(value); return true; } catch (IllegalArgumentException ex) { return false; } }
    public CompletableFuture<Path> downloadTo(Path directory) {
        engine.getLoadWorker().cancel();
        return viewModel.download(textField.getText(), directory);
    }
    public void downloadVideo() {
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Choose download folder");
        File directory = chooser.showDialog(textField.getScene().getWindow());
        if (directory != null) downloadTo(directory.toPath());
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        java.util.List.copyOf(composers).forEach(RequestComposer::close);
        composers.clear();
        engine.getLoadWorker().cancel(); viewModel.close();
    }
}
