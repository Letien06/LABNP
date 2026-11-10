package com.ronial.app.views;

import com.ronial.app.context.Context;
import com.ronial.app.mail.Server;
import com.ronial.app.utils.DateUtils;
import com.ronial.app.views.components.ButtonComponent;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.Objects;

public class LogFrame extends JFrame implements Context {
    private final JTextArea logArea;
    private final JButton stopButton;

    public LogFrame() {
        setTitle("Ya-mail Server Control Panel");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getResource("/assets/Email-icon.png"))).getImage());

        // Áp dụng layout chính
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(240, 240, 240));

        // Panel chứa log
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.BOLD, 16));
        logArea.setBackground(Color.WHITE);
        logArea.setForeground(Color.DARK_GRAY);
        logArea.setMargin(new Insets(10, 10, 10, 10));

        // ✅ Kích hoạt tự động xuống dòng
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        add(scrollPane, BorderLayout.CENTER);

        // Nút dừng server
        stopButton = new JButton("Stop Server");
        ButtonComponent.styleButton(stopButton);
        stopButton.setBackground(new Color(200, 0, 0));
        stopButton.setForeground(Color.WHITE);
        stopButton.setFont(new Font("Arial Unicode MS", Font.BOLD, 16));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setBackground(new Color(230, 230, 230));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        bottomPanel.add(stopButton);
        add(bottomPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    public JButton getStopButton() {
        return stopButton;
    }

    public JTextArea getLogArea() {
        return logArea;
    }

    public void addLog(Class<?> clazz, Object log) {
        String content = "[" + DateUtils.formatInstant(Instant.now()) + "] "
                + clazz.getSimpleName() + " ➜ " + log.toString() + "\n";
        System.out.println(content);
        logArea.append(content);

        // Cuộn xuống cuối khi có log mới
        SwingUtilities.invokeLater(() -> logArea.setCaretPosition(logArea.getDocument().getLength()));
    }
}
