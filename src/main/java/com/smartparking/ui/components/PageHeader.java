package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;

import javax.swing.*;
import java.awt.*;

/**
 * Consistent screen header: title, one-line description and a right-aligned action area.
 * <pre>
 *   Overview                                   [Refresh] [Export]
 *   Live status of all levels and gates
 * </pre>
 */
public class PageHeader extends JPanel {
    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JPanel actions;

    public PageHeader(String title, String subtitle) {
        super(new BorderLayout(16, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        titleLabel = new JLabel(title);
        titleLabel.setFont(UITheme.font(Font.BOLD, 20));
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);
        subtitleLabel = new JLabel(subtitle == null ? "" : subtitle);
        subtitleLabel.setFont(UITheme.font(Font.PLAIN, 13));
        subtitleLabel.setForeground(UITheme.TEXT_SECONDARY);
        text.add(titleLabel);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitleLabel);
        add(text, BorderLayout.WEST);

        actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JPanel actionsWrap = new JPanel(new GridBagLayout());
        actionsWrap.setOpaque(false);
        actionsWrap.add(actions);
        add(actionsWrap, BorderLayout.EAST);
    }

    public PageHeader addAction(JComponent c) {
        actions.add(c);
        return this;
    }

    public void setTitle(String t) {
        titleLabel.setText(t);
    }

    public void setSubtitle(String s) {
        subtitleLabel.setText(s);
    }

    public JLabel getTitleLabel() {
        return titleLabel;
    }

    public JLabel getSubtitleLabel() {
        return subtitleLabel;
    }

    /** Small section title used inside cards. */
    public static JLabel cardTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.font(Font.BOLD, 14));
        l.setForeground(UITheme.TEXT_PRIMARY);
        return l;
    }

    /** Muted caption used under card titles. */
    public static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UITheme.font(Font.PLAIN, 12));
        l.setForeground(UITheme.TEXT_TERTIARY);
        return l;
    }
}
