package com.ronial.app.views;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.mail.MailService;
import com.ronial.app.models.Email;
import com.ronial.app.views.components.ButtonComponent;
import com.ronial.app.views.components.InputComponent;
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
        MailService mailService = ContextProvider.get(MailService.class);
        setTitle("Transfer mail - " + email.getSubject());
        setSize(800, 200);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel composePanel = new JPanel(new GridLayout(2, 1, 5, 5));
        composePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Font font = new Font("Arial", Font.PLAIN, 19);

        emailsField = InputComponent.createTextField();
        emailsField.setFont(font);


        composePanel.add(new JLabel("Danh sách email cần chuyển tiếp ( dùng dấu , để ngăn cách):"));
        composePanel.add(emailsField);

        add(composePanel, BorderLayout.NORTH);

        JButton sendButton = new JButton("Gửi");
        ButtonComponent.styleButton(sendButton);
        sendButton.addActionListener(e -> {
            try {
                mailService.transferMail(this);
            } catch (IOException ex) {
                Toast.error(ex.getMessage());
            }
        });

        add(sendButton, BorderLayout.SOUTH);
        setVisible(true);
    }
}