package com.ronial.app.views;

import com.ronial.app.models.Email;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MailChrome {
    static final Color CANVAS = new Color(0xf6f8fc);
    static final Color SEARCH = new Color(0xe9eef6);
    static final Color INK = new Color(0x202124);
    static final Color MUTED = new Color(0x5f6368);
    static final Color BLUE = new Color(0x0b57d0);
    static final Color ACTIVE = new Color(0xd3e3fd);
    static final Color COMPOSE = new Color(0xc2e7ff);
    static final Color DIVIDER = new Color(0xe8eaed);

    enum Symbol { MENU, BRAND, COMPOSE, SEARCH, INBOX, SENT, DRAFT, REFRESH, BACK, FORWARD, DELETE, UNREAD, CLOSE, MAIL }

    private static final Pattern ENTITIES = Pattern.compile("&(?:amp|lt|gt|quot|#x27|#39|nbsp);");
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_BREAK = Pattern.compile("<br\\s*/?>|</(?:p|li|div)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("</?[A-Za-z][^>]*>");

    private MailChrome() {}

    static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JPanel rounded(LayoutManager layout, Color color, int arc) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBackground(color);
        return panel;
    }

    static JButton button(String text, Symbol symbol, String tooltip) {
        JButton button = new JButton(text, symbol == null ? null : icon(symbol, 18, MUTED));
        button.putClientProperty("html.disable", true);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("FlatLaf.style",
                "arc: 32; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1; "
                        + "background: #00000000; hoverBackground: #e8edf5; pressedBackground: #dbe4f1; "
                        + "focusedBackground: #e8edf5; margin: 7,10,7,10");
        button.setFont(font(Font.PLAIN, 14));
        button.setForeground(INK);
        button.setIconTextGap(12);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (tooltip != null) {
            button.setToolTipText(tooltip);
            button.getAccessibleContext().setAccessibleName(tooltip);
        }
        return button;
    }

    static Icon icon(Symbol symbol, int size, Color color) {
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }

            @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.scale(size / 24.0, size / 24.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c != null && !c.isEnabled() ? new Color(0x9aa0a6) : color);
                g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (symbol) {
                    case MENU -> { path(g, 4, 6, 20, 6); path(g, 4, 12, 20, 12); path(g, 4, 18, 20, 18); }
                    case BRAND -> {
                        g.setStroke(new BasicStroke(3.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.setColor(new Color(0x4285f4)); path(g, 3, 19, 3, 5, 12, 12);
                        g.setColor(new Color(0xea4335)); path(g, 3, 5, 12, 12, 21, 5);
                        g.setColor(new Color(0x34a853)); path(g, 21, 5, 21, 19);
                    }
                    case COMPOSE -> { path(g, 4, 20, 5, 15, 16, 4, 20, 8, 9, 19, 4, 20); path(g, 14, 6, 18, 10); }
                    case SEARCH -> { g.drawOval(4, 4, 11, 11); path(g, 14, 14, 20, 20); }
                    case INBOX -> path(g, 3, 13, 6, 5, 18, 5, 21, 13, 21, 20, 3, 20, 3, 13, 8, 13, 10, 16, 14, 16, 16, 13, 21, 13);
                    case SENT -> { path(g, 3, 4, 21, 12, 3, 20, 6, 12, 3, 4); path(g, 6, 12, 14, 12); }
                    case DRAFT -> { path(g, 5, 3, 14, 3, 19, 8, 19, 21, 5, 21, 5, 3); path(g, 14, 3, 14, 8, 19, 8); path(g, 8, 13, 16, 13); path(g, 8, 17, 14, 17); }
                    case REFRESH -> { g.drawArc(4, 4, 16, 16, 42, 304); path(g, 20, 4, 20, 9, 15, 9); }
                    case BACK -> { path(g, 11, 5, 4, 12, 11, 19); path(g, 4, 12, 20, 12); }
                    case FORWARD -> { path(g, 13, 5, 20, 12, 13, 19); path(g, 20, 12, 4, 12); }
                    case DELETE -> { path(g, 4, 6, 20, 6); path(g, 9, 6, 9, 3, 15, 3, 15, 6); path(g, 6, 7, 7, 21, 17, 21, 18, 7); path(g, 10, 10, 10, 17); path(g, 14, 10, 14, 17); }
                    case UNREAD -> { path(g, 3, 9, 12, 3, 21, 9, 21, 20, 3, 20, 3, 9, 12, 15, 21, 9); }
                    case CLOSE -> { path(g, 6, 6, 18, 18); path(g, 18, 6, 6, 18); }
                    case MAIL -> { g.drawRoundRect(3, 5, 18, 14, 3, 3); path(g, 3, 7, 12, 13, 21, 7); }
                }
                g.dispose();
            }
        };
    }

    private static void path(Graphics2D g, int... xy) {
        Path2D outline = new Path2D.Double();
        outline.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) outline.lineTo(xy[i], xy[i + 1]);
        g.draw(outline);
    }

    static String decode(String value) {
        Matcher matcher = ENTITIES.matcher(value == null ? "" : value);
        return matcher.replaceAll(match -> switch (match.group()) {
            case "&amp;" -> "&";
            case "&lt;" -> "<";
            case "&gt;" -> ">";
            case "&quot;" -> "\"";
            case "&nbsp;" -> " ";
            default -> "'";
        });
    }

    static String subject(String value) {
        String result = decode(value).strip();
        return result.isEmpty() ? "(Không có tiêu đề)" : result;
    }

    static String bodyText(String value) {
        if (value == null) return "";
        return decode(TAG.matcher(LINE_BREAK.matcher(value).replaceAll("\n")).replaceAll(""));
    }

    static String previewText(String value) {
        if (value != null && value.length() > 600) {
            value = value.substring(0, Character.isHighSurrogate(value.charAt(599)) ? 599 : 600);
        }
        return bodyText(value);
    }

    static String messageHtml(Email email) {
        String body = bodyText(email.getBody());
        if (email.getLinks() != null && !email.getLinks().isBlank()) {
            body += "\n\n" + email.getLinks().replace(';', '\n');
        }
        if (email.getTransferFrom() != null && !email.getTransferFrom().isBlank()) {
            body = "Chuyển tiếp từ " + decode(email.getTransferFrom()) + "\n\n" + body;
        }
        StringBuilder markup = new StringBuilder("<html><body style='font-family:Segoe UI,sans-serif; color:#202124; font-size:14px'>");
        Matcher links = LINK.matcher(body);
        int offset = 0;
        while (links.find()) {
            markup.append(escape(body.substring(offset, links.start())));
            String url = links.group();
            int end = url.length();
            while (end > 8 && ".,;!?".indexOf(url.charAt(end - 1)) >= 0) end--;
            String address = escape(url.substring(0, end));
            markup.append("<a style='color:#0b57d0' href=\"").append(address)
                    .append("\">").append(address).append("</a>").append(escape(url.substring(end)));
            offset = links.end();
        }
        return markup.append(escape(body.substring(offset))).append("</body></html>").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("\n", "<br>");
    }

    static String correspondent(String field) {
        String value = decode(field).strip();
        int angle = value.lastIndexOf('<');
        if (angle >= 0) {
            String name = value.substring(0, angle).replaceAll("^\"|\"$", "").strip();
            if (!name.isBlank()) return name;
            int end = value.indexOf('>', angle);
            if (end > angle + 1) return value.substring(angle + 1, end);
        }
        return value.isBlank() ? "Không rõ" : value;
    }

    static String initial(String name) {
        return name.isBlank() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }

    static JLabel avatar(int size) {
        JLabel mark = new JLabel("", SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0x3c6bb4));
                g.fillOval(0, (getHeight() - size) / 2, size, size);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        mark.setFont(font(Font.BOLD, size / 2));
        mark.setForeground(Color.WHITE);
        mark.setPreferredSize(new Dimension(size, size));
        return mark;
    }
}
