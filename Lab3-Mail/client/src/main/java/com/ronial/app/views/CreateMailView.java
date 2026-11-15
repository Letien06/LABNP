package com.ronial.app.views;

import com.ronial.app.mail.MailService;
import com.ronial.app.mail.ProtocolMailService;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.MouseInputAdapter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
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
    private boolean draftBusy, closing, sent;
    private boolean sending, discarding;

    public CreateMailView(MailView mailView) {
        this(mailView, null);
    }

    CreateMailView(MailView mailView, ProtocolMailService.Draft restored) {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        this.mailView = mailView;
        MailService mailService = mailView.getMailService();
        setTitle("Soạn mail - " + mailView.getUser().getName() + " - "+ mailView.getUser().getEmail());
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(0xdadce0)));
        setContentPane(card);
        card.add(composerHeader(this, restored == null ? "Thư mới" : "Thư nháp"), BorderLayout.NORTH);

        JPanel editor = new JPanel(new BorderLayout());
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        card.add(editor, BorderLayout.CENTER);

        JPanel addressRows = new JPanel(new GridLayout(0, 1));
        addressRows.setOpaque(false);
        recipientField = new JTextField();
        subjectField = new JTextField();
        attachLink = new JTextField();
        attachLink.setToolTipText("Phân tách nhiều liên kết bằng dấu chấm phẩy");
        addressRows.add(fieldRow("Đến", recipientField));
        addressRows.add(fieldRow("Tiêu đề", subjectField));
        addressRows.add(fieldRow("Liên kết", attachLink));
        editor.add(addressRows, BorderLayout.NORTH);

        messageArea = new JTextArea();
        messageArea.getAccessibleContext().setAccessibleName("Nội dung thư");
        messageArea.setForeground(new Color(0x202124));
        messageArea.setFont(COMPOSER_FONT);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setBorder(BorderFactory.createEmptyBorder(12, 0, 6, 0));
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messageScrollPane.setBorder(BorderFactory.createEmptyBorder());
        editor.add(messageScrollPane, BorderLayout.CENTER);

        sendButton = primaryButton("Gửi");
        sendButton.addActionListener(e -> {
            try {
                if (draftService == null) mailService.sendMail(this);
                else sendCurrentDraft();
            } catch (IOException ex) {
                Toast.error(ex.getMessage());
            }
        });

        JPanel actions = new JPanel(new BorderLayout(0, 4));
        actions.setBorder(BorderFactory.createEmptyBorder(4, 16, 12, 16));
        actions.setOpaque(false);
        JPanel commandRow = new JPanel(new BorderLayout(8, 0));
        commandRow.setOpaque(false);
        commandRow.add(sendButton, BorderLayout.WEST);
        actions.add(commandRow, BorderLayout.NORTH);
        if (mailService instanceof ProtocolMailService protocol && protocol.usesLocalServer()) {
            initDrafts(protocol, restored);
            commandRow.add(discardButton, BorderLayout.EAST);
            actions.add(draftStatus, BorderLayout.SOUTH);
        }
        card.add(actions, BorderLayout.SOUTH);
        anchorComposer(this, mailView, new Dimension(560, 530));
        setVisible(true);
        recipientField.requestFocusInWindow();
    }

    private static final Font COMPOSER_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    static JPanel composerHeader(JFrame composer, String title) {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(new Color(0xedf2fa));
        header.setBorder(BorderFactory.createEmptyBorder(5, 16, 5, 10));
        JLabel caption = new JLabel(title);
        caption.setFont(COMPOSER_FONT.deriveFont(Font.BOLD));
        caption.setForeground(new Color(0x041e49));
        JButton close = textButton("×");
        close.setFont(COMPOSER_FONT.deriveFont(20f));
        close.setToolTipText("Đóng cửa sổ");
        close.getAccessibleContext().setAccessibleName("Đóng cửa sổ");
        close.addActionListener(e -> composer.dispatchEvent(
                new java.awt.event.WindowEvent(composer, java.awt.event.WindowEvent.WINDOW_CLOSING)));
        MouseInputAdapter drag = new MouseInputAdapter() {
            private Point offset;

            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                offset = event.getLocationOnScreen();
                offset.translate(-composer.getX(), -composer.getY());
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (offset != null) composer.setLocation(event.getXOnScreen() - offset.x, event.getYOnScreen() - offset.y);
            }

            @Override public void mouseReleased(MouseEvent event) {
                offset = null;
            }
        };
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);
        caption.addMouseListener(drag);
        caption.addMouseMotionListener(drag);
        header.add(caption, BorderLayout.CENTER);
        header.add(close, BorderLayout.EAST);
        return header;
    }

    static JPanel fieldRow(String title, JTextField input) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xe8eaed)),
                BorderFactory.createEmptyBorder(8, 0, 8, 0)));
        JLabel label = new JLabel(title);
        label.setFont(COMPOSER_FONT);
        label.setForeground(new Color(0x5f6368));
        label.setPreferredSize(new Dimension(62, 20));
        label.setLabelFor(input);
        input.setBorder(BorderFactory.createEmptyBorder());
        input.setFont(COMPOSER_FONT);
        input.setForeground(new Color(0x202124));
        row.add(label, BorderLayout.WEST);
        row.add(input, BorderLayout.CENTER);
        return row;
    }

    static JButton textButton(String title) {
        JButton button = new JButton(title);
        button.setFont(COMPOSER_FONT.deriveFont(13f));
        button.setForeground(new Color(0x0b57d0));
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    static JButton primaryButton(String title) {
        JButton button = new JButton(title) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D paint = (Graphics2D) graphics.create();
                paint.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color background = new Color(0x0b57d0);
                if (!isEnabled()) background = new Color(0x767d89);
                else if (getModel().isPressed()) background = new Color(0x0842a0);
                else if (getModel().isRollover()) background = new Color(0x1765da);
                paint.setColor(background);
                paint.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                if (hasFocus()) {
                    paint.setColor(Color.WHITE);
                    paint.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, getHeight() - 4, getHeight() - 4);
                }
                paint.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false);
        button.setForeground(Color.WHITE);
        button.setFont(COMPOSER_FONT.deriveFont(Font.BOLD));
        button.setBorder(BorderFactory.createEmptyBorder(9, 24, 9, 24));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JLabel statusLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(0x5f6368));
        label.setFont(COMPOSER_FONT.deriveFont(12f));
        label.addPropertyChangeListener("text", event -> label.setToolTipText(label.getText()));
        label.setToolTipText(text);
        return label;
    }

    static void anchorComposer(JFrame composer, JFrame owner, Dimension preferredSize) {
        GraphicsConfiguration configuration = owner.getGraphicsConfiguration();
        Rectangle workArea = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        workArea.x += insets.left;
        workArea.y += insets.top;
        workArea.width -= insets.left + insets.right;
        workArea.height -= insets.top + insets.bottom;
        Rectangle ownerBounds = owner.getBounds();
        int width = Math.min(preferredSize.width, workArea.width);
        int height = Math.min(preferredSize.height, workArea.height);
        int x = Math.min(ownerBounds.x + ownerBounds.width - width - 24, workArea.x + workArea.width - width);
        int y = Math.min(ownerBounds.y + ownerBounds.height - height - 12, workArea.y + workArea.height - height);
        composer.setBounds(Math.max(workArea.x, x), Math.max(workArea.y, y), width, height);
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

        draftStatus = statusLabel(restored == null ? "" : "Đã lưu thư nháp");
        discardButton = textButton("Bỏ thư nháp");
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
        sendButton.setEnabled(!closing && !sending && !discarding);
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
}
