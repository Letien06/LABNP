package com.ronial.app.infrastructure.mail;

import com.ronial.app.context.Context;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import java.util.Properties;

public class SessionMailer implements Context {
    public record AuthProperties(String username, String password) {}
    private final Session session;

    public SessionMailer(Properties sessionProperties, AuthProperties authProperties) {
        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(authProperties.username, authProperties.password);
            }
        };
        // Khởi tạo Session
        session = Session.getInstance(sessionProperties, auth);
    }

    public Session getSession() {
        return session;
    }
}
