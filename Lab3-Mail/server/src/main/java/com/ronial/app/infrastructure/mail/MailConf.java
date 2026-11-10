package com.ronial.app.infrastructure.mail;

import com.ronial.app.context.ContextProvider;

import java.util.Properties;

public class MailConf {
    public record AuthenticationProps(String username, String password) {}
    private MailConf() {}
    public static void initialize(AuthenticationProps props) {
        SessionMailer.AuthProperties authProperties =
                new SessionMailer.AuthProperties(props.username,  props.password);
        Properties sessionProperties = new Properties();
        sessionProperties.put("mail.smtp.auth", "true");
        sessionProperties.put("mail.smtp.starttls.enable", "true");
        sessionProperties.put("mail.smtp.host", "smtp.gmail.com");
        sessionProperties.put("mail.smtp.port", "587");

        SessionMailer sessionMailer = new SessionMailer(sessionProperties,authProperties);
        ContextProvider.register(SessionMailer.class, sessionMailer);
    }
}
