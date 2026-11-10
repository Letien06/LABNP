package com.ronial.browser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Objects;

public class Main extends Application{

	@Override
	public void start(Stage primaryStage) {
		try {
			Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/main.fxml")));
			Scene scene = new Scene(root, 1200, 780);
			primaryStage.setTitle("Lab4 • Ronial Browser");
			primaryStage.setScene(scene);
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}

}
