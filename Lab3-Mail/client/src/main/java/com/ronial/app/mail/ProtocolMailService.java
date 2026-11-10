package com.ronial.app.mail;

import com.ronial.app.models.Email;
import com.ronial.app.models.User;
import com.ronial.app.views.*;
import com.ronial.app.views.utils.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

/** SMTP/POP3 adapter; Python standard library handles MIME and TLS. */
public class ProtocolMailService implements MailService {
    private String username = "", password = "";
    private final Map<Integer, String> uids = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private JSONObject call(String action, JSONObject extra) throws IOException {
        return call(action, extra, username, password);
    }

    private JSONObject call(String action, JSONObject extra, String account, String accountPassword) throws IOException {
        JSONObject request = new JSONObject().put("action", action).put("user", account)
                .put("password", accountPassword).put("provider", System.getProperty("lab3.provider", "local"))
                .put("host", System.getProperty("lab3.host", "127.0.0.1"))
                .put("smtpPort", Integer.getInteger("lab3.smtpPort", 2525))
                .put("popPort", Integer.getInteger("lab3.popPort", 8110))
                .put("accountPort", Integer.getInteger("lab3.accountPort", 8033));
        for (String key : extra.keySet()) request.put(key, extra.get(key));
        Path script = Path.of(System.getProperty("lab3.path", "."), "mail_bridge.py").toAbsolutePath();
        Process process = new ProcessBuilder(System.getProperty("lab3.python", "python"), script.toString()).start();
        var stdout = CompletableFuture.supplyAsync(() -> {
            try { return process.getInputStream().readAllBytes(); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        try {
            process.getOutputStream().write(request.toString().getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Mail connection timed out.");
            }
            String output = new String(stdout.join(), StandardCharsets.UTF_8);
            if (output.isBlank()) throw new IOException("Mail bridge could not start. Check Python and mail_bridge.py.");
            JSONObject result = new JSONObject(output);
            if (!result.optBoolean("ok")) throw new IOException(result.optString("error", "Mail operation failed."));
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); process.destroyForcibly(); throw new IOException("Mail operation interrupted.", e);
        } catch (org.json.JSONException e) { throw new IOException("Invalid mail bridge response.", e); }
    }

    public boolean usesLocalServer() {
        return !"gmail".equals(System.getProperty("lab3.provider"));
    }

    public boolean canConfigureGmailRelay() {
        String token = System.getenv("YAMAIL_ADMIN_TOKEN");
        return usesLocalServer() && token != null && !token.isBlank();
    }

    /** The bridge returns an empty sender when the server has no relay configured. */
    public String gmailRelaySender() throws IOException {
        return call("relay_status", new JSONObject(), "", "").optString("sender");
    }

    public String configureGmailRelay(String address, char[] appPassword) throws IOException {
        return call("configure_relay", new JSONObject()
                .put("relayUser", address)
                .put("relayPassword", new String(appPassword)), "", "").getString("sender");
    }

    @Override public void registerAccount(RegisterView view) throws IOException {
        JSONObject result = call("register", new JSONObject()
                .put("name", view.nameField.getText().trim())
                .put("email", view.emailField.getText().trim())
                .put("password", new String(view.passwordField.getPassword())));
        view.passwordField.setText("");
        SignInView.launchAfterRegistration(result.getString("email"));
        view.dispose();
    }

    @Override public void signInAccount(SignInView view) throws IOException {
        username = view.emailField.getText().trim();
        password = new String(view.passwordField.getPassword());
        if ("gmail".equals(System.getProperty("lab3.provider"))) password = password.strip().replace(" ", "");
        JSONObject account;
        try { account = call("login", new JSONObject()); }
        catch (IOException e) { password = ""; throw e; }
        username = account.optString("email", username);
        seen.clear();
        uids.clear();
        view.passwordField.setText("");
        view.dispose();
        MailView.launch(new User(0, "SMTP / POP3", username, "", ""));
    }

    @Override public void loadMails(MailView view) throws IOException {
        JSONArray data = call("load", new JSONObject()).getJSONArray("messages");
        List<Email> inbox = new ArrayList<>(), sent = new ArrayList<>();
        uids.clear();
        for (Object value : data) {
            JSONObject row = (JSONObject) value;
            Email email = Email.fromJSON(row.toString());
            uids.put(email.getId(), row.getString("uid"));
            email.setSeen(seen.contains(row.getString("uid")) || row.getBoolean("sent"));
            (row.getBoolean("sent") ? sent : inbox).add(email);
        }
        // Set backing lists before clearing models (selection events may fire).
        view.getEmailList().clearSelection();
        view.setInboxEmails(inbox); view.setSentEmails(sent);
        view.getInboxModel().clear(); view.getSentModel().clear();
        for (Email email : inbox) view.getInboxModel().addElement((email.isSeen() ? "" : "● ") + email.getSubject());
        for (Email email : sent) view.getSentModel().addElement(email.getSubject());
    }

    private JSONObject send(String to, String subject, String body, String links) throws IOException {
        if (subject.isBlank() || body.isBlank()) throw new IOException("Vui lòng nhập tiêu đề và nội dung thư.");
        return call("send", new JSONObject().put("to", to).put("subject", subject).put("body", body).put("links", links));
    }

    private void finishSend(JFrame composer, MailView mailbox, JSONObject result) {
        composer.dispose();
        String warning = result.optString("warning");
        if (!warning.isBlank()) {
            JSONArray refused = result.optJSONArray("refusedRecipients");
            if (refused != null) {
                for (Object item : refused) warning += "\n• " + ((JSONObject) item).optString("email");
            }
            Toast.info(warning);
        } else if (result.optBoolean("relayAccepted") || "gmail".equals(System.getProperty("lab3.provider"))) {
            Toast.info("Máy chủ Gmail đã chấp nhận thư.");
        }
        mailbox.refreshInbox();
    }
    @Override public void sendMail(CreateMailView view) throws IOException {
        JSONObject result = send(view.recipientField.getText(), view.subjectField.getText(), view.messageArea.getText(), view.attachLink.getText());
        finishSend(view, view.mailView, result);
    }
    @Override public void deleteMail(MailView view) throws IOException {
        int index = view.getEmailList().getSelectedIndex();
        if (index < 0) throw new IOException("Select a message to delete.");
        Email email = view.getEmailFromTabbedPane(index);
        call("delete", new JSONObject().put("uid", uids.get(email.getId())));
        view.refreshInbox();
    }
    @Override public void transferMail(TransferMailView view) throws IOException {
        String text = view.email.getBody().replace("<br/>", "\n").replaceAll("<[^>]*>", "");
        JSONObject result = send(view.emailsField.getText(), "Fwd: " + view.email.getSubject(),
                "Forwarded from " + view.email.getFrom() + "\n\n" + text, view.email.getLinks());
        finishSend(view, view.mailView, result);
    }
    @Override public void readMail(MailView view, Email email) {
        email.setSeen(true);
        String uid = uids.get(email.getId());
        if (uid != null) seen.add(uid);
        SwingUtilities.invokeLater(() -> {
            int index = view.getInboxEmails().indexOf(email);
            if (index >= 0 && index < view.getInboxModel().size()) view.getInboxModel().set(index, email.getSubject());
        });
    }
}
