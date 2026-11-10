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

public class SignInView extends JFrame {
    public JTextField nameField;
    public JTextField emailField;
    public JPasswordField passwordField;
    private MailService mailService;

    public SignInView() {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        this.mailService = ContextProvider.get(MailService.class);
        setTitle("Đăng nhập Ya-mail");
        setSize(520, 430);
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

        boolean gmail = "gmail".equals(System.getProperty("lab3.provider"));
        JLabel titleLabel = new JLabel(gmail ? "ĐĂNG NHẬP GMAIL" : "ĐĂNG NHẬP YA-MAIL", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(35, 79, 130));
        titleLabel.setBounds(60, 65, 400, 32);
        panel.add(titleLabel);

        JLabel helpLabel = new JLabel(gmail
                ? "<html><div style='text-align:center'>Dùng Gmail đã có và mật khẩu ứng dụng Google.<br>Ya-mail không tạo tài khoản Gmail mới.</div></html>"
                : "<html><div style='text-align:center'>Nhập tên tài khoản Ya-mail hoặc địa chỉ đầy đủ.</div></html>", JLabel.CENTER);
        helpLabel.setForeground(new Color(90, 110, 135));
        helpLabel.setBounds(35, 102, 450, 44);
        panel.add(helpLabel);

        Font inputFont = new Font("Arial", Font.PLAIN, 14);

        JLabel emailLabel = new JLabel("Email:");
        emailLabel.setFont(inputFont);
        emailLabel.setBounds(35, 173, 150, 25);
        panel.add(emailLabel);

        emailField = InputComponent.createTextField(150, 120);
        emailField.setBounds(190, 168, 285, 36);
        panel.add(emailField);

        JLabel passwordLabel = new JLabel(gmail ? "Mật khẩu ứng dụng:" : "Mật khẩu:");
        passwordLabel.setFont(inputFont);
        passwordLabel.setBounds(35, 228, 150, 25);
        panel.add(passwordLabel);

        passwordField = PasswordInputComponent.createPasswordField(150, 170);
        passwordField.setBounds(190, 223, 285, 36);
        panel.add(passwordField);

        JButton registerButton = ButtonComponent.createButton("Đăng nhập");
        registerButton.setBounds(190, 291, 285, 42);
        getRootPane().setDefaultButton(registerButton);
        panel.add(registerButton);

        registerButton.addActionListener(e -> {
            String email = emailField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.error("Vui lòng nhập đầy đủ thông tin!");
            } else {
                try {
                    mailService.signInAccount(this);
                } catch (IOException ex) {
                    Toast.error(ex.getMessage());
                }
            }
        });

        add(panel);
    }
    private void backToWelcome() {
        passwordField.setText("");
        MailLaunch.launch();
        dispose();
    }
    public static void launch() {
        new SignInView().setVisible(true);
    }

    public static void launchAfterRegistration(String email) {
        SignInView signIn = new SignInView();
        signIn.setTitle("Đã tạo tài khoản Ya-mail – Đăng nhập");
        signIn.emailField.setText(email);
        signIn.setVisible(true);
        signIn.passwordField.requestFocusInWindow();
    }
}
