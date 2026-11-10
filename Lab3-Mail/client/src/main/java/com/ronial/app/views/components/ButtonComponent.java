package com.ronial.app.views.components;

import javax.swing.*;
import java.awt.*;

public class ButtonComponent {
    private ButtonComponent(){}
    public static JButton createButton(String text){
        JButton registerButton = new JButton(text);
        registerButton.setBounds(150, 230, 120, 40);
        registerButton.setBackground(new Color(0, 102, 204));
        registerButton.setForeground(Color.WHITE);
        registerButton.setFont(new Font("Arial", Font.BOLD, 14));
        registerButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        registerButton.setBorder(BorderFactory.createEmptyBorder());
        registerButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                registerButton.setBackground(new Color(0, 80, 180));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                registerButton.setBackground(new Color(0, 102, 204));
            }
        });
        return registerButton;
    }
    public static void styleButton(JButton button) {
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(new Color(30, 144, 255));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}
