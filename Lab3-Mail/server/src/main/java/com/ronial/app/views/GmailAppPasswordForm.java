package com.ronial.app.views;

import com.ronial.app.MailServerApplication;
import com.ronial.app.infrastructure.mail.MailConf;
import com.ronial.app.views.components.InputComponent;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class GmailAppPasswordForm extends JFrame {
    public GmailAppPasswordForm() {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        setTitle("Nhập App Password Gmail");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(null);

        JLabel titleLabel = new JLabel("Nhập App Password Gmail");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setBounds(50, 20, 300, 30);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel);

        JTextField passwordField = InputComponent.createTextField(50, 70);
        add(passwordField);

        JButton submitButton = new JButton("Xác nhận");
        submitButton.setBounds(50, 130, 300, 40);
        submitButton.setFont(new Font("Arial", Font.BOLD, 14));
        submitButton.setBackground(new Color(30, 144, 255));
        submitButton.setForeground(Color.WHITE);
        submitButton.setFocusPainted(false);
        submitButton.setBorder(BorderFactory.createEmptyBorder());
        submitButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        add(submitButton);

        JLabel resultLabel = new JLabel("");
        resultLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        resultLabel.setForeground(Color.RED);
        resultLabel.setBounds(50, 180, 300, 30);
        resultLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(resultLabel);

        submitButton.addActionListener(e -> {
            String password = passwordField.getText();
            if (password.isEmpty()) {
                resultLabel.setText(" Vui lòng nhập mật khẩu!");
            } else {
                String email = JOptionPane.showInputDialog(this, "Địa chỉ Gmail gửi thư:", System.getenv("YAMAIL_RELAY_USER"));
                if (email == null || email.isBlank()) {
                    return;
                }
                try {
                    dispose();
                    MailServerApplication.start(new MailConf.AuthenticationProps(email.trim(), password));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        setVisible(true);
    }

}
