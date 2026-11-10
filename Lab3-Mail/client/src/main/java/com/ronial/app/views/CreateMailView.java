package com.ronial.app.views;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.mail.MailService;
import com.ronial.app.mail.ProtocolMailService;
import com.ronial.app.views.components.ButtonComponent;
import com.ronial.app.views.components.InputComponent;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
public class CreateMailView extends JFrame {
    public JTextField recipientField;
    public JTextField subjectField;
    public JTextField attachLink;
    public JTextArea messageArea;
    public MailView mailView;
    public CreateMailView(MailView mailView) {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        this.mailView = mailView;
        MailService mailService = ContextProvider.get(MailService.class);
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

        JButton sendButton = new JButton("Gửi thư");
        ButtonComponent.styleButton(sendButton);
        sendButton.addActionListener(e -> {
            try {
                mailService.sendMail(this);
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
                connect.addActionListener(e -> configureRelay(protocol, connect, status, sendButton));
                relayControls.add(connect);
            }
            relayControls.add(status);
            actions.add(relayControls, BorderLayout.CENTER);
            refreshRelayStatus(protocol, connect, status);
        }
        actions.add(sendButton, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);
        setVisible(true);
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

    private void configureRelay(ProtocolMailService protocol, JButton connect, JLabel status, JButton sendButton) {
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
        sendButton.setEnabled(false);
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
                sendButton.setEnabled(true);
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
