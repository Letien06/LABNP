package com.ronial.app.views;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.mail.MailService;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import java.io.IOException;

public class SignInView extends JFrame {
    public JTextField nameField;
    public JTextField emailField;
    public JPasswordField passwordField;

    private final MailService mailService;

    public SignInView() {
        mailService = ContextProvider.get(MailService.class);
        setTitle("Đăng nhập Ya-mail");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        boolean gmail = "gmail".equals(System.getProperty("lab3.provider"));
        AuthUi ui = new AuthUi(this, "Đăng nhập", gmail
                ? "Sử dụng địa chỉ Gmail và mật khẩu ứng dụng Google."
                : "Dùng tên tài khoản Ya-mail hoặc địa chỉ email đầy đủ.");

        emailField = new JTextField(20);
        passwordField = new JPasswordField(20);
        ui.field(gmail ? "Địa chỉ Gmail" : "Tên tài khoản hoặc email", emailField, null);
        ui.field(gmail ? "Mật khẩu ứng dụng" : "Mật khẩu", passwordField, null);

        JButton back = AuthUi.button("Quay lại", false);
        back.setName("backToWelcome");
        back.setToolTipText("Về màn hình bắt đầu (Esc)");
        back.addActionListener(e -> backToWelcome());
        ui.backAction(this::backToWelcome);

        JButton submit = AuthUi.button("Đăng nhập", true);
        submit.addActionListener(e -> {
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
        ui.actions(back, submit);
        ui.finish();
    }

    private void backToWelcome() {
        passwordField.setText("");
        MailLaunch.launch();
        dispose();
    }

    public static void launch() {
        SignInView signIn = new SignInView();
        signIn.setVisible(true);
        signIn.emailField.requestFocusInWindow();
    }

    public static void launchAfterRegistration(String email) {
        SignInView signIn = new SignInView();
        signIn.setTitle("Đã tạo tài khoản Ya-mail – Đăng nhập");
        signIn.emailField.setText(email);
        signIn.setVisible(true);
        signIn.passwordField.requestFocusInWindow();
    }
}
