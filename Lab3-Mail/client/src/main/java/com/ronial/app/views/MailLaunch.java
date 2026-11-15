package com.ronial.app.views;

import javax.swing.*;

public class MailLaunch extends JFrame {
    public MailLaunch() {
        setTitle("Ya-mail");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        boolean gmail = "gmail".equals(System.getProperty("lab3.provider"));
        AuthUi ui = new AuthUi(this, "Chào mừng", gmail
                ? "Truy cập hộp thư Ya-mail bằng tài khoản Gmail của bạn."
                : "Đăng nhập hoặc tạo tài khoản thư nội bộ @yamail.local.");

        JButton login = AuthUi.button("Đăng nhập", true);
        login.addActionListener(e -> {
            dispose();
            SignInView.launch();
        });

        JButton secondary = AuthUi.button(gmail ? "Hướng dẫn Gmail" : "Tạo tài khoản", false);
        secondary.addActionListener(e -> {
            if (gmail) {
                JOptionPane.showMessageDialog(this,
                        "Đăng nhập bằng địa chỉ Gmail và mật khẩu ứng dụng.\nBật POP trong tài khoản Gmail trước khi sử dụng.",
                        "Tài khoản Gmail", JOptionPane.INFORMATION_MESSAGE);
            } else {
                RegisterView.launch();
                dispose();
            }
        });
        ui.actions(secondary, login);
        ui.finish();
        setVisible(true);
        login.requestFocusInWindow();
    }

    public static void launch() {
        new MailLaunch();
    }
}
