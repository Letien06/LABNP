package com.ronial.app.views;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class MailLaunch extends JFrame {
    public MailLaunch() {
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());
        setTitle("Ya-mail");
        setSize(500, 270);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 2, 20, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 28, 24, 28));
        buttonPanel.setOpaque(false);

        JButton btnLogin = new JButton("Đăng nhập");
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.setBackground(new Color(45, 105, 180));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setFocusPainted(false);
        btnLogin.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btnLogin.addActionListener(e -> {
            dispose();
            SignInView.launch();
        });

        boolean gmail = "gmail".equals(System.getProperty("lab3.provider"));
        JButton btnRegister = new JButton(gmail ? "Hướng dẫn Gmail" : "Đăng ký Ya-mail");
        btnRegister.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRegister.setBackground(new Color(90, 110, 135));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFocusPainted(false);
        btnRegister.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btnRegister.addActionListener(e -> {
            if (!gmail) {
                RegisterView.launch();
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Đăng nhập bằng địa chỉ Gmail và mật khẩu ứng dụng.\nBật POP trong tài khoản Gmail trước khi sử dụng.",
                        "Tài khoản Gmail", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        buttonPanel.add(btnLogin);
        buttonPanel.add(btnRegister);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(new Color(247, 249, 252));
        JLabel brand = new JLabel("Ya-mail", JLabel.CENTER);
        brand.setFont(new Font("Segoe UI", Font.BOLD, 24)); brand.setForeground(new Color(35, 79, 130));
        JPanel heading = new JPanel(new BorderLayout(0, 8));
        heading.setOpaque(false);
        heading.setBorder(BorderFactory.createEmptyBorder(16, 12, 8, 12));
        heading.add(brand, BorderLayout.NORTH);
        JLabel modeLabel = new JLabel(gmail ? "Gmail • Dùng tài khoản Google đã có" : "Tạo tài khoản @yamail.local hoặc đăng nhập", JLabel.CENTER);
        modeLabel.setForeground(new Color(90, 110, 135));
        heading.add(modeLabel, BorderLayout.CENTER);
        mainPanel.add(heading, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);

        add(mainPanel);
        setVisible(true);
    }
    public static void launch(){
        new MailLaunch();
    }
}
