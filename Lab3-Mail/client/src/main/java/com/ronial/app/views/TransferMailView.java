package com.ronial.app.views;

import com.ronial.app.mail.MailService;
import com.ronial.app.models.Email;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class TransferMailView extends JFrame {
    public JTextField emailsField;
    public Email email;
    public MailView mailView;
    public TransferMailView(Email email, MailView mailView) {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        this.mailView = mailView;
        this.email = email;
        MailService mailService = mailView.getMailService();
        setTitle("Chuyển tiếp thư - " + email.getSubject());
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(0xdadce0)));
        setContentPane(card);
        card.add(CreateMailView.composerHeader(this, "Chuyển tiếp thư"), BorderLayout.NORTH);

        JPanel composePanel = new JPanel(new BorderLayout(0, 10));
        composePanel.setOpaque(false);
        composePanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 10, 16));
        emailsField = new JTextField();
        emailsField.setToolTipText("Phân tách nhiều địa chỉ bằng dấu phẩy");
        composePanel.add(CreateMailView.fieldRow("Đến", emailsField), BorderLayout.NORTH);

        JLabel subject = new JLabel();
        subject.putClientProperty("html.disable", true);
        subject.setText(email.getSubject());
        subject.setToolTipText(email.getSubject());
        subject.setForeground(new Color(0x5f6368));
        composePanel.add(subject, BorderLayout.CENTER);

        JButton sendButton = CreateMailView.primaryButton("Gửi");
        sendButton.addActionListener(e -> {
            try {
                mailService.transferMail(this);
            } catch (IOException ex) {
                Toast.error(ex.getMessage());
            }
        });

        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        actions.add(sendButton, BorderLayout.WEST);
        composePanel.add(actions, BorderLayout.SOUTH);
        card.add(composePanel, BorderLayout.CENTER);
        getRootPane().setDefaultButton(sendButton);
        CreateMailView.anchorComposer(this, mailView, new Dimension(560, 180));
        setVisible(true);
        emailsField.requestFocusInWindow();
    }
}
