package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;

/**
 * Compact status pill: tinted fill, matching outline, coloured text and optional leading dot.
 * Use {@link #forStatus(String)} for parking / ticket / reservation / payment states.
 */
public class Badge extends JComponent {
    private String text;
    private Color color;
    private final boolean dot;

    public Badge(String text, Color color, boolean dot) {
        this.text = text;
        this.color = color;
        this.dot = dot;
        setFont(UITheme.font(Font.BOLD, 11));
        setOpaque(false);
    }

    public Badge(String text, Color color) {
        this(text, color, true);
    }

    public void set(String text, Color color) {
        this.text = text;
        this.color = color;
        revalidate();
        repaint();
    }

    /** Maps common domain states to semantic colours. */
    public static Color colorFor(String status) {
        if (status == null) return UITheme.TEXT_TERTIARY;
        String s = status.trim().toUpperCase();
        if (s.contains("AVAIL") || s.contains("VACANT") || s.equals("ACTIVE") || s.contains("COMPLET") || s.contains("CONFIRM")
                || s.contains("SUCCESS") || s.contains("PAID") || s.equals("ENTRY") || s.contains("VALID") || s.equals("OK")) return UITheme.SUCCESS;
        if (s.contains("OCCUP") || s.contains("FAIL") || s.contains("LOCK") || s.contains("CANCEL") || s.contains("EXPIRED")
                || s.contains("ERROR") || s.contains("DENIED") || s.contains("OVERSTAY") || s.contains("REFUND") || s.equals("EXIT")) return UITheme.ERROR;
        if (s.contains("RESERV") || s.contains("PENDING") || s.contains("WARN") || s.contains("NO_SHOW") || s.contains("NO-SHOW")) return UITheme.WARNING;
        if (s.contains("MAINT") || s.contains("OFFLINE") || s.contains("INACTIVE") || s.contains("EXITED")) return UITheme.SLOT_MAINTENANCE;
        if (s.contains("EV") || s.contains("CHARG")) return UITheme.SLOT_EV;
        return UITheme.ACCENT;
    }

    public static Badge forStatus(String status) {
        return new Badge(status, colorFor(status), true);
    }

    /** Text colour for a semantic colour on dark surfaces. */
    public static Color textFor(Color c) {
        return UITheme.blend(c, Color.WHITE, 0.72);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text == null ? "" : text) + (dot ? 26 : 16), 22);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintBadge((Graphics2D) g, text, color, dot, getFont(), 0, (getHeight() - 22) / 2, getPreferredSize().width);
    }

    /** Paints a badge at the given location — reusable from table cell renderers and custom canvases. */
    public static void paintBadge(Graphics2D g, String text, Color color, boolean dot, Font font, int x, int y, int w) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int h = 22;
        g2.setColor(UITheme.tint(color));
        g2.fillRoundRect(x, y, w, h, h, h);
        g2.setColor(UITheme.blend(color, UITheme.BG_CARD, 0.32));
        g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
        int tx = x + 8;
        if (dot) {
            g2.setColor(color);
            g2.fillOval(x + 9, y + h / 2 - 3, 6, 6);
            tx = x + 19;
        }
        g2.setFont(font);
        g2.setColor(textFor(color));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text == null ? "" : text, tx, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        g2.dispose();
    }

    /** Table cell renderer drawing the cell value as a status badge. */
    public static class Renderer extends javax.swing.table.DefaultTableCellRenderer {
        private Object value;
        private boolean selected;
        private int row;
        private JTable table;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            this.value = value;
            this.selected = isSelected;
            this.row = row;
            this.table = table;
            setText("");
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Object hr = table.getClientProperty("hoverRow");
            boolean hover = hr instanceof Integer && (Integer) hr == row;
            g.setColor(selected ? UITheme.BG_SELECTED : hover ? UITheme.BG_HOVER : (row % 2 == 0 ? UITheme.BG_CARD : UITheme.BG_SURFACE));
            g.fillRect(0, 0, getWidth(), getHeight());
            String s = value == null ? "" : value.toString();
            if (s.isEmpty() || "—".equals(s)) {
                g.setColor(UITheme.TEXT_TERTIARY);
                g.setFont(UITheme.FONT_REGULAR);
                g.drawString(s, 10, getHeight() / 2 + 4);
                return;
            }
            Font f = UITheme.font(Font.BOLD, 11);
            int w = g.getFontMetrics(f).stringWidth(s) + 26;
            paintBadge((Graphics2D) g, s, colorFor(s), true, f, 8, (getHeight() - 22) / 2, Math.min(w, getWidth() - 12));
        }
    }
}
