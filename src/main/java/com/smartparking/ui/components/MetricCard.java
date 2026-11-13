package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;

/**
 * KPI card: caption + tinted icon badge on top, large value, contextual subtitle.
 * Values are always supplied by the caller from live data.
 */
public class MetricCard extends RoundedPanel {
    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JLabel valueLabel;
    private final JLabel subtitleLabel;

    public MetricCard(String title, String initialValue, Icon icon, Color accentColor, String subtitle) {
        super(UITheme.RADIUS_LG, UITheme.BG_CARD, UITheme.BORDER_SUBTLE);
        Color accent = accentColor != null ? accentColor : UITheme.ACCENT;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setPreferredSize(new Dimension(140, 120));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.font(Font.BOLD, 12));
        titleLabel.setForeground(UITheme.TEXT_SECONDARY);
        top.add(titleLabel, BorderLayout.WEST);

        iconLabel = new JLabel(icon);
        JComponent badge = new IconBadge(iconLabel, accent, 28);
        top.add(badge, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        valueLabel = new JLabel(initialValue);
        valueLabel.setFont(UITheme.font(Font.BOLD, 24));
        valueLabel.setForeground(UITheme.TEXT_PRIMARY);
        subtitleLabel = new JLabel(subtitle != null ? subtitle : "");
        subtitleLabel.setFont(UITheme.font(Font.PLAIN, 12));
        subtitleLabel.setForeground(UITheme.TEXT_TERTIARY);
        body.add(valueLabel);
        body.add(Box.createVerticalStrut(2));
        body.add(subtitleLabel);
        add(body, BorderLayout.CENTER);
    }

    public void setValue(String value) {
        this.valueLabel.setText(value);
    }

    public void setSubtitle(String subtitle) {
        this.subtitleLabel.setText(subtitle);
    }

    public void setSubtitleColor(Color c) {
        this.subtitleLabel.setForeground(c);
    }

    /** Rounded tinted square containing an icon. */
    public static class IconBadge extends JPanel {
        private final Color accent;

        public IconBadge(JComponent content, Color accent, int size) {
            super(new GridBagLayout());
            this.accent = accent;
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
            add(content);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UITheme.tint(accent));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(UITheme.blend(accent, UITheme.BG_CARD, 0.28));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
