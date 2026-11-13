package com.smartparking.ui.components;

import com.smartparking.ui.theme.UITheme;
import com.smartparking.ui.util.VectorIconUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Non-blocking notification shown at the bottom-right of the window for a few seconds.
 * Use for confirmations ("Tariffs saved") and recoverable errors; keep {@link JOptionPane}
 * for decisions that need an answer (destructive confirmations).
 *
 * <pre>
 *   Toast.success(component, "Pass issued", "UP32 AB 1234 · valid until 13 Dec 2026");
 * </pre>
 */
public final class Toast extends JComponent {

    public enum Kind { SUCCESS, ERROR, WARNING, INFO }

    private static final int WIDTH = 360;
    private final Kind kind;
    private final String title;
    private final String message;
    private float alpha = 0f;

    private Toast(Kind kind, String title, String message) {
        this.kind = kind;
        this.title = title;
        this.message = message;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Click to dismiss");
    }

    public static void success(Component anchor, String title, String message) { show(anchor, Kind.SUCCESS, title, message); }
    public static void error(Component anchor, String title, String message) { show(anchor, Kind.ERROR, title, message); }
    public static void warning(Component anchor, String title, String message) { show(anchor, Kind.WARNING, title, message); }
    public static void info(Component anchor, String title, String message) { show(anchor, Kind.INFO, title, message); }

    /** Shows a toast in the window that contains {@code anchor}. Safe to call from any thread. */
    public static void show(Component anchor, Kind kind, String title, String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(anchor, kind, title, message));
            return;
        }
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) return;
        JLayeredPane layer = root.getLayeredPane();
        // only one toast at a time: replace the previous one
        for (Component c : layer.getComponentsInLayer(JLayeredPane.POPUP_LAYER)) {
            if (c instanceof Toast) layer.remove(c);
        }
        Toast t = new Toast(kind, title, message);
        int h = t.preferredHeight();
        t.setBounds(layer.getWidth() - WIDTH - 24, layer.getHeight() - h - 24, WIDTH, h);
        layer.add(t, JLayeredPane.POPUP_LAYER);
        layer.repaint();
        t.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                t.dismiss(layer);
            }
        });
        Timer in = new Timer(16, null);
        in.addActionListener(e -> {
            t.alpha = Math.min(1f, t.alpha + 0.12f);
            t.repaint();
            if (t.alpha >= 1f) in.stop();
        });
        in.start();
        Timer life = new Timer(kind == Kind.ERROR ? 6000 : 3800, e -> t.dismiss(layer));
        life.setRepeats(false);
        life.start();
    }

    private void dismiss(JLayeredPane layer) {
        Timer out = new Timer(16, null);
        out.addActionListener(e -> {
            alpha = Math.max(0f, alpha - 0.12f);
            repaint();
            if (alpha <= 0f) {
                out.stop();
                layer.remove(this);
                layer.repaint();
            }
        });
        out.start();
    }

    private Color color() {
        switch (kind) {
            case SUCCESS: return UITheme.SUCCESS;
            case ERROR: return UITheme.ERROR;
            case WARNING: return UITheme.WARNING;
            default: return UITheme.ACCENT;
        }
    }

    private VectorIconUtil.IconType icon() {
        switch (kind) {
            case SUCCESS: return VectorIconUtil.IconType.CHECK;
            case ERROR: return VectorIconUtil.IconType.CROSS;
            case WARNING: return VectorIconUtil.IconType.ALERT;
            default: return VectorIconUtil.IconType.ALERT;
        }
    }

    private int preferredHeight() {
        return (message == null || message.isEmpty()) ? 52 : 70;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
        int w = getWidth(), h = getHeight();
        // soft shadow
        for (int i = 6; i > 0; i--) {
            g2.setColor(new Color(0, 0, 0, 10));
            g2.fill(new RoundRectangle2D.Float(-i + 6, -i + 8, w + i * 2 - 12, h + i * 2 - 12, 14, 14));
        }
        g2.setColor(UITheme.BG_ELEVATED);
        g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 12, 12));
        g2.setColor(UITheme.BORDER_STRONG);
        g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 12, 12));
        Color c = color();
        g2.setColor(UITheme.tint(c));
        g2.fillOval(14, h / 2 - 14, 28, 28);
        VectorIconUtil.getIcon(icon(), 14, c).paintIcon(this, g2, 21, h / 2 - 7);
        g2.setColor(UITheme.TEXT_PRIMARY);
        g2.setFont(UITheme.fontFor(title, Font.BOLD, 13));
        FontMetrics fm = g2.getFontMetrics();
        boolean two = message != null && !message.isEmpty();
        int ty = two ? 28 : (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(clip(title, fm, w - 72), 54, ty);
        if (two) {
            g2.setFont(UITheme.fontFor(message, Font.PLAIN, 12));
            g2.setColor(UITheme.TEXT_SECONDARY);
            g2.drawString(clip(message, g2.getFontMetrics(), w - 72), 54, ty + 20);
        }
        g2.dispose();
    }

    private static String clip(String s, FontMetrics fm, int max) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
