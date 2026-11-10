package com.ronial.app.mail;

import com.ronial.app.context.Context;
import com.ronial.app.models.Email;
import com.ronial.app.views.*;

import java.io.IOException;

public interface MailService extends Context {
    void registerAccount(RegisterView view) throws IOException;
    void signInAccount(SignInView view) throws IOException;
    void sendMail(CreateMailView view) throws IOException;
    void loadMails(MailView view) throws IOException;
    void deleteMail(MailView view) throws IOException;
    void transferMail(TransferMailView view) throws IOException;
    void readMail(MailView view, Email email) throws IOException;
}
