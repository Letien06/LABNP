module com.ronial.browser {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.graphics;
    requires javafx.base;

    opens com.ronial.browser to javafx.fxml;
    exports com.ronial.browser;
}