package com.ronial.app;

import com.formdev.flatlaf.FlatLightLaf;
import com.ronial.app.conf.db.DatabaseConf;
import com.ronial.app.conf.db.DatabaseProperty;
import com.ronial.app.context.ContextProvider;
import com.ronial.app.infrastructure.mail.MailConf;
import com.ronial.app.mail.Server;
import com.ronial.app.mail.service.MailService;
import com.ronial.app.mail.service.MailServiceImpl;
import com.ronial.app.repositories.RepositoryInitializer;
import com.ronial.app.security.RSASecurity;
import com.ronial.app.views.GmailAppPasswordForm;
import com.ronial.app.views.LogFrame;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class MailServerApplication {
    public static final String MAIL_FOLDER_NAME = "mails";
    public static final String MAIL_LOG_FOLDER_NAME = "logs";

    static {
        File mails = new File(MAIL_FOLDER_NAME);
        File logs = new File(MAIL_LOG_FOLDER_NAME);
        if (!mails.exists()) {
            mails.mkdirs();
        }
        if (!logs.exists()) {
            logs.mkdirs();
        }
        try {
            UIManager.setLookAndFeel(new FlatLightLaf()); // Hoặc FlatDarkLaf cho chế độ tối
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }
    }

    public static void start(MailConf.AuthenticationProps mailAuthProps) throws IOException {
        LogFrame logFrame = new LogFrame();
        ContextProvider.register(LogFrame.class, logFrame);
        String welcome = "\n\n";
        welcome += "===============================================================================\n\n";
        welcome += "\t\t\t TO YA-MAIL SERVER!\n\n";
        welcome += "===============================================================================\n\n";
        logFrame.addLog(MailServerApplication.class, welcome);
        //Session mailer initialize
        MailConf.initialize(mailAuthProps);
        //Database configuration and connection
        DatabaseProperty databaseProperty = new DatabaseProperty(
                "localhost",
                3306,
                "mailsv",
                "mailsv",
                "mailsv_udp");
        DatabaseConf databaseConf = new DatabaseConf();
        databaseConf.connect(DatabaseConf.DatabaseType.MYSQL, databaseProperty);
        ContextProvider.register(DatabaseConf.class, databaseConf);
        //Repository initializer new construction
        RepositoryInitializer.initialize();
        //Security configuration loading key from folder
        RSASecurity security = new RSASecurity();
        ContextProvider.register(RSASecurity.class, security);
        //Mail service new instance
        MailService mailService = new MailServiceImpl();
        ContextProvider.register(MailService.class, mailService);
        //Start mail server
        Server server = Server.launch();
        ContextProvider.register(Server.class, server);

    }

    public static void main(String[] args) {
        new GmailAppPasswordForm();
    }
}