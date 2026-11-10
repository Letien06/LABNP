package com.ronial.browser.view;

import com.ronial.browser.model.HttpResponseData;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Response text is always native text, never executable page markup. */
public final class ResponsePane extends VBox {
    private final Label summary = new Label("Send a HEAD or POST request to inspect its response");
    private final TextArea headers = new TextArea();
    private final TextArea body = new TextArea();
    public ResponsePane() {
        super(12); setPadding(new Insets(18));
        summary.setId("responseSummary"); summary.setWrapText(true);
        headers.setId("responseHeaders"); body.setId("responseBody");
        headers.setEditable(false); body.setEditable(false);
        headers.setPrefRowCount(7); body.setWrapText(true);
        headers.setAccessibleText("Response headers"); body.setAccessibleText("Response body");
        getChildren().addAll(summary, new Label("Headers"), headers, new Label("Body"), body);
        VBox.setVgrow(body, Priority.ALWAYS);
    }
    public void display(HttpResponseData response) {
        summary.setText(response.method() + " · HTTP " + response.statusCode() + " · " + response.elapsedMillis() + " ms\n" + response.uri());
        StringBuilder text = new StringBuilder();
        response.headers().forEach((name, values) -> values.forEach(value -> text.append(name).append(": ").append(value).append('\n')));
        headers.setText(text.toString()); body.setText(response.body());
        headers.positionCaret(0); body.positionCaret(0);
    }
}
