package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;

import javax.swing.*;
import java.awt.*;

/** Centred empty / no-results state: icon in a soft circle, title and guidance text. */
public class EmptyState extends JPanel {
    private final JLabel title;
    private final JLabel message;

    public EmptyState(VectorIconUtil.IconType icon, String titleText, String messageText) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JComponent circle = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UITheme.BG_ELEVATED);
                g2.fillOval(0, 0, 48, 48);
                g2.setColor(UITheme.BORDER);
                g2.drawOval(0, 0, 47, 47);
                VectorIconUtil.getIcon(icon, 22, UITheme.TEXT_SECONDARY).paintIcon(this, g2, 13, 13);
                g2.dispose();
            }
        };
        Dimension d = new Dimension(48, 48);
        circle.setPreferredSize(d);
        circle.setMaximumSize(d);
        circle.setAlignmentX(Component.CENTER_ALIGNMENT);

        title = new JLabel(titleText, SwingConstants.CENTER);
        title.setFont(UITheme.font(Font.BOLD, 14));
        title.setForeground(UITheme.TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        message = new JLabel("<html><div style='text-align:center;width:230px'>" + escape(messageText) + "</div></html>", SwingConstants.CENTER);
        message.setFont(UITheme.font(Font.PLAIN, 12));
        message.setForeground(UITheme.TEXT_SECONDARY);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);

        col.add(circle);
        col.add(Box.createVerticalStrut(12));
        col.add(title);
        col.add(Box.createVerticalStrut(4));
        col.add(message);
        add(col);
    }

    public void setText(String titleText, String messageText) {
        title.setText(titleText);
        message.setText("<html><div style='text-align:center;width:230px'>" + escape(messageText) + "</div></html>");
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
