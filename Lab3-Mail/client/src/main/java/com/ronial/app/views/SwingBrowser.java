package com.ronial.app.views;

import com.ronial.app.context.Context;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebView;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

public class SwingBrowser extends JFrame implements Context {
    private String url;
    private WebEngine webEngine;

    public static SwingBrowser launch() {
        return new SwingBrowser();
    }

    public SwingBrowser() {
        try {
            createAndShowGUI();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public SwingBrowser(String url) {
        this.url = url;
        validURL();
        createAndShowGUI();
    }

    private void validURL() {
        if (!this.url.startsWith("http://") && !this.url.startsWith("https://")) {
            this.url = "https://" + this.url;
        }
    }

    public void close() {
        setVisible(false);
        Platform.runLater(() -> {
            webEngine.load("");
        });
    }

    public void open(String url) {
        this.url = url;
        validURL();
        Platform.runLater(() -> {
            webEngine.load(this.url);
        });
        setVisible(true);
    }

    public void createAndShowGUI() {
        setTitle("Browser");
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        setLayout(new BorderLayout());
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());

        JFXPanel fxPanel = new JFXPanel(); // Nhúng JavaFX vào Swing
        add(fxPanel, BorderLayout.CENTER);

        Platform.runLater(() -> {
            WebView webView = new WebView();
            webEngine = webView.getEngine();
            WebHistory history = webEngine.getHistory();

            TextField urlField = new TextField(this.url);
            urlField.setFont(new Font("Arial", 14));
            HBox.setHgrow(urlField, Priority.ALWAYS);

            urlField.setOnAction(e -> {
                this.url = urlField.getText().trim();
                validURL();
                webEngine.load(this.url);
            });

            Button backButton = createStyledButton("←");
            backButton.setOnAction(e -> {
                if (history.getCurrentIndex() > 0) {
                    history.go(-1);
                    Platform.runLater(() -> urlField.setText(history.getEntries().get(history.getCurrentIndex()).getUrl()));
                }
            });

            Button forwardButton = createStyledButton("→");
            forwardButton.setOnAction(e -> {
                if (history.getCurrentIndex() < history.getEntries().size() - 1) {
                    history.go(1);
                    Platform.runLater(() -> urlField.setText(history.getEntries().get(history.getCurrentIndex()).getUrl()));
                }
            });

            Button zoomInButton = createStyledButton("+");
            zoomInButton.setOnAction(e -> webView.setZoom(webView.getZoom() + 0.1));

            Button zoomOutButton = createStyledButton("-");
            zoomOutButton.setOnAction(e -> webView.setZoom(webView.getZoom() - 0.1));

            HBox controls = new HBox(5, backButton, forwardButton, urlField, zoomInButton, zoomOutButton);
            controls.setStyle("-fx-padding: 10px; -fx-background-color: #f0f0f0; -fx-border-width: 0px 0px 2px 0px; -fx-border-color: #cccccc;");
            BorderPane root = new BorderPane();
            root.setBottom(controls);
            root.setCenter(webView);

            webEngine.locationProperty().addListener((obs, oldLocation, newLocation) -> {
                Platform.runLater(() -> urlField.setText(newLocation));
            });

            webEngine.load(urlField.getText());

            fxPanel.setScene(new Scene(root));
        });
    }

    private Button createStyledButton(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-font-size: 14px; -fx-padding: 5px 10px; -fx-background-color: #0078D7; -fx-text-fill: white; -fx-border-radius: 5px;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-font-size: 14px; -fx-padding: 5px 10px; -fx-background-color: #005A9E; -fx-text-fill: white; -fx-border-radius: 5px;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-font-size: 14px; -fx-padding: 5px 10px; -fx-background-color: #0078D7; -fx-text-fill: white; -fx-border-radius: 5px;"));
        return button;
    }
}
