package lab1.chat;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/** Small shared Swing details, with no external look-and-feel dependency. */
final class ChatTheme {
  private ChatTheme() { }

  static JLabel label(String text, Font font, Color foreground) {
    JLabel label = new JLabel(text) {
      @Override public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        // Give font overhang and Windows fractional scaling some trailing room.
        return new Dimension(size.width + 14, size.height);
      }
      @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    };
    label.setFont(font);
    label.setForeground(foreground);
    return label;
  }

  static JButton button(String text, Color background, Color foreground) {
    JButton button = new JButton(text) {
      @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = getBackground();
        if (getModel().isPressed()) fill = fill.darker();
        else if (getModel().isRollover()) fill = blend(fill, foreground, .08f);
        g.setColor(fill);
        g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
        if (Color.WHITE.equals(background)) {
          g.setColor(new Color(214, 225, 238));
          g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
        }
        if (isFocusOwner()) {
          g.setColor(new Color(99, 166, 224));
          g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 13, 13);
        }
        g.dispose();
        super.paintComponent(graphics);
      }
    };
    button.setBackground(background);
    button.setForeground(foreground);
    button.setOpaque(false);
    button.setContentAreaFilled(false);
    button.setBorderPainted(false);
    button.setFocusPainted(false);
    button.setRolloverEnabled(true);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return button;
  }

  private static Color blend(Color from, Color to, float amount) {
    return new Color(Math.round(from.getRed() * (1 - amount) + to.getRed() * amount),
      Math.round(from.getGreen() * (1 - amount) + to.getGreen() * amount),
      Math.round(from.getBlue() * (1 - amount) + to.getBlue() * amount));
  }

  static void styleScrollPane(JScrollPane pane, boolean dark) {
    pane.setBorder(BorderFactory.createEmptyBorder());
    pane.setOpaque(false);
    pane.getViewport().setOpaque(false);
    JScrollBar bar = pane.getVerticalScrollBar();
    bar.setOpaque(false);
    bar.setBorder(BorderFactory.createEmptyBorder());
    bar.setPreferredSize(new Dimension(8, 0));
    bar.setUnitIncrement(18);
    bar.setUI(new BasicScrollBarUI() {
      @Override protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
      @Override protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
      private JButton emptyButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        button.setFocusable(false);
        return button;
      }
      @Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) { }
      @Override protected void paintThumb(Graphics graphics, JComponent c, Rectangle bounds) {
        if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(dark ? new Color(72, 94, 119) : new Color(197, 208, 222));
        g.fillRoundRect(bounds.x + 1, bounds.y + 2, Math.max(3, bounds.width - 2), Math.max(6, bounds.height - 4), 6, 6);
        g.dispose();
      }
    });
  }
}
