package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Pill-shaped segmented control (e.g. 7D / 30D / 90D / 1Y). Keyboard: Left/Right when focused. */
public class SegmentedControl extends JComponent {
    private final String[] options;
    private int selected;
    private int hover = -1;
    private final Consumer<String> onChange;

    public SegmentedControl(String[] options, int selected, Consumer<String> onChange) {
        this.options = options;
        this.selected = selected;
        this.onChange = onChange;
        setFont(UITheme.font(Font.BOLD, 12));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int h = indexAt(e.getX());
                if (h != hover) {
                    hover = h;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                select(indexAt(e.getX()));
                requestFocusInWindow();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_LEFT) select(Math.max(0, SegmentedControl.this.selected - 1));
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_RIGHT) select(Math.min(options.length - 1, SegmentedControl.this.selected + 1));
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
    }

    private int segW() {
        return 52;
    }

    private int indexAt(int x) {
        int i = (x - 3) / segW();
        return i >= 0 && i < options.length ? i : -1;
    }

    private void select(int i) {
        if (i < 0 || i == selected) return;
        selected = i;
        repaint();
        if (onChange != null) onChange.accept(options[i]);
    }

    /** Programmatically selects a segment; {@code notify} controls whether the change callback fires. */
    public void setSelectedIndex(int i, boolean notify) {
        if (i < 0 || i >= options.length || i == selected) return;
        if (notify) {
            select(i);
        } else {
            selected = i;
            repaint();
        }
    }

    public int getSelectedIndex() {
        return selected;
    }

    public String getSelected() {
        return options[selected];
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(segW() * options.length + 6, 30);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int h = getHeight();
        g2.setColor(UITheme.BG_INPUT);
        g2.fillRoundRect(0, 0, getWidth() - 1, h - 1, 8, 8);
        g2.setColor(isFocusOwner() ? UITheme.ACCENT_BORDER : UITheme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, h - 1, 8, 8);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < options.length; i++) {
            int x = 3 + i * segW();
            if (i == selected) {
                g2.setColor(UITheme.BG_HOVER);
                g2.fillRoundRect(x, 3, segW(), h - 7, 6, 6);
                g2.setColor(UITheme.BORDER_STRONG);
                g2.drawRoundRect(x, 3, segW() - 1, h - 8, 6, 6);
            } else if (i == hover) {
                g2.setColor(UITheme.BG_ELEVATED);
                g2.fillRoundRect(x, 3, segW(), h - 7, 6, 6);
            }
            g2.setColor(i == selected ? UITheme.TEXT_PRIMARY : (i == hover ? UITheme.TEXT_BODY : UITheme.TEXT_SECONDARY));
            g2.drawString(options[i], x + (segW() - fm.stringWidth(options[i])) / 2, (h + fm.getAscent() - fm.getDescent()) / 2 - 1);
        }
        g2.dispose();
    }
}
