package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;

/**
 * Card surface: anti-aliased rounded background with an optional 1px outline.
 * Default = {@link UITheme#BG_CARD} with {@link UITheme#BORDER_SUBTLE} outline.
 */
public class RoundedPanel extends JPanel {
    private int cornerRadius;
    private Color backgroundColor;
    private Color borderColor;

    public RoundedPanel(int radius, Color bgColor, Color borderColor) {
        super();
        this.cornerRadius = radius;
        this.backgroundColor = (bgColor != null) ? bgColor : UITheme.BG_CARD;
        this.borderColor = borderColor;
        setOpaque(false);
    }

    public RoundedPanel(int radius, Color bgColor) {
        this(radius, bgColor, UITheme.BORDER_SUBTLE);
    }

    public RoundedPanel(int radius) {
        this(radius, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
    }

    /** Standard design-system card (radius 10, card surface, subtle outline, given padding). */
    public static RoundedPanel card(int padding) {
        RoundedPanel p = new RoundedPanel(UITheme.RADIUS_LG);
        p.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        return p;
    }

    public void setBackgroundColor(Color color) {
        this.backgroundColor = color;
        repaint();
    }

    public void setBorderColor(Color color) {
        this.borderColor = color;
        repaint();
    }

    @Override
    public Color getBackground() {
        // Children that inherit background (e.g. non-opaque wrappers, scroll viewports) see the card colour.
        return backgroundColor != null ? backgroundColor : super.getBackground();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        g2.setColor(backgroundColor);
        g2.fillRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius);
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
