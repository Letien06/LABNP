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
    private Map<String, CachedMessage> cachedRows = Map.of();
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
        view.passwordField.setText("");
        view.dispose();
        ProtocolMailService session = new ProtocolMailService();
        session.username = username;
        session.password = password;
        password = "";
        MailView.launch(new User(0, "SMTP / POP3", username, "", ""), session);
    }

    private record CachedMessage(int id, boolean sent, Email email) {
        MailRow forDisplay(String uid, boolean read) {
            return new MailRow(uid, new Email(id, email.getFrom(), email.getTo(), email.getSubject(),
                    email.getBody(), email.getLinks(), email.getContentHtml(), sent || read,
                    email.getCreatedAt(), email.getTransferFrom()));
        }
    }

    public record MailRow(String uid, Email email) {}
    public record Mailbox(List<MailRow> inbox, List<MailRow> sent, boolean hasReadState) {}

    public Mailbox fetchMailbox() throws IOException {
        JSONObject result = call("load", new JSONObject().put("knownUids", new JSONArray(cachedRows.keySet())));
        Map<String, CachedMessage> freshRows = new LinkedHashMap<>();
        JSONArray changed = result.getJSONArray("messages");
        JSONArray mailbox = result.optJSONArray("mailbox");
        Map<String, CachedMessage> nextCache = new LinkedHashMap<>();
        if (mailbox != null) {
            for (Object value : changed) {
                JSONObject row = (JSONObject) value;
                freshRows.put(row.getString("uid"), parseMessage(row));
            }
            for (Object value : mailbox) {
                JSONObject entry = (JSONObject) value;
                String uid = entry.getString("uid");
                CachedMessage row = freshRows.getOrDefault(uid, cachedRows.get(uid));
                if (row == null) {
                    cachedRows = Map.of();
                    throw new IOException("Danh sách thư đã thay đổi. Vui lòng thử làm mới.");
                }
                nextCache.put(uid, new CachedMessage(entry.getInt("id"), row.sent(), row.email()));
            }
        }

        Set<String> readUids = seen;
        boolean hasReadState = !result.optBoolean("readStateUnavailable");
        if (usesLocalServer() && hasReadState) {
            readUids = new HashSet<>();
            JSONArray data = result.optJSONArray("readUids");
            if (data == null) data = call("list_read", new JSONObject()).getJSONArray("uids");
            for (Object uid : data) readUids.add((String) uid);
        }
        List<MailRow> inbox = new ArrayList<>(), sent = new ArrayList<>();
        if (mailbox == null) {
            // A full refresh can contain duplicate UIDLs. Keep each server ordinal.
            for (Object value : changed) {
                JSONObject data = (JSONObject) value;
                CachedMessage row = parseMessage(data);
                String uid = data.getString("uid");
                (row.sent() ? sent : inbox).add(row.forDisplay(uid, readUids.contains(uid)));
            }
        } else {
            for (var entry : nextCache.entrySet()) {
                String uid = entry.getKey();
                CachedMessage row = entry.getValue();
                (row.sent() ? sent : inbox).add(row.forDisplay(uid, readUids.contains(uid)));
            }
        }
        cachedRows = nextCache;
        return new Mailbox(inbox, sent, hasReadState);
    }

    private static CachedMessage parseMessage(JSONObject row) {
        return new CachedMessage(row.getInt("id"), row.getBoolean("sent"), Email.fromJSON(row.toString()));
    }

    @Override public void loadMails(MailView view) throws IOException {
        view.applyMailbox(fetchMailbox());
    }

    public void setRead(String uid, boolean read) throws IOException {
        if (usesLocalServer()) {
            call("set_read", new JSONObject().put("uid", uid).put("read", read));
        } else if (read) {
            seen.add(uid);
        } else {
            seen.remove(uid);
        }
    }

    public record Draft(String id, String to, String subject, String body, String links) {
        public boolean isEmpty() {
            return to.isBlank() && subject.isBlank() && body.isBlank() && links.isBlank();
        }

        public boolean canAutosave() {
            return to.codePointCount(0, to.length()) <= 2048
                    && subject.codePointCount(0, subject.length()) <= 1024
                    && body.codePointCount(0, body.length()) <= 65536
                    && links.codePointCount(0, links.length()) <= 2048;
        }

        @Override public String toString() {
            return (subject.isBlank() ? "(Không có tiêu đề)" : subject) + (to.isBlank() ? "" : " — " + to);
        }
    }

    public List<Draft> listDrafts() throws IOException {
        List<Draft> drafts = new ArrayList<>();
        for (Object value : call("list_drafts", new JSONObject()).getJSONArray("drafts")) {
            JSONObject row = (JSONObject) value;
            drafts.add(new Draft(row.getString("id"), row.getString("to"), row.getString("subject"),
                    row.getString("body"), row.getString("links")));
        }
        return drafts;
    }

    public void saveDraft(Draft draft) throws IOException {
        call("save_draft", new JSONObject().put("id", draft.id()).put("to", draft.to())
                .put("subject", draft.subject()).put("body", draft.body()).put("links", draft.links()));
    }

    public void deleteDraft(String id) throws IOException {
        call("delete_draft", new JSONObject().put("id", id));
    }

    public JSONObject sendDraft(Draft draft) throws IOException {
        return send(draft.to(), draft.subject(), draft.body(), draft.links());
    }

    private JSONObject send(String to, String subject, String body, String links) throws IOException {
        if (subject.isBlank() || body.isBlank()) throw new IOException("Vui lòng nhập tiêu đề và nội dung thư.");
        return call("send", new JSONObject().put("to", to).put("subject", subject).put("body", body).put("links", links));
    }

    public void finishSend(JFrame composer, MailView mailbox, JSONObject result) {
        composer.dispose();
        notifySendResult(result);
        mailbox.refreshInbox();
    }

    public void notifySendResult(JSONObject result) {
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
    }
    @Override public void sendMail(CreateMailView view) throws IOException {
        JSONObject result = send(view.recipientField.getText(), view.subjectField.getText(), view.messageArea.getText(), view.attachLink.getText());
        finishSend(view, view.mailView, result);
    }
    @Override public void deleteMail(MailView view) throws IOException {
        int index = view.getEmailList().getSelectedIndex();
        if (index < 0) throw new IOException("Select a message to delete.");
        deleteMessage(view.getSelectedMessageUid());
        view.refreshInbox();
    }
    @Override public void transferMail(TransferMailView view) throws IOException {
        String text = view.email.getBody().replace("<br/>", "\n").replaceAll("<[^>]*>", "");
        JSONObject result = send(view.emailsField.getText(), "Fwd: " + view.email.getSubject(),
                "Forwarded from " + view.email.getFrom() + "\n\n" + text, view.email.getLinks());
        finishSend(view, view.mailView, result);
    }
    public void deleteMessage(String uid) throws IOException {
        call("delete", new JSONObject().put("uid", uid));
    }

    @Override public void readMail(MailView view, Email email) throws IOException {
        String uid = view.getSelectedMessageUid();
        if (uid != null) setRead(uid, true);
    }
}
