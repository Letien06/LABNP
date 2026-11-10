package com.ronial.app.views;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.mail.MailService;
import com.ronial.app.views.components.ButtonComponent;
import com.ronial.app.views.components.InputComponent;
import com.ronial.app.views.components.PasswordInputComponent;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Objects;

public class RegisterView extends JFrame {
    public JTextField nameField;
    public JTextField emailField;
    public JPasswordField passwordField;
    private MailService mailService;

    public RegisterView() {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        this.mailService = ContextProvider.get(MailService.class);
        setTitle("Đăng ký Ya-mail");
        setSize(520, 450);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        panel.setBackground(new Color(247, 249, 252));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton backButton = new JButton("← Quay lại");
        backButton.setName("backToWelcome");
        backButton.setBounds(24, 18, 120, 32);
        backButton.setToolTipText("Về màn hình bắt đầu (Esc)");
        backButton.addActionListener(e -> backToWelcome());
        panel.add(backButton);
        getRootPane().registerKeyboardAction(e -> backToWelcome(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { backToWelcome(); }
        });

        JLabel titleLabel = new JLabel("ĐĂNG KÝ YA-MAIL NỘI BỘ", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(35, 79, 130));
        titleLabel.setBounds(55, 65, 410, 32);
        panel.add(titleLabel);

        Font inputFont = new Font("Arial", Font.PLAIN, 14);

        JLabel nameLabel = new JLabel("Họ và tên:");
        nameLabel.setFont(inputFont);
        nameLabel.setBounds(40, 133, 135, 25);
        panel.add(nameLabel);

        nameField = InputComponent.createTextField(150, 70);
        nameField.setBounds(185, 128, 290, 36);
        panel.add(nameField);

        boolean legacy = Boolean.getBoolean("lab3.legacyUdp");
        JLabel emailLabel = new JLabel(legacy ? "Email:" : "Tên tài khoản:");
        emailLabel.setFont(inputFont);
        emailLabel.setBounds(40, 188, 135, 25);
        panel.add(emailLabel);

        emailField = InputComponent.createTextField(150, 120);
        emailField.setBounds(185, 183, 290, 36);
        if (!legacy) {
            emailField.putClientProperty("JTextField.placeholderText", "Ví dụ: minh → minh@yamail.local");
        }
        panel.add(emailField);

        JLabel passwordLabel = new JLabel("Mật khẩu:");
        passwordLabel.setFont(inputFont);
        passwordLabel.setBounds(40, 243, 135, 25);
        panel.add(passwordLabel);

        passwordField = PasswordInputComponent.createPasswordField(150, 170);
        passwordField.setBounds(185, 238, 290, 36);
        if (!legacy) {
            JLabel passwordHint = new JLabel("Ít nhất 6 ký tự");
            passwordHint.setForeground(new Color(90, 110, 135));
            passwordHint.setBounds(185, 278, 290, 20);
            panel.add(passwordHint);
        }
        panel.add(passwordField);

        JButton registerButton = ButtonComponent.createButton("Đăng ký");
        registerButton.setBounds(185, 305, 290, 42);
        getRootPane().setDefaultButton(registerButton);
        panel.add(registerButton);

        registerButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.error("Vui lòng nhập đầy đủ thông tin!");
            } else {
                try {
                    mailService.registerAccount(this);
                } catch (IOException ex) {
                    Toast.error(ex.getMessage());
                }
            }
        });

        // Thêm panel vào JFrame
        add(panel);
    }
    private void backToWelcome() {
        passwordField.setText("");
        MailLaunch.launch();
        dispose();
    }
    public static void launch() {
        new RegisterView().setVisible(true);
    }
}
