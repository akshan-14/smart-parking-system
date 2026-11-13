package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;

/**
 * Modern dashboard KPI card matching the Tailwind aesthetic:
 * Centered category title, prominent colored metric number, subtitle,
 * and bottom-left rounded icon badge with accent tint.
 */
public class MetricCard extends RoundedPanel {

    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JLabel valueLabel;
    private final JLabel subtitleLabel;

    public MetricCard(String title, String initialValue, Icon icon, Color accentColor, String subtitle) {
        super(12, UITheme.BG_CARD, UITheme.BORDER_LIGHT);
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        setPreferredSize(new Dimension(190, 128));

        // --- TOP: Centered Metrics Info ---
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);

        titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleLabel.setForeground(new Color(100, 116, 139)); // slate-500
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        valueLabel = new JLabel(initialValue);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        valueLabel.setForeground(accentColor != null ? accentColor : UITheme.TEXT_DARK);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        subtitleLabel = new JLabel(subtitle != null ? subtitle : "");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        subtitleLabel.setForeground(new Color(100, 116, 139)); // slate-500
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        topPanel.add(titleLabel);
        topPanel.add(Box.createVerticalStrut(2));
        topPanel.add(valueLabel);
        topPanel.add(Box.createVerticalStrut(2));
        topPanel.add(subtitleLabel);

        add(topPanel, BorderLayout.CENTER);

        // --- BOTTOM: Left-aligned Icon Badge ---
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bottomRow.setOpaque(false);

        Color tintBg = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 22);
        Color tintBorder = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80);

        JPanel iconBadge = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tintBg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(tintBorder);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setOpaque(false);
        iconBadge.setPreferredSize(new Dimension(32, 32));

        iconLabel = new JLabel(icon);
        iconBadge.add(iconLabel);
        bottomRow.add(iconBadge);

        add(bottomRow, BorderLayout.SOUTH);
    }

    public void setValue(String value) {
        this.valueLabel.setText(value);
    }

    public void setSubtitle(String subtitle) {
        this.subtitleLabel.setText(subtitle);
    }
}
