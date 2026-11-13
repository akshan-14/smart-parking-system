package com.smartparking.ui.theme;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Design-system tokens for the Smart Parking Management System (dark theme).
 *
 * <p>Layers, from back to front:
 * <pre>
 *   BG_APP (#090B10)  -> window / sidebar / top bar
 *   BG_CANVAS (#0D1117) -> content canvas behind cards
 *   BG_CARD (#141A24) -> cards, tables, panels
 *   BG_ELEVATED (#1A2230) -> inputs, table headers, nested surfaces
 *   BG_HOVER (#212B3B) -> hover rows, pressed secondary buttons
 * </pre>
 * Spacing follows a 4px grid ({@link #SP_1} .. {@link #SP_8}).
 * Legacy constant names (PRIMARY_BLUE, TEXT_DARK, BG_CARD ...) are preserved so every
 * existing screen keeps compiling; their values now resolve to the dark palette.
 */
public final class UITheme {

    private UITheme() {
    }

    // ------------------------------------------------------------------ surfaces
    public static final Color BG_APP = new Color(9, 11, 16);            // #090B10
    public static final Color BG_CANVAS = new Color(13, 17, 23);        // #0D1117
    public static final Color BG_SURFACE = new Color(16, 20, 29);       // #10141D
    public static final Color BG_CARD = new Color(20, 26, 36);          // #141A24
    public static final Color BG_ELEVATED = new Color(26, 34, 48);      // #1A2230
    public static final Color BG_HOVER = new Color(33, 43, 59);         // #212B3B
    public static final Color BG_INPUT = new Color(15, 20, 28);         // #0F141C
    public static final Color BG_SELECTED = new Color(36, 39, 77);      // indigo-tinted selection

    // ------------------------------------------------------------------ borders
    public static final Color BORDER = new Color(38, 50, 68);           // #263244
    public static final Color BORDER_SUBTLE = new Color(29, 38, 52);    // #1D2634
    public static final Color BORDER_STRONG = new Color(51, 65, 90);    // #33415A
    public static final Color BORDER_LIGHT = BORDER;                    // legacy alias

    // ------------------------------------------------------------------ text
    public static final Color TEXT_PRIMARY = new Color(241, 245, 249);  // #F1F5F9
    public static final Color TEXT_BODY = new Color(203, 213, 225);     // #CBD5E1 long-form body copy
    public static final Color TEXT_SECONDARY = new Color(148, 163, 184);// #94A3B8
    public static final Color TEXT_TERTIARY = new Color(100, 116, 139); // #64748B
    public static final Color TEXT_DISABLED = new Color(71, 85, 105);   // #475569
    public static final Color TEXT_ON_ACCENT = Color.WHITE;

    // ------------------------------------------------------------------ accent (indigo)
    public static final Color ACCENT = new Color(99, 102, 241);         // #6366F1
    public static final Color ACCENT_HOVER = new Color(118, 121, 245);  // lighter on hover in dark UIs
    public static final Color ACCENT_PRESSED = new Color(79, 70, 229);  // #4F46E5
    public static final Color ACCENT_TEXT = new Color(165, 180, 252);   // #A5B4FC
    public static final Color ACCENT_SOFT = blend(ACCENT, BG_CARD, 0.16);
    public static final Color ACCENT_BORDER = blend(ACCENT, BG_CARD, 0.45);

    // ------------------------------------------------------------------ semantic
    public static final Color SUCCESS = new Color(34, 197, 94);         // #22C55E
    public static final Color SUCCESS_TEXT = new Color(74, 222, 128);   // #4ADE80
    public static final Color WARNING = new Color(245, 158, 11);        // #F59E0B
    public static final Color WARNING_TEXT = new Color(251, 191, 36);   // #FBBF24
    public static final Color ERROR = new Color(239, 68, 68);           // #EF4444
    public static final Color ERROR_TEXT = new Color(248, 113, 113);    // #F87171
    public static final Color INFO = new Color(56, 189, 248);           // #38BDF8
    public static final Color INFO_TEXT = new Color(125, 211, 252);     // #7DD3FC

    // ------------------------------------------------------------------ parking slot states
    public static final Color SLOT_AVAILABLE = SUCCESS;
    public static final Color SLOT_OCCUPIED = ERROR;
    public static final Color SLOT_RESERVED = WARNING;
    public static final Color SLOT_MAINTENANCE = new Color(100, 116, 139);
    public static final Color SLOT_EV = new Color(20, 184, 166);        // teal-500 #14B8A6
    public static final Color SLOT_EV_TEXT = new Color(45, 212, 191);
    public static final Color SLOT_ACCESSIBLE = new Color(129, 140, 248); // indigo-400

    // ------------------------------------------------------------------ chart series
    public static final Color[] CHART_SERIES = {
            ACCENT, SLOT_EV, WARNING, new Color(236, 72, 153), INFO, SUCCESS
    };

    // ------------------------------------------------------------------ legacy aliases (kept for API compatibility)
    public static final Color PRIMARY_NAVY = BG_APP;
    public static final Color PRIMARY_DARK = BG_APP;
    public static final Color TOPBAR_BG = BG_APP;
    public static final Color SIDEBAR_BG = BG_APP;
    public static final Color HERO_BG = BG_CARD;
    public static final Color PRIMARY_BLUE = ACCENT;
    public static final Color PRIMARY_ACCENT = ACCENT;
    public static final Color PRIMARY_HOVER = ACCENT_HOVER;
    public static final Color SECONDARY_CYAN = INFO;
    public static final Color SKY_BLUE = INFO;
    public static final Color CARD_BG_DARK = BG_ELEVATED;
    public static final Color COLOR_AVAILABLE = SLOT_AVAILABLE;
    public static final Color EMERALD_SUCCESS = SUCCESS;
    public static final Color COLOR_OCCUPIED = SLOT_OCCUPIED;
    public static final Color COLOR_RESERVED = SLOT_RESERVED;
    public static final Color AMBER_GOLD = WARNING;
    public static final Color COLOR_MAINTENANCE = SLOT_MAINTENANCE;
    public static final Color TEXT_DARK = TEXT_PRIMARY;
    public static final Color TEXT_MUTED = TEXT_SECONDARY;
    public static final Color TEXT_LIGHT = TEXT_PRIMARY;

    // ------------------------------------------------------------------ spacing (4px grid) & radii
    public static final int SP_1 = 4, SP_2 = 8, SP_3 = 12, SP_4 = 16, SP_5 = 20, SP_6 = 24, SP_8 = 32;
    public static final int RADIUS_SM = 6, RADIUS = 8, RADIUS_LG = 10;

    // ------------------------------------------------------------------ typography
    /** Best available UI sans family: Inter > Segoe UI Variable > Segoe UI > SF/Helvetica > Dialog. */
    public static final String FONT_FAMILY = pickFamily(
            "Inter", "Segoe UI Variable Text", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Roboto", Font.DIALOG);
    /** Best available monospace family for plates, IDs and codes. */
    public static final String MONO_FAMILY = pickFamily(
            "JetBrains Mono", "Cascadia Mono", "Cascadia Code", "Consolas", "SF Mono", "Menlo", "DejaVu Sans Mono", Font.MONOSPACED);

    public static final Font FONT_DISPLAY = font(Font.BOLD, 28);   // big KPI numbers
    public static final Font FONT_TITLE = font(Font.BOLD, 20);     // page titles
    public static final Font FONT_SUBTITLE = font(Font.BOLD, 15);  // card titles
    public static final Font FONT_BOLD = font(Font.BOLD, 13);      // labels / buttons
    public static final Font FONT_REGULAR = font(Font.PLAIN, 13);  // body
    public static final Font FONT_SMALL = font(Font.PLAIN, 12);    // helper text
    public static final Font FONT_CAPTION = font(Font.BOLD, 11);   // overlines / table headers
    public static final Font FONT_MONO = mono(Font.PLAIN, 12);

    /** UI font at the given style/size in the resolved family. */
    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, Math.round(size));
    }

    /** Monospace font at the given style/size in the resolved family. */
    public static Font mono(int style, float size) {
        return new Font(MONO_FAMILY, style, Math.round(size));
    }

    /**
     * UI font that can actually render {@code text}. The preferred family (e.g. Inter) has no
     * Devanagari or symbol glyphs, and physical fonts in Java do not fall back, so text such as
     * Hindi labels would otherwise render as empty boxes. Falls back to the logical Dialog font,
     * which uses the platform's composite fallback chain.
     */
    public static Font fontFor(String text, int style, float size) {
        Font f = font(style, size);
        if (text == null || f.canDisplayUpTo(text) == -1) return f;
        return new Font(Font.DIALOG, style, Math.round(size));
    }

    private static String pickFamily(String... candidates) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                // Font enumeration still works headless; guard anyway for exotic JREs.
            }
            Set<String> available = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String c : candidates) {
                if (available.contains(c)) return c;
            }
        } catch (Throwable ignored) {
        }
        return candidates[candidates.length - 1];
    }

    // ------------------------------------------------------------------ colour helpers
    /** Opaque mix of {@code fg} over {@code bg}; {@code amount}=0 gives bg, 1 gives fg. */
    public static Color blend(Color fg, Color bg, double amount) {
        double a = Math.max(0, Math.min(1, amount));
        return new Color(
                (int) Math.round(fg.getRed() * a + bg.getRed() * (1 - a)),
                (int) Math.round(fg.getGreen() * a + bg.getGreen() * (1 - a)),
                (int) Math.round(fg.getBlue() * a + bg.getBlue() * (1 - a)));
    }

    /** Low-emphasis opaque tint of a semantic colour on card surfaces (badges, alert rows). */
    public static Color tint(Color c) {
        return blend(c, BG_CARD, 0.14);
    }

    /** Border colour matching {@link #tint(Color)}. */
    public static Color tintBorder(Color c) {
        return blend(c, BG_CARD, 0.38);
    }

    /** Same colour with an alpha channel (0-255). */
    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    // ------------------------------------------------------------------ component styling helpers
    /** Standard input border: 1px outline + inner padding. Focus colouring is handled by {@link DarkTheme}. */
    public static Border inputBorder() {
        return BorderFactory.createCompoundBorder(
                new DarkTheme.RoundBorder(BORDER, RADIUS),
                new EmptyBorder(7, 11, 7, 11));
    }

    /** Styles a text field (or password field) to the dark input spec. */
    public static void styleTextField(JTextField field) {
        field.setFont(FONT_REGULAR);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(ACCENT_TEXT);
        field.setSelectionColor(BG_SELECTED);
        field.setSelectedTextColor(TEXT_PRIMARY);
        field.setBorder(inputBorder());
        DarkTheme.installFocusRing(field);
    }

    /** Styles a combo box to the dark input spec. */
    public static void styleComboBox(JComboBox<?> box) {
        box.setFont(FONT_REGULAR);
        box.setBackground(BG_INPUT);
        box.setForeground(TEXT_PRIMARY);
        box.setBorder(new DarkTheme.RoundBorder(BORDER, RADIUS));
    }

    /** Creates an empty padding border. */
    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }

    /** Card border: 1px subtle outline with inner padding. */
    public static Border cardBorder(int pad) {
        return BorderFactory.createCompoundBorder(
                new DarkTheme.RoundBorder(BORDER_SUBTLE, RADIUS_LG),
                new EmptyBorder(pad, pad, pad, pad));
    }
}
