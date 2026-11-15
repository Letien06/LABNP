package com.ronial.app.views;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;

final class AuthUi {
    private static final Color CANVAS = new Color(240, 244, 249);
    private static final Color TEXT = new Color(31, 31, 31);
    private static final Color SECONDARY = new Color(68, 71, 70);
    private static final Color BLUE = new Color(11, 87, 208);
    private static final Color OUTLINE = new Color(116, 119, 117);
    private static final Font BODY = new Font("Segoe UI", Font.PLAIN, 15);

    private final JFrame frame;
    private final JPanel card;
    private final JPanel form = column();

    AuthUi(JFrame frame, String title, String description) {
        this.frame = frame;
        URL icon = AuthUi.class.getResource("/assets/Email-icon.png");
        if (icon != null) {
            frame.setIconImage(new ImageIcon(icon).getImage());
        }

        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(CANVAS);
        background.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        frame.setContentPane(background);

        card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D graphics = (Graphics2D) g.create();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(Color.WHITE);
                graphics.fillRoundRect(0, 0, getWidth(), getHeight(), 32, 32);
                graphics.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(34, 36, 34, 36));
        background.add(card);

        JPanel heading = column();
        JLabel brand = label("Ya-mail", BODY.deriveFont(Font.BOLD, 19f), BLUE);
        JLabel headingText = label(title, BODY.deriveFont(Font.PLAIN, 34f), TEXT);
        JLabel detail = label("<html><div style='width:285px'>" + description + "</div></html>", BODY, SECONDARY);
        heading.add(brand);
        heading.add(Box.createVerticalStrut(20));
        heading.add(headingText);
        heading.add(Box.createVerticalStrut(10));
        heading.add(detail);

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = 0;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(0, 0, 0, 38);
        card.add(heading, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = 0;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.anchor = GridBagConstraints.NORTHWEST;
        card.add(form, right);
    }

    void field(String caption, JTextComponent input, String help) {
        JLabel fieldLabel = label(caption, BODY.deriveFont(Font.BOLD, 13f), TEXT);
        fieldLabel.setLabelFor(input);
        form.add(fieldLabel);
        form.add(Box.createVerticalStrut(6));

        input.setFont(BODY);
        input.setForeground(TEXT);
        input.setCaretColor(TEXT);
        input.setBorder(new AbstractBorder() {
            @Override
            public Insets getBorderInsets(Component c, Insets insets) {
                insets.set(11, 13, 11, 13);
                return insets;
            }

            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                Graphics2D graphics = (Graphics2D) g.create();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(c.hasFocus() ? BLUE : OUTLINE);
                graphics.setStroke(new BasicStroke(c.hasFocus() ? 2f : 1f));
                graphics.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 8, 8);
                graphics.dispose();
            }
        });
        input.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { input.repaint(); }
            @Override public void focusLost(FocusEvent e) { input.repaint(); }
        });
        input.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension preferred = input.getPreferredSize();
        input.setPreferredSize(new Dimension(Math.max(preferred.width, 300), preferred.height));
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        form.add(input);

        if (help != null) {
            form.add(Box.createVerticalStrut(5));
            form.add(label(help, BODY.deriveFont(13f), SECONDARY));
        }
        form.add(Box.createVerticalStrut(16));
    }

    void actions(JButton secondary, JButton primary) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (secondary != null) {
            actions.add(secondary);
        }
        actions.add(primary);
        form.add(actions);
        frame.getRootPane().setDefaultButton(primary);
    }

    void backAction(Runnable action) {
        frame.getRootPane().registerKeyboardAction(e -> action.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { action.run(); }
        });
    }

    void finish() {
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
    }

    static JButton button(String caption, boolean primary) {
        JButton button = new JButton(caption) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D graphics = (Graphics2D) g.create();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean pressed = getModel().isPressed();
                boolean rollover = getModel().isRollover();
                if (primary || pressed || rollover) {
                    graphics.setColor(primary
                            ? (pressed ? new Color(8, 64, 155) : rollover ? new Color(26, 102, 219) : BLUE)
                            : (pressed ? new Color(211, 227, 253) : new Color(232, 240, 254)));
                    graphics.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 40, 40);
                }
                if (hasFocus()) {
                    graphics.setColor(BLUE);
                    graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 40, 40);
                }
                graphics.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(BODY.deriveFont(Font.BOLD, 14f));
        button.setForeground(primary ? Color.WHITE : BLUE);
        button.setBorder(BorderFactory.createEmptyBorder(11, 20, 11, 20));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel column() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        return column;
    }
}
