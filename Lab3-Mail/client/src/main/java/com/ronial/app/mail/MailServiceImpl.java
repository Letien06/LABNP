package com.ronial.app.mail;

import com.ronial.app.models.Email;
import com.ronial.app.models.Request;
import com.ronial.app.models.Response;
import com.ronial.app.models.User;
import com.ronial.app.utils.DateUtils;
import com.ronial.app.utils.RegexUtils;
import com.ronial.app.utils.StringUtils;
import com.ronial.app.views.*;
import com.ronial.app.views.utils.Toast;
import org.json.JSONArray;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MailServiceImpl implements MailService {
    private final Client client;

    public MailServiceImpl(Client client) {
        this.client = client;
    }

    @Override
    public void registerAccount(RegisterView view) throws IOException {
        Request request = new Request(CommandType.REGISTER_ACCOUNT);
        String email = view.emailField.getText().trim();
        if (!RegexUtils.isEmail(email)){
            email = view.emailField.getText().trim() + "@gmail.com";
        }
        request.getData()
                .put("name", view.nameField.getText().trim())
                .put("email", email)
                .put("password", new String(view.passwordField.getPassword()).trim());

        client.sendRequest(request);

        Response response = client.receiveResponse();
        if (response.isSuccess()) {
            Toast.info(response.getMessage());
            view.dispose();
            SignInView.launch();
        } else {
            Toast.error(response.getMessage());
        }
    }

    @Override
    public void signInAccount(SignInView view) throws IOException {
        Request request = new Request(CommandType.LOGIN_ACCOUNT);
        String email = view.emailField.getText().trim();
        if (!RegexUtils.isEmail(email)){
            email = view.emailField.getText().trim() + "@gmail.com";
        }
        request.getData()
                .put("email",email)
                .put("password", new String(view.passwordField.getPassword()).trim());
        client.sendRequest(request);

        Response response = client.receiveResponse();
        if (response.isSuccess()) {
            Toast.info(response.getMessage());
            view.dispose();
            String userJson = response.getData().getString("user");
            User user = User.fromJson(userJson);
            MailView.launch(user);
        } else {
            Toast.error(response.getMessage());
        }
    }
    private StringBuilder validateSendMail(Email email){
        StringBuilder errors = new StringBuilder();
        if (email.getTo().trim().isBlank()){
            errors.append("Địa chỉ đến không thể trống.\n");
        }
        if (email.getSubject().trim().isBlank()){
            errors.append("Tiêu đề không thể trống.\n");
        }
        if (email.getBody().trim().isBlank()){
            errors.append("Nội dung không thể trống.\n");
        }
        return errors;
    }
    @Override
    public void sendMail(CreateMailView view) throws IOException {
        Request request = new Request(CommandType.SEND_MAIL);
        StringBuilder recipients = new StringBuilder();
        if (!view.recipientField.getText().trim().isBlank()){
            Arrays.stream(view.recipientField.getText().trim().split(","))
                    .forEach(to -> {
                        if (RegexUtils.isEmail(to.trim())){
                            recipients.append(to.trim());
                        } else{
                            recipients.append(to).append("@gmail.com");
                        }
                        recipients.append(",");
                    });
        }
        Email email = new Email();
        email.setTo(recipients.toString().trim());
        email.setFrom(view.mailView.getUser().getEmail());
        email.setSubject(view.subjectField.getText().trim());
        email.setBody(view.messageArea.getText().trim().replace("\n", "<br/>"));
        email.setLinks(view.attachLink.getText().trim());
        email.setIsSeen(false);
        request.getData()
                .put("email", email.toJSON());
        StringBuilder errors = validateSendMail(email);
        if (!errors.isEmpty()){
            Toast.error(errors.toString());
            return;
        }
        client.sendRequest(request);
        Response response = client.receiveResponse();
        if (response.isSuccess()) {
            Toast.info(response.getMessage());
            view.mailView.getEmailContent().setText("");
            view.mailView.refreshInbox();
            view.dispose();
        } else {
            Toast.error(response.getMessage());
        }
    }

    @Override
    public void loadMails(MailView view) throws IOException {
        Request request = new Request(CommandType.GET_MAILS);
        request.getData().put("email", view.getUser().getEmail());
        client.sendRequest(request);

        Response response = client.receiveResponse();
        if (response.isSuccess()) {
            JSONArray jsonArrayEmailsReceive = new JSONArray(response.getData().getString("emailsReceive"));
            JSONArray jsonArrayEmailsSent= new JSONArray(response.getData().getString("emailsSent"));

            view.getInboxModel().clear();
            view.getSentModel().clear();

            List<Email> emailsReceive = new ArrayList<>();
            List<Email> emailsSent = new ArrayList<>();

            jsonArrayEmailsReceive.forEach(o -> {
                Email email = Email.fromJSON(o.toString());
                emailsReceive.add(email);
                view.getInboxModel().addElement(" ✉️ " + (email.isSeen() ? "" : " (Chưa đọc) ") + StringUtils.truncateString(email.getSubject(), 30));
            });
            jsonArrayEmailsSent.forEach(o -> {
                Email email = Email.fromJSON(o.toString());
                email.setSeen(true);
                emailsSent.add(email);
                view.getSentModel().addElement(" ✉️ " + StringUtils.truncateString(email.getSubject(), 30));
            });
            view.setInboxEmails(emailsReceive);
            view.setSentEmails(emailsSent);
        } else {
            Toast.error(response.getMessage());
        }
    }

    @Override
    public void deleteMail(MailView view) throws IOException {
        Request request = new Request(CommandType.DELETE_MAIL);
        int selectedEmailIndex = view.getEmailList().getSelectedIndex();
        Email email = view.getEmailFromTabbedPane(selectedEmailIndex);
        request.getData()
                .put("email", view.getUser().getEmail())
                .put("id", email.getId());
        client.sendRequest(request);
        Response response = client.receiveResponse();
        if (response.isSuccess()) {
            view.refreshInbox();
            view.getEmailContent().setText("");
            Toast.info(response.getMessage());
        } else {
            Toast.error(response.getMessage());
        }
    }

    @Override
    public void transferMail(TransferMailView view) throws IOException {
        Request request = new Request(CommandType.TRANSFER_MAIL);
        StringBuilder recipients = new StringBuilder();
        if (!view.emailsField.getText().trim().isBlank()){
            Arrays.stream(view.emailsField.getText().trim().split(","))
                    .forEach(to -> {
                        if (RegexUtils.isEmail(to.trim())){
                            recipients.append(to.trim());
                        } else{
                            recipients.append(to).append("@gmail.com");
                        }
                        recipients.append(",");
                    });
        } else {
            Toast.error("Địa chỉ đến không thể trống");
            return;
        }
        Email email = view.email;
        email.setTransferFrom(view.mailView.getUser().getEmail());
        request.getData()
                .put("email", email.toJSON())
                .put("emails", recipients.toString().trim());
        client.sendRequest(request);
        Response response = client.receiveResponse();
        if (response.isSuccess()) {
            Toast.info(response.getMessage());
            view.dispose();
        } else {
            Toast.error(response.getMessage());
        }
    }

    @Override
    public void readMail(MailView view, Email email) throws IOException {
        Request request = new Request(CommandType.READ_MAIL);
        request.getData()
                .put("email", view.getUser().getEmail())
                .put("id", email.getId());
        client.sendRequest(request);

        Response response = client.receiveResponse();
        if (response.isSuccess()) {
            Email emailRead = Email.fromJSON(response.getData().getString("email"));
           view.displayEmailContent(emailRead);
        }
    }
}
