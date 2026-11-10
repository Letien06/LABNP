module com.ronial.browser {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.graphics;
    requires javafx.base;
    requires java.net.http;
    requires jdk.httpserver;

    opens com.ronial.browser to javafx.fxml;
    exports com.ronial.browser;
    exports com.ronial.browser.model;
    exports com.ronial.browser.service;
    exports com.ronial.browser.server;
    exports com.ronial.browser.viewmodel;
}
