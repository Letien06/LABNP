package com.ronial.app.views;

import com.ronial.app.mail.ProtocolMailService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static com.ronial.app.views.MailChrome.*;

final class GmailRelayDialog extends JDialog {
    private final ProtocolMailService protocol;
    private final boolean canConfigure;
    private final JTextField address = new JTextField(30);
    private final JPasswordField password = new JPasswordField(30);
    private final JTextArea status = new JTextArea(3, 30);
    private final JButton save = CreateMailView.primaryButton("Lưu kết nối");
    private final JButton close = button("Đóng", null, null);

    GmailRelayDialog(MailView owner, ProtocolMailService protocol) {
        super(owner, "Gmail gửi đi", true);
        this.protocol = protocol;
        canConfigure = protocol.canConfigureGmailRelay();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        content.setBackground(Color.WHITE);
        content.add(label("Gmail gửi đi", Font.PLAIN, 22, INK), BorderLayout.NORTH);
        JPanel fields = transparent(new GridLayout(0, 1, 0, 5));
        if (canConfigure) {
            addField(fields, "Địa chỉ Gmail", address);
            addField(fields, "Mật khẩu ứng dụng Google", password);
        }
        status.setFont(font(Font.PLAIN, 13));
        status.setForeground(MUTED);
        status.setOpaque(false);
        status.setEditable(false);
        status.setFocusable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        JPanel details = transparent(new BorderLayout(0, 12));
        if (canConfigure) details.add(fields, BorderLayout.NORTH);
        details.add(status, BorderLayout.CENTER);
        content.add(details, BorderLayout.CENTER);

        JPanel actions = transparent(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        close.addActionListener(e -> dispose());
        actions.add(close);
        if (canConfigure) {
            save.addActionListener(e -> saveConnection());
            actions.add(save);
            getRootPane().setDefaultButton(save);
        }
        getRootPane().registerKeyboardAction(e -> { if (close.isEnabled()) dispose(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        content.add(actions, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        loadStatus();
    }

    private static void addField(JPanel fields, String name, JTextField field) {
        JLabel caption = label(name, Font.PLAIN, 13, INK);
        caption.setLabelFor(field);
        fields.add(caption);
        fields.add(field);
    }

    private void setInputEnabled(boolean enabled) {
        address.setEnabled(enabled && canConfigure);
        password.setEnabled(enabled && canConfigure);
        save.setEnabled(enabled && canConfigure);
    }

    private void loadStatus() {
        setInputEnabled(false);
        status.setText("Đang kiểm tra kết nối Gmail…");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws IOException { return protocol.gmailRelaySender(); }

            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    String sender = get();
                    if (canConfigure) address.setText(sender);
                    status.setText(sender.isBlank() ? "Chưa kết nối Gmail gửi đi." : "Đang gửi thư qua " + sender);
                    if (!canConfigure) status.append("\nCần quyền quản trị server Ya-mail để đổi kết nối.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    status.setText("Chưa kiểm tra được kết nối Gmail.");
                } finally {
                    setInputEnabled(true);
                }
            }
        }.execute();
    }

    private void saveConnection() {
        String sender = address.getText().strip();
        char[] secret = password.getPassword();
        password.setText("");
        if (sender.isEmpty() || secret.length == 0) {
            Arrays.fill(secret, '\0');
            status.setText("Nhập địa chỉ Gmail và mật khẩu ứng dụng Google.");
            return;
        }
        setInputEnabled(false);
        close.setEnabled(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        status.setText("Đang xác thực với Gmail…");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws IOException {
                try {
                    return protocol.configureGmailRelay(sender, secret);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }

            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    String connected = get();
                    address.setText(connected);
                    status.setText("Đã kết nối " + connected + " cho phiên Ya-mail này.");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    status.setText("Không thể lưu kết nối. " + ex.getCause().getMessage());
                } finally {
                    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
                    setInputEnabled(true);
                    close.setEnabled(true);
                }
            }
        }.execute();
    }

    @Override public void dispose() {
        password.setText("");
        super.dispose();
    }
}
