package com.ronial.browser;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.web.WebHistory;

public class HistoryWindow {

    private Stage stage;
    private ListView<String> listView;

    public HistoryWindow(WebHistory history) {
        stage = new Stage();
        stage.setTitle("Lịch sử duyệt web");

        listView = new ListView<>();
        ObservableList<WebHistory.Entry> entries = history.getEntries();

//        for (WebHistory.Entry entry : entries) {
//            listView.getItems().add(entry.getUrl());
//        }
//        for (WebHistory.Entry entry : entries) {
//            listView.getItems().add(entry.getTitle() + " (" + entry.getLastVisitedDate() + "): " + entry.getUrl());
//        }
//
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        for (int i = entries.size() - 1; i >= 0; i--) {
            WebHistory.Entry entry = entries.get(i);
            LocalDateTime dateTime = LocalDateTime.ofInstant(entry.getLastVisitedDate().toInstant(), ZoneId.systemDefault());
            String formattedDate = dateTime.format(formatter);
            listView.getItems().add("(" + formattedDate + "): " + entry.getUrl());
        }

        Button closeButton = new Button("Đóng");
        closeButton.setOnAction(e -> stage.close());

        VBox vbox = new VBox(listView, closeButton);
        Scene scene = new Scene(vbox, 400, 300);
        stage.setScene(scene);
    }

    public void show() {
        stage.show();
    }
}
