package com.ronial.app.views.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class InputComponent {
    private InputComponent(){}
    public static JTextField createTextField(int x, int y) {
        JTextField textField = createTextField();
        textField.setBounds(x, y, 300, 40);
        return textField;
    }
    public static JTextField createTextField() {
        JTextField textField = new JTextField();
        textField.setFont(new Font("Arial", Font.PLAIN, 14));
        textField.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        textField.setBackground(Color.WHITE);
        textField.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                textField.setBorder(BorderFactory.createLineBorder(new Color(0, 102, 204), 2));
            }
            public void focusLost(FocusEvent e) {
                textField.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
            }
        });
        return textField;
    }
}
