package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Design-system button.
 *
 * <p>Variants (all share radius, padding, focus ring and a short hover fade):
 * <ul>
 *   <li><b>Primary</b> — {@code new ModernButton(text[, icon])}: solid indigo</li>
 *   <li><b>Secondary</b> — {@link #createSecondary}: elevated surface with 1px border</li>
 *   <li><b>Success</b> / <b>Danger</b> — {@link #createSuccess} / {@link #createDanger}: solid semantic colour</li>
 *   <li><b>Ghost</b> — {@link #createGhost}: transparent until hovered</li>
 * </ul>
 * Custom colours via the 5-arg constructor remain supported for existing screens.
 */
public class ModernButton extends JButton {
    private Color normalColor;
    private Color hoverColor;
    private Color pressedColor;
    private Color borderColor;
    private Color hoverBorderColor;
    private int cornerRadius = UITheme.RADIUS;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private float hoverMix = 0f;
    private final Timer fade;

    public ModernButton(String text, Icon icon, Color normalColor, Color hoverColor, Color textColor) {
        super(text, icon);
        this.normalColor = (normalColor != null) ? normalColor : UITheme.ACCENT;
        this.hoverColor = (hoverColor != null) ? hoverColor : UITheme.ACCENT_HOVER;
        this.pressedColor = UITheme.blend(Color.BLACK, this.normalColor, 0.18);

        setFont(UITheme.font(Font.BOLD, 13));
        setForeground((textColor != null) ? textColor : Color.WHITE);
        harmonize();
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setIconTextGap(8);
        setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));

        fade = new Timer(15, e -> {
            float target = isHovered ? 1f : 0f;
            hoverMix += (target - hoverMix) * 0.35f;
            if (Math.abs(target - hoverMix) < 0.02f) {
                hoverMix = target;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    fade.start();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                fade.start();
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

    public ModernButton(String text, Icon icon) {
        this(text, icon, UITheme.ACCENT, UITheme.ACCENT_HOVER, Color.WHITE);
    }

    /**
     * Maps ad-hoc saturated colours passed by individual screens onto the design system so the
     * whole app uses one button vocabulary:
     * <ul>
     *   <li>blue / indigo / violet solids &rarr; Primary (accent)</li>
     *   <li>red solids &rarr; Danger (soft: tinted surface, red label)</li>
     *   <li>green / teal solids &rarr; Success (soft)</li>
     *   <li>orange / amber solids &rarr; Warning (soft)</li>
     * </ul>
     * Neutral, translucent and dark colours are left untouched.
     */
    private void harmonize() {
        Color c = normalColor;
        if (c.getAlpha() < 255) return;
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        if (hsb[1] < 0.42f || hsb[2] < 0.38f) return;          // neutral / dark surfaces keep their colour
        float deg = hsb[0] * 360f;
        if (deg >= 200 && deg <= 300) {                          // blue..violet -> primary accent
            normalColor = UITheme.ACCENT;
            hoverColor = UITheme.ACCENT_HOVER;
            pressedColor = UITheme.ACCENT_PRESSED;
            setForeground(Color.WHITE);
            return;
        }
        Color sem;
        Color label;
        if (deg < 18 || deg > 330) { sem = UITheme.ERROR; label = UITheme.ERROR_TEXT; }
        else if (deg < 65) { sem = UITheme.WARNING; label = UITheme.WARNING_TEXT; }
        else if (deg < 200) { sem = UITheme.SUCCESS; label = UITheme.SUCCESS_TEXT; }
        else { return; }
        applySoft(sem, label);
    }

    private void applySoft(Color sem, Color label) {
        normalColor = UITheme.blend(sem, UITheme.BG_CARD, 0.14);
        hoverColor = UITheme.blend(sem, UITheme.BG_CARD, 0.24);
        pressedColor = UITheme.blend(sem, UITheme.BG_CARD, 0.32);
        borderColor = UITheme.blend(sem, UITheme.BG_CARD, 0.42);
        hoverBorderColor = UITheme.blend(sem, UITheme.BG_CARD, 0.62);
        setForeground(label);
    }

    /** Primary call-to-action (solid accent). Exactly one per view is recommended. */
    public static ModernButton createPrimary(String text, Icon icon) {
        return new ModernButton(text, icon);
    }

    /** Small/compact variant (tables toolbars, inline actions). */
    public ModernButton compact() {
        setFont(UITheme.font(Font.BOLD, 12));
        setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return this;
    }

    public ModernButton(String text) {
        this(text, null, UITheme.ACCENT, UITheme.ACCENT_HOVER, Color.WHITE);
    }

    /** Neutral button: elevated surface + border. */
    public static ModernButton createSecondary(String text, Icon icon) {
        ModernButton b = new ModernButton(text, icon, UITheme.BG_ELEVATED, UITheme.BG_HOVER, UITheme.TEXT_PRIMARY);
        b.borderColor = UITheme.BORDER;
        return b;
    }

    /** Positive confirmation (soft green). */
    public static ModernButton createSuccess(String text, Icon icon) {
        return new ModernButton(text, icon, new Color(22, 163, 74), UITheme.SUCCESS, Color.WHITE);
    }

    /** Destructive action (soft red); pair with a confirmation dialog. */
    public static ModernButton createDanger(String text, Icon icon) {
        return new ModernButton(text, icon, new Color(220, 38, 38), UITheme.ERROR, Color.WHITE);
    }

    /** Transparent button that only shows a surface on hover (toolbar / tertiary actions). */
    public static ModernButton createGhost(String text, Icon icon) {
        ModernButton b = new ModernButton(text, icon, new Color(0, 0, 0, 0), UITheme.BG_HOVER, UITheme.TEXT_SECONDARY);
        b.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        return b;
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = radius;
        repaint();
    }

    public void setHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
    }

    public void setNormalColor(Color normalColor) {
        this.normalColor = normalColor;
        this.pressedColor = UITheme.blend(Color.BLACK, normalColor, 0.18);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        Color bg;
        if (!isEnabled()) {
            bg = UITheme.BG_SURFACE;
        } else if (isPressed) {
            bg = pressedColor;
        } else {
            bg = mix(normalColor, hoverColor, hoverMix);
        }
        if (bg.getAlpha() > 0) {
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, cornerRadius, cornerRadius);
        }
        if (borderColor != null || !isEnabled()) {
            g2.setColor(isEnabled() ? (isHovered ? (hoverBorderColor != null ? hoverBorderColor : UITheme.BORDER_STRONG) : borderColor) : UITheme.BORDER_SUBTLE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
        } else if (bg.getAlpha() == 255) {
            // subtle top highlight gives solid buttons depth without gradients
            g2.setColor(new Color(255, 255, 255, 22));
            g2.drawLine(cornerRadius / 2, 0, w - cornerRadius / 2, 0);
        }
        if (isFocusOwner() && com.smartparking.ui.theme.DarkTheme.isFocusVisible()) {
            g2.setColor(UITheme.alpha(UITheme.ACCENT_TEXT, 170));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(1, 1, w - 3, h - 3, cornerRadius - 1, cornerRadius - 1);
        }
        g2.dispose();

        if (!isEnabled()) {
            // flat disabled text instead of BasicButtonUI's engraved effect
            Color fg = getForeground();
            setForeground(UITheme.TEXT_DISABLED);
            super.paintComponent(g);
            setForeground(fg);
        } else {
            super.paintComponent(g);
        }
    }

    private static Color mix(Color a, Color b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
}
