package com.ronial.app.views;

import javax.swing.*;
import java.awt.*;

import static com.ronial.app.views.MailChrome.*;

/** Renders one compact mailbox row with aligned sender, content and date. */
final class MailMessageRow extends JComponent {
    private final String sender;
    private final String subject;
    private final String preview;
    private final String date;
    private final boolean unread;
    private final boolean selected;
    private final boolean focused;

    static JComponent create(String sender, String subject, String body, String date,
                             boolean unread, boolean selected, boolean focused) {
        return new MailMessageRow(sender, subject, body, date, unread, selected, focused);
    }

    private MailMessageRow(String sender, String subject, String body, String date,
                           boolean unread, boolean selected, boolean focused) {
        this.sender = sender;
        this.subject = subject;
        this.preview = body.replaceAll("\\s+", " ").strip();
        this.date = date;
        this.unread = unread;
        this.selected = selected;
        this.focused = focused;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth(), height = getHeight();
        g.setColor(selected ? ACTIVE : unread ? Color.WHITE : new Color(0xf2f6fc));
        g.fillRect(0, 0, width, height);
        g.setColor(DIVIDER);
        g.fillRect(0, height - 1, width, 1);
        if (focused) {
            g.setColor(BLUE);
            g.fillRect(0, 0, 2, height);
        }
        if (unread) {
            g.setColor(BLUE);
            g.fillOval(18, (height - 7) / 2, 7, 7);
        }

        int senderWidth = Math.min(185, Math.max(110, width / 4));
        int subjectStart = senderWidth + 44;
        g.setFont(font(unread ? Font.BOLD : Font.PLAIN, 12));
        int dateWidth = g.getFontMetrics().stringWidth(date);
        int dateStart = width - dateWidth - 18;
        g.setColor(MUTED);
        g.drawString(date, dateStart, baseline(g, height));

        g.setFont(font(unread ? Font.BOLD : Font.PLAIN, 14));
        FontMetrics metrics = g.getFontMetrics();
        int line = baseline(g, height);
        g.setColor(INK);
        g.drawString(fit(sender, metrics, senderWidth), 34, line);
        int available = dateStart - subjectStart - 18;
        String heading = fit(subject, metrics, available);
        g.drawString(heading, subjectStart, line);
        int advance = metrics.stringWidth(heading) + 7;
        if (!preview.isEmpty() && available - advance > 18) {
            g.setColor(MUTED);
            g.setFont(font(Font.PLAIN, 14));
            g.drawString(fit("—  " + preview, g.getFontMetrics(), available - advance), subjectStart + advance, line);
        }
        g.dispose();
    }

    private static int baseline(Graphics2D g, int height) {
        FontMetrics m = g.getFontMetrics();
        return (height - m.getHeight()) / 2 + m.getAscent();
    }

    private static String fit(String value, FontMetrics metrics, int width) {
        if (width < metrics.stringWidth("…")) return "";
        // Never measure more than the beginning of a long body in the renderer.
        if (value.length() < 250 && metrics.stringWidth(value) <= width) return value;
        int end = value.offsetByCodePoints(0, Math.min(220, value.codePointCount(0, value.length())));
        while (end > 0 && metrics.stringWidth(value.substring(0, end) + "…") > width) {
            end = value.offsetByCodePoints(end, -1);
        }
        return value.substring(0, end) + "…";
    }
}
