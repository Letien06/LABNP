package com.ronial.app.views;

import com.ronial.app.mail.MailService;
import com.ronial.app.mail.ProtocolMailService;
import com.ronial.app.views.components.ButtonComponent;
import com.ronial.app.views.components.InputComponent;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
public class CreateMailView extends JFrame {
    public JTextField recipientField;
    public JTextField subjectField;
    public JTextField attachLink;
    public JTextArea messageArea;
    public MailView mailView;
    private ProtocolMailService draftService;
    private ProtocolMailService.Draft savedDraft;
    private String draftId;
    private Timer autosave;
    private JLabel draftStatus;
    private JButton sendButton, discardButton;
    private boolean draftBusy, closing, relayBusy, sent;
    private boolean sending, discarding;

    public CreateMailView(MailView mailView) {
        this(mailView, null);
    }

    CreateMailView(MailView mailView, ProtocolMailService.Draft restored) {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        this.mailView = mailView;
        MailService mailService = mailView.getMailService();
        setTitle("Soạn mail - " + mailView.getUser().getName() + " - "+ mailView.getUser().getEmail());
        setSize(1000, 800);
        setLayout(new BorderLayout(0, 8));
        getContentPane().setBackground(new Color(247, 249, 252));
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel composePanel = new JPanel(new GridBagLayout());
        composePanel.setBackground(Color.WHITE);
        composePanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(220,226,234)), BorderFactory.createEmptyBorder(16,18,12,18)));
        GridBagConstraints gc = new GridBagConstraints(); gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1; gc.insets = new Insets(4,4,4,4);

        Font font = new Font("Arial Unicode MS", Font.PLAIN, 19);

        recipientField = InputComponent.createTextField();
        recipientField.setFont(font);

        subjectField = InputComponent.createTextField();
        subjectField.setFont(font);

        attachLink = InputComponent.createTextField();
        attachLink.setFont(font);

        messageArea = new JTextArea();
        messageArea.setFont(new Font("Arial Unicode MS", Font.PLAIN, 20));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);

        gc.gridx=0; gc.gridy=0; composePanel.add(new JLabel("Đến"), gc); gc.gridy=1; composePanel.add(recipientField, gc);
        gc.gridy=2; composePanel.add(new JLabel("Tiêu đề"), gc); gc.gridy=3; composePanel.add(subjectField, gc);
        gc.gridy=4; composePanel.add(new JLabel("Liên kết đính kèm (phân tách bằng ; )"), gc); gc.gridy=5; composePanel.add(attachLink, gc);

        add(composePanel, BorderLayout.NORTH);
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        add(messageScrollPane, BorderLayout.CENTER);

        sendButton = new JButton("Gửi thư");
        ButtonComponent.styleButton(sendButton);
        sendButton.addActionListener(e -> {
            try {
                if (draftService == null) mailService.sendMail(this);
                else sendCurrentDraft();
            } catch (IOException ex) {
                Toast.error(ex.getMessage());
            }
        });

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 10));
        actions.setOpaque(false);
        if (mailService instanceof ProtocolMailService protocol && protocol.usesLocalServer()) {
            JPanel relayControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            relayControls.setOpaque(false);
            JButton connect = new JButton("Kết nối Gmail");
            JLabel status = new JLabel("Đang kiểm tra kết nối Gmail…");
            if (protocol.canConfigureGmailRelay()) {
                connect.setToolTipText("Dùng Gmail của bạn để gửi thư đến địa chỉ bên ngoài Ya-mail");
                connect.addActionListener(e -> configureRelay(protocol, connect, status));
                relayControls.add(connect);
            }
            relayControls.add(status);
            actions.add(relayControls, BorderLayout.CENTER);
            initDrafts(protocol, restored);
            JPanel draftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            draftControls.setOpaque(false);
            draftControls.add(discardButton);
            draftControls.add(draftStatus);
            actions.add(draftControls, BorderLayout.SOUTH);
            refreshRelayStatus(protocol, connect, status);
        }
        actions.add(sendButton, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);
        setVisible(true);
    }

    private void initDrafts(ProtocolMailService protocol, ProtocolMailService.Draft restored) {
        draftService = protocol;
        draftId = restored == null ? UUID.randomUUID().toString() : restored.id();
        savedDraft = restored;
        if (restored != null) {
            recipientField.setText(restored.to());
            subjectField.setText(restored.subject());
            messageArea.setText(restored.body());
            attachLink.setText(restored.links());
        }

        draftStatus = new JLabel(restored == null ? "" : "Đã lưu thư nháp");
        discardButton = new JButton("Bỏ thư nháp");
        discardButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Bỏ thư nháp này?", "Thư nháp",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            discarding = true;
            autosave.stop();
            advanceDraft();
        });
        autosave = new Timer(800, e -> advanceDraft());
        autosave.setRepeats(false);
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { scheduleSave(); }
            @Override public void removeUpdate(DocumentEvent e) { scheduleSave(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSave(); }
        };
        recipientField.getDocument().addDocumentListener(listener);
        subjectField.getDocument().addDocumentListener(listener);
        messageArea.getDocument().addDocumentListener(listener);
        attachLink.getDocument().addDocumentListener(listener);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (sending || discarding) return;
                closing = true;
                autosave.stop();
                advanceDraft();
            }
        });
        mailView.registerComposer(draftId, this);
    }

    @Override public void dispose() {
        if (draftService != null) {
            autosave.stop();
            mailView.releaseComposer(draftId);
        }
        super.dispose();
    }

    private void scheduleSave() {
        draftStatus.setText("Có thay đổi chưa lưu");
        autosave.restart();
    }

    private ProtocolMailService.Draft currentDraft() {
        return new ProtocolMailService.Draft(draftId, recipientField.getText(), subjectField.getText(),
                messageArea.getText(), attachLink.getText());
    }

    private void sendCurrentDraft() throws IOException {
        if (!sent && (subjectField.getText().isBlank() || messageArea.getText().isBlank())) {
            throw new IOException("Vui lòng nhập tiêu đề và nội dung thư.");
        }
        sending = true;
        autosave.stop();
        advanceDraft();
    }

    private void updateDraftControls() {
        boolean locked = closing || sending || discarding || sent;
        recipientField.setEditable(!locked);
        subjectField.setEditable(!locked);
        messageArea.setEditable(!locked);
        attachLink.setEditable(!locked);
        discardButton.setEnabled(!locked);
        sendButton.setEnabled(!relayBusy && !closing && !sending && !discarding);
    }

    private void advanceDraft() {
        updateDraftControls();
        if (draftBusy) return;
        ProtocolMailService.Draft draft = currentDraft();
        boolean needsSave = !sent && !draft.equals(savedDraft) && (savedDraft != null || !draft.isEmpty());
        if (!discarding && !sending && needsSave && !draft.canAutosave()) {
            closing = false;
            draftStatus.setText("Thư nháp quá dài để tự lưu. Bạn vẫn có thể gửi thư.");
            updateDraftControls();
            return;
        }
        if (!discarding && !sending && !needsSave) {
            if (closing) dispose();
            return;
        }

        boolean discardNow = discarding;
        boolean sendNow = sending;
        boolean alreadySent = sent;
        draftBusy = true;
        draftStatus.setText(discardNow ? "Đang bỏ thư nháp…" : sendNow ? "Đang gửi…" : "Đang lưu thư nháp…");
        new SwingWorker<Void, Void>() {
            private boolean didSave;
            private JSONObject confirmation;
            private ProtocolMailService.Draft refusedDraft;

            @Override protected Void doInBackground() throws IOException {
                if (!discardNow && needsSave && draft.canAutosave()) {
                    draftService.saveDraft(draft);
                    didSave = true;
                }
                if (sendNow && !alreadySent) {
                    confirmation = draftService.sendDraft(draft);
                    JSONArray refused = confirmation.optJSONArray("refusedRecipients");
                    if (refused != null && !refused.isEmpty()) {
                        StringJoiner addresses = new StringJoiner(", ");
                        for (Object item : refused) addresses.add(((JSONObject) item).getString("email"));
                        refusedDraft = new ProtocolMailService.Draft(draft.id(), addresses.toString(),
                                draft.subject(), draft.body(), draft.links());
                        if (refusedDraft.canAutosave()) draftService.saveDraft(refusedDraft);
                        else draftService.deleteDraft(draftId);
                    }
                }
                if (discardNow || sendNow && refusedDraft == null) draftService.deleteDraft(draftId);
                return null;
            }

            @Override protected void done() {
                draftBusy = false;
                if (didSave) savedDraft = draft;
                if (confirmation != null && refusedDraft == null) sent = true;
                try {
                    get();
                    if (discardNow) {
                        dispose();
                    } else if (sendNow) {
                        if (refusedDraft != null) {
                            showRemainingRecipients(refusedDraft);
                            savedDraft = refusedDraft.canAutosave() ? refusedDraft : null;
                            draftStatus.setText(savedDraft == null
                                    ? "Thư nháp quá dài để lưu. Cửa sổ vẫn giữ các người nhận bị từ chối."
                                    : "Đã lưu thư nháp cho người nhận bị từ chối");
                            draftService.notifySendResult(confirmation);
                            mailView.refreshInbox();
                        } else if (confirmation == null) {
                            dispose();
                            mailView.refreshInbox();
                        } else {
                            draftService.finishSend(CreateMailView.this, mailView, confirmation);
                        }
                    } else {
                        draftStatus.setText(currentDraft().equals(savedDraft)
                                ? "Đã lưu thư nháp" : "Có thay đổi chưa lưu");
                        if (closing || sending || discarding || !autosave.isRunning()) advanceDraft();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    reportDraftError(ex);
                } catch (ExecutionException ex) {
                    if (refusedDraft != null) {
                        showRemainingRecipients(refusedDraft);
                        draftService.notifySendResult(confirmation);
                        mailView.refreshInbox();
                        sending = true;
                    }
                    reportDraftError(ex.getCause());
                }
            }
        }.execute();
    }

    private void showRemainingRecipients(ProtocolMailService.Draft draft) {
        sending = false;
        recipientField.setText(draft.to());
        autosave.stop();
        updateDraftControls();
    }

    private void reportDraftError(Throwable error) {
        boolean notify = closing || sending || discarding;
        closing = sending = discarding = false;
        if (sent) {
            draftStatus.setText("Đã gửi thư. Chưa xóa được thư nháp.");
            sendButton.setText("Thử xóa thư nháp");
        } else {
            draftStatus.setText(currentDraft().equals(savedDraft)
                    ? "Đã lưu thư nháp" : "Chưa lưu được thư nháp");
        }
        if (notify) Toast.error(error.getMessage());
        updateDraftControls();
    }
    private void refreshRelayStatus(ProtocolMailService protocol, JButton connect, JLabel status) {
        connect.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws IOException { return protocol.gmailRelaySender(); }

            @Override protected void done() {
                try {
                    showRelaySender(get(), protocol, connect, status);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    status.setText("Chưa kiểm tra được Gmail gửi đi");
                } finally {
                    connect.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showRelaySender(String sender, ProtocolMailService protocol, JButton connect, JLabel status) {
        if (sender.isBlank()) {
            connect.setText("Kết nối Gmail");
            status.setText(protocol.canConfigureGmailRelay()
                    ? "Gửi ra ngoài cần kết nối Gmail"
                    : "Chạy run-client.ps1 để cấu hình Gmail gửi đi");
        } else {
            connect.setText("Đổi Gmail gửi đi");
            status.setText("Gmail gửi đi: " + sender);
        }
    }

    private void configureRelay(ProtocolMailService protocol, JButton connect, JLabel status) {
        JTextField address = new JTextField(28);
        JPasswordField appPassword = new JPasswordField(28);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 6));
        fields.add(new JLabel("Nhập Gmail của bạn để gửi thư ra ngoài."));
        fields.add(new JLabel("Gmail gửi đi"));
        fields.add(address);
        fields.add(new JLabel("Mật khẩu ứng dụng Google"));
        fields.add(appPassword);
        fields.add(new JLabel("Cấu hình dùng trong phiên Ya-mail này."));
        int choice = JOptionPane.showConfirmDialog(this, fields, "Kết nối Gmail",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        String gmail = address.getText().trim();
        char[] secret = appPassword.getPassword();
        appPassword.setText("");
        if (choice != JOptionPane.OK_OPTION || gmail.isEmpty() || secret.length == 0) {
            Arrays.fill(secret, '\0');
            if (choice == JOptionPane.OK_OPTION) {
                JOptionPane.showMessageDialog(this, "Nhập Gmail gửi đi và mật khẩu ứng dụng Google.",
                        "Kết nối Gmail", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        connect.setEnabled(false);
        relayBusy = true;
        updateDraftControls();
        status.setText("Đang xác thực với Gmail…");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws IOException {
                try {
                    return protocol.configureGmailRelay(gmail, secret);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }

            @Override protected void done() {
                relayBusy = false;
                updateDraftControls();
                if (!isDisplayable()) return;
                try {
                    showRelaySender(get(), protocol, connect, status);
                    connect.setEnabled(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    refreshRelayStatus(protocol, connect, status);
                } catch (ExecutionException ex) {
                    refreshRelayStatus(protocol, connect, status);
                    JOptionPane.showMessageDialog(CreateMailView.this, ex.getCause().getMessage(),
                            "Kết nối Gmail", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
