package com.ronial.app.views;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.mail.MailService;
import com.ronial.app.views.utils.Toast;

import javax.swing.*;
import java.io.IOException;

public class RegisterView extends JFrame {
    public JTextField nameField;
    public JTextField emailField;
    public JPasswordField passwordField;

    private final MailService mailService;

    public RegisterView() {
        mailService = ContextProvider.get(MailService.class);
        setTitle("Đăng ký Ya-mail");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        boolean legacy = Boolean.getBoolean("lab3.legacyUdp");
        AuthUi ui = new AuthUi(this, "Tạo tài khoản", legacy
                ? "Nhập thông tin để đăng ký tài khoản Ya-mail."
                : "Tạo địa chỉ thư nội bộ @yamail.local.");

        nameField = new JTextField(20);
        emailField = new JTextField(20);
        passwordField = new JPasswordField(20);
        ui.field("Họ và tên", nameField, null);
        ui.field(legacy ? "Email" : "Tên tài khoản", emailField,
                legacy ? null : "Ví dụ: minh → minh@yamail.local");
        ui.field("Mật khẩu", passwordField, legacy ? null : "Ít nhất 6 ký tự");

        JButton back = AuthUi.button("Quay lại", false);
        back.setName("backToWelcome");
        back.setToolTipText("Về màn hình bắt đầu (Esc)");
        back.addActionListener(e -> backToWelcome());
        ui.backAction(this::backToWelcome);

        JButton submit = AuthUi.button("Đăng ký", true);
        submit.addActionListener(e -> {
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
        ui.actions(back, submit);
        ui.finish();
    }

    private void backToWelcome() {
        passwordField.setText("");
        MailLaunch.launch();
        dispose();
    }

    public static void launch() {
        RegisterView register = new RegisterView();
        register.setVisible(true);
        register.nameField.requestFocusInWindow();
    }
}
