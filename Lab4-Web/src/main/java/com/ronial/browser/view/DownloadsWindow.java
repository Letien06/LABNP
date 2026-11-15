package com.ronial.browser.view;

import com.ronial.browser.model.DownloadProgress;
import com.ronial.browser.model.DownloadResult;
import com.ronial.browser.service.HttpService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class DownloadsWindow implements AutoCloseable {
    private final HttpService service;
    private final ListView<DownloadRow> downloads = new ListView<>();
    private Stage stage;

    public DownloadsWindow(HttpService service) {
        this.service = Objects.requireNonNull(service);
        downloads.setId("downloadsList");
        downloads.setPlaceholder(new Label("No downloads yet."));
        downloads.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(DownloadRow row, boolean empty) {
                super.updateItem(row, empty);
                setGraphic(empty ? null : row.content);
            }
        });
    }

    public void show(Window owner) {
        if (stage == null) {
            stage = new Stage();
            if (owner != null) stage.initOwner(owner);
            stage.setTitle("Downloads");
            stage.setScene(new Scene(downloads, 620, 320));
        }
        stage.show();
        stage.toFront();
    }

    public CompletableFuture<Path> start(URI uri, Path directory) {
        DownloadRow row = new DownloadRow(uri);
        CompletableFuture<DownloadResult> transfer = service.download(uri, directory,
                progress -> Platform.runLater(() -> row.update(progress)));
        downloads.getItems().add(0, row);
        row.cancel.setOnAction(event -> transfer.cancel(true));
        transfer.whenComplete((result, failure) -> Platform.runLater(() -> {
            row.finished = true;
            row.cancel.setDisable(true);
            if (result != null) {
                row.complete(result);
            } else {
                row.progress.setProgress(0);
                row.status.setText(transfer.isCancelled() ? "Canceled" : "Failed — " + message(failure));
            }
        }));
        row.transfer = transfer;
        CompletableFuture<Path> path = new CompletableFuture<>();
        transfer.whenComplete((result, failure) -> {
            if (transfer.isCancelled()) path.cancel(false);
            else if (failure != null) path.completeExceptionally(failure);
            else path.complete(result.path());
        });
        path.whenComplete((value, failure) -> {
            if (path.isCancelled() && !transfer.isDone()) transfer.cancel(true);
        });
        return path;
    }

    @Override public void close() {
        for (DownloadRow row : downloads.getItems()) row.transfer.cancel(true);
        if (stage != null) stage.close();
    }

    private static String message(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static final class DownloadRow {
        final Label name = new Label();
        final Label status = new Label("Connecting…");
        final ProgressBar progress = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        final Button cancel = new Button("Cancel");
        final Button folder = new Button("Open folder");
        final HBox content;
        CompletableFuture<DownloadResult> transfer;
        boolean finished;

        DownloadRow(URI uri) {
            name.setText(uri.toString());
            name.setStyle("-fx-font-weight: bold;");
            status.getStyleClass().add("download-status");
            progress.getStyleClass().add("download-progress");
            cancel.getStyleClass().add("download-cancel");
            folder.getStyleClass().add("download-open-folder");
            progress.setMaxWidth(Double.MAX_VALUE);
            folder.setDisable(true);
            VBox details = new VBox(4, name, progress, status);
            details.setMinWidth(0);
            HBox.setHgrow(details, Priority.ALWAYS);
            content = new HBox(10, details, cancel, folder);
            content.setPadding(new Insets(7));
        }

        void update(DownloadProgress update) {
            if (finished) return;
            long bytes = update.bytesReceived();
            if (update.totalBytes().isPresent()) {
                long total = update.totalBytes().getAsLong();
                progress.setProgress(total == 0 ? 1 : Math.min(1, (double) bytes / total));
                status.setText(String.format("%,d / %,d bytes", bytes, total));
            } else {
                status.setText(String.format("%,d bytes", bytes));
            }
        }

        void complete(DownloadResult result) {
            name.setText(result.path().getFileName().toString());
            progress.setProgress(1);
            String completed = String.format("Completed · %,d bytes", result.bytes());
            status.setText(completed);
            folder.setDisable(false);
            folder.setOnAction(event -> {
                folder.setDisable(true);
                CompletableFuture.runAsync(() -> openFolder(result.path().getParent()))
                        .whenComplete((ignored, error) -> Platform.runLater(() -> {
                            folder.setDisable(false);
                            status.setText(error == null ? completed : "Could not open folder — " + message(error));
                        }));
            });
        }

        private static void openFolder(Path directory) {
            try {
                Desktop.getDesktop().open(directory.toFile());
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }
    }
}
