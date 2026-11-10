package com.ronial.app.views.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class PasswordInputComponent {
    private PasswordInputComponent(){}
    public static JPasswordField createPasswordField(int x, int y) {
        JPasswordField passwordField = new JPasswordField();
        passwordField.setBounds(x, y, 200, 30);
        passwordField.setFont(new Font("Arial", Font.PLAIN, 14));
        passwordField.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        passwordField.setBackground(Color.WHITE);
        passwordField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                passwordField.setBorder(BorderFactory.createLineBorder(new Color(0, 102, 204), 2));
            }
            public void focusLost(FocusEvent e) {
                passwordField.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
            }
        });
        return passwordField;
    }
}
