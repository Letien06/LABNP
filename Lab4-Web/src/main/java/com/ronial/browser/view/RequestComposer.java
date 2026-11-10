package com.ronial.browser.view;

import com.ronial.browser.model.HttpRequestData;
import com.ronial.browser.viewmodel.BrowserViewModel;
import java.util.LinkedHashMap;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Modeless request editor; retains input after validation and transport errors. */
public final class RequestComposer {
    private final Stage stage = new Stage();
    private Runnable onClosed = () -> {};
    public RequestComposer(Window owner, String address, BrowserViewModel model, Runnable beforeSend) {
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Compose POST request");
        TextField url = new TextField(address); url.setId("requestUrl");
        TextArea headers = new TextArea("Content-Type: application/json; charset=UTF-8"); headers.setId("requestHeaders");
        TextArea body = new TextArea(); body.setId("requestBody");
        Label validation = new Label(); validation.setId("requestValidation"); validation.setWrapText(true);
        headers.setPrefRowCount(4); body.setPrefRowCount(10);
        headers.setPromptText("Header-Name: value (one per line)");
        body.setPromptText("JSON, form fields, or plain text — sent exactly as entered");
        Button send = new Button("Send POST"); send.setId("sendRequest"); send.setDefaultButton(true);
        Button cancel = new Button("Cancel request"); cancel.setId("composerCancelRequest");
        send.disableProperty().bind(model.busyProperty());
        cancel.disableProperty().bind(model.busyProperty().not());
        cancel.setOnAction(event -> model.cancel());
        send.setOnAction(event -> {
            try {
                var parsedHeaders = new LinkedHashMap<String, String>();
                for (String line : headers.getText().split("\\R")) {
                    if (line.isBlank()) continue;
                    int colon = line.indexOf(':');
                    if (colon <= 0) throw new IllegalArgumentException("Use Header-Name: value for each header line");
                    String name = line.substring(0, colon).trim();
                    if (parsedHeaders.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name)))
                        throw new IllegalArgumentException("Duplicate header: " + name);
                    parsedHeaders.put(name, line.substring(colon + 1).trim());
                }
                var request = new HttpRequestData(HttpRequestData.parseAddress(url.getText()), "POST", parsedHeaders, body.getText());
                validation.setText(""); beforeSend.run(); model.send(request);
            } catch (IllegalArgumentException ex) { validation.setText(ex.getMessage()); }
        });
        Label status = new Label(); status.textProperty().bind(model.statusProperty()); status.setWrapText(true);
        VBox content = new VBox(10, label("URL", url), url, label("Headers", headers), headers,
                label("Request body", body), body, validation, new HBox(10, send, cancel), status);
        content.setPadding(new Insets(20)); VBox.setVgrow(body, Priority.ALWAYS);
        Scene scene = new Scene(content, 650, 600);
        var css = RequestComposer.class.getResource("/browser.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN), send::fire);
        stage.setScene(scene); stage.setMinWidth(460); stage.setMinHeight(480);
        stage.setOnHidden(event -> {
            send.disableProperty().unbind(); cancel.disableProperty().unbind(); status.textProperty().unbind();
            onClosed.run();
        });
    }
    private static Label label(String text, javafx.scene.Node control) { Label label = new Label(text); label.setLabelFor(control); return label; }
    public void show() { stage.show(); stage.toFront(); }
    public void setOnClosed(Runnable action) { onClosed = action; }
    public void close() { stage.close(); }
}
