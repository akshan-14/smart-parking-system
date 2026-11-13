package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Modern flat rounded JButton with hover transitions, high-contrast text,
 * icon support, and accessible hand cursor.
 */
public class ModernButton extends JButton {

    private Color normalColor;
    private Color hoverColor;
    private Color pressedColor;
    private int cornerRadius = 10;
    private boolean isHovered = false;
    private boolean isPressed = false;

    public ModernButton(String text, Icon icon, Color normalColor, Color hoverColor, Color textColor) {
        super(text, icon);
        this.normalColor = (normalColor != null) ? normalColor : UITheme.PRIMARY_BLUE;
        this.hoverColor = (hoverColor != null) ? hoverColor : UITheme.PRIMARY_HOVER;
        this.pressedColor = this.hoverColor.darker();

        setFont(UITheme.FONT_BOLD);
        setForeground((textColor != null) ? textColor : Color.WHITE);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setIconTextGap(10);
        setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });
    }

    public ModernButton(String text, Icon icon) {
        this(text, icon, UITheme.PRIMARY_BLUE, UITheme.PRIMARY_HOVER, Color.WHITE);
    }

    public ModernButton(String text) {
        this(text, null, UITheme.PRIMARY_BLUE, UITheme.PRIMARY_HOVER, Color.WHITE);
    }

    public static ModernButton createSecondary(String text, Icon icon) {
        return new ModernButton(text, icon, new Color(241, 245, 249), new Color(226, 232, 240), UITheme.TEXT_DARK);
    }

    public static ModernButton createSuccess(String text, Icon icon) {
        return new ModernButton(text, icon, UITheme.COLOR_AVAILABLE, new Color(5, 150, 105), Color.WHITE);
    }

    public static ModernButton createDanger(String text, Icon icon) {
        return new ModernButton(text, icon, UITheme.COLOR_OCCUPIED, new Color(220, 38, 38), Color.WHITE);
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    public void setHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
        this.pressedColor = (hoverColor != null) ? hoverColor.darker() : null;
    }

    public void setNormalColor(Color normalColor) {
        this.normalColor = normalColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color bg;
        if (!isEnabled()) {
            bg = new Color(203, 213, 225);
        } else if (isPressed) {
            bg = pressedColor;
        } else if (isHovered) {
            bg = hoverColor;
        } else {
            bg = normalColor;
        }

        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);

        g2.dispose();
        super.paintComponent(g);
    }
}
