package com.ronial.app.views.utils;

import javax.swing.*;
import java.util.Objects;

public class Toast {
    public static void error(String message) {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(Toast.class.getResource("/assets/Email-icon.png")));
        JOptionPane.showMessageDialog(null, message, "Lỗi", JOptionPane.ERROR_MESSAGE, icon);
    }

    public static void info(String message) {
        ImageIcon icon = new ImageIcon(Objects.requireNonNull(Toast.class.getResource("/assets/Email-icon.png")));
        JOptionPane.showMessageDialog(null, message, "Thông báo", JOptionPane.INFORMATION_MESSAGE, icon);
    }
}
