package com.smartparking.ui.theme;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modern design system tokens for the Smart Parking Management System GUI.
 * Features a sleek enterprise dark/light palette with high contrast and readable typography.
 */
public class UITheme {

    // Brand & Theme Colors
    public static final Color PRIMARY_NAVY = new Color(15, 23, 42);      // #0F172A (Sidebar / Dark header)
    public static final Color PRIMARY_DARK = PRIMARY_NAVY;
    public static final Color TOPBAR_BG = new Color(11, 19, 41);         // #0B1329 (Header top bar)
    public static final Color SIDEBAR_BG = new Color(13, 21, 39);        // #0D1527 (Left navigation sidebar)
    public static final Color HERO_BG = new Color(15, 23, 42);           // #0F172A (Hero announcement card)
    public static final Color PRIMARY_BLUE = new Color(37, 99, 235);     // #2563EB (Accent / Primary Action)
    public static final Color PRIMARY_ACCENT = PRIMARY_BLUE;
    public static final Color PRIMARY_HOVER = new Color(29, 78, 216);    // #1D4ED8
    public static final Color SECONDARY_CYAN = new Color(14, 165, 233);  // #0EA5E9 (Tech Highlights)
    public static final Color SKY_BLUE = new Color(14, 165, 233);        // #0EA5E9
    public static final Color CARD_BG_DARK = new Color(30, 41, 59);      // #1E293B (Dark Sidebar Card)

    // Canvas & Panels
    public static final Color BG_CANVAS = new Color(241, 245, 249);      // #F1F5F9 (Main Content Canvas)
    public static final Color BG_CARD = new Color(255, 255, 255);        // #FFFFFF (Clean Cards)
    public static final Color BG_INPUT = new Color(241, 245, 249);       // #F1F5F9 (Text Fields)
    public static final Color BORDER_LIGHT = new Color(226, 232, 240);    // #E2E8F0 (Subtle dividers)

    // Status Colors (Matching standard parking conventions)
    public static final Color COLOR_AVAILABLE = new Color(16, 185, 129); // #10B981 (Emerald Green)
    public static final Color EMERALD_SUCCESS = COLOR_AVAILABLE;
    public static final Color COLOR_OCCUPIED = new Color(239, 68, 68);   // #EF4444 (Rose Red)
    public static final Color COLOR_RESERVED = new Color(245, 158, 11);  // #F59E0B (Amber Gold)
    public static final Color AMBER_GOLD = COLOR_RESERVED;
    public static final Color COLOR_MAINTENANCE = new Color(107, 114, 128); // #6B7280 (Cool Gray)

    // Text Colors
    public static final Color TEXT_DARK = new Color(15, 23, 42);         // Primary Headings
    public static final Color TEXT_MUTED = new Color(100, 116, 139);     // Secondary Descriptions
    public static final Color TEXT_LIGHT = new Color(248, 250, 252);     // On Dark Backgrounds

    // Typography
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 13);

    /**
     * Styles a standard JTextField with rounded padded styling.
     */
    public static void styleTextField(JTextField field) {
        field.setFont(FONT_REGULAR);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_DARK);
        field.setCaretColor(PRIMARY_BLUE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_LIGHT, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }

    /**
     * Styles a standard JComboBox.
     */
    public static void styleComboBox(JComboBox<?> box) {
        box.setFont(FONT_REGULAR);
        box.setBackground(BG_CARD);
        box.setForeground(TEXT_DARK);
        box.setBorder(BorderFactory.createLineBorder(BORDER_LIGHT, 1, true));
    }

    /**
     * Creates an empty padding border.
     */
    public static Border padding(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }
}
