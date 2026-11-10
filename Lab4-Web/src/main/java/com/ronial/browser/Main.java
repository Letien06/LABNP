package com.ronial.browser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Objects;

public final class Main extends Application {
    private ClientController controller;

    @Override public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(getClass().getResource("/main.fxml")));
        Parent root = loader.load();
        controller = loader.getController();
        primaryStage.setScene(new Scene(root, 1200, 780));
        primaryStage.setTitle("Lab4 • Ronial Browser");
        primaryStage.setOnCloseRequest(event -> controller.close());
        primaryStage.show();
    }

    @Override public void stop() { if (controller != null) controller.close(); }
    public static void main(String[] args) { launch(args); }
}
