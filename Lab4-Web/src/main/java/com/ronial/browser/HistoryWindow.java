package com.ronial.browser;

import com.ronial.browser.service.BrowsingHistory;
import com.ronial.browser.service.BrowsingHistory.Entry;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class HistoryWindow {
    private static final DateTimeFormatter VISIT_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Stage stage = new Stage();

    public HistoryWindow(BrowsingHistory history, Consumer<String> openPage) {
        this(history, null, openPage);
    }

    public HistoryWindow(BrowsingHistory history, Window owner, Consumer<String> openPage) {
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Lịch sử duyệt web");

        ListView<Entry> list = new ListView<>();
        list.setId("historyList");
        list.getItems().setAll(history.entries());
        list.setPlaceholder(new Label("Chưa có lịch sử duyệt web."));
        list.setCellFactory(view -> {
            ListCell<Entry> cell = new ListCell<>() {
                @Override
                protected void updateItem(Entry entry, boolean empty) {
                    super.updateItem(entry, empty);
                    setText(empty || entry == null ? null
                            : entry.title() + "\n" + entry.url() + "\n" + VISIT_TIME.format(entry.visitedAt()));
                }
            };
            cell.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !cell.isEmpty()) {
                    open(cell.getItem(), openPage);
                }
            });
            return cell;
        });
        list.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                open(list.getSelectionModel().getSelectedItem(), openPage);
                event.consume();
            }
        });

        Button open = new Button("Mở trang");
        open.setId("openHistoryEntry");
        open.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        open.setOnAction(event -> open(list.getSelectionModel().getSelectedItem(), openPage));

        Button clear = new Button("Xóa lịch sử");
        clear.disableProperty().bind(Bindings.isEmpty(list.getItems()));
        clear.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Xóa toàn bộ lịch sử duyệt web?");
            confirm.initOwner(stage);
            confirm.setHeaderText(null);
            confirm.showAndWait().filter(ButtonType.OK::equals).ifPresent(choice -> {
                history.clear();
                list.getItems().clear();
            });
        });

        Button close = new Button("Đóng");
        close.setOnAction(event -> stage.close());
        close.setCancelButton(true);

        VBox root = new VBox(12, list, new HBox(8, open, clear, close));
        root.setPadding(new Insets(12));
        VBox.setVgrow(list, Priority.ALWAYS);
        stage.setScene(new Scene(root, 560, 390));
    }

    private void open(Entry entry, Consumer<String> openPage) {
        if (entry == null) return;
        stage.close();
        openPage.accept(entry.url());
    }

    public void show() {
        stage.show();
    }
}
