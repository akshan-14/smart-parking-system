package com.smartparking.util;

import java.awt.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enterprise Internationalization (i18n) manager supporting English and Hindi (Devanagari).
 * Guarantees proper UTF-8 decoding, fallback handling, Devanagari typography,
 * and live notification when the active locale is toggled.
 */
public final class I18n {

    public static final Locale EN = Locale.ENGLISH;
    public static final Locale HI = new Locale("hi");

    private static Locale currentLocale = EN;
    private static ResourceBundle bundleEn;
    private static ResourceBundle bundleHi;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    static {
        loadBundles();
    }

    private I18n() {}

    private static void loadBundles() {
        try {
            bundleEn = loadBundleFromClasspath("i18n/messages_en.properties");
        } catch (Exception e) {
            System.err.println("[I18n] Failed to load messages_en: " + e.getMessage());
        }

        try {
            bundleHi = loadBundleFromClasspath("i18n/messages_hi.properties");
        } catch (Exception e) {
            System.err.println("[I18n] Failed to load messages_hi: " + e.getMessage());
        }
    }

    private static ResourceBundle loadBundleFromClasspath(String resourcePath) throws Exception {
        ClassLoader cl = I18n.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }

    /**
     * Gets the active Locale.
     */
    public static Locale getLocale() {
        return currentLocale;
    }

    /**
     * Checks if Hindi is currently active.
     */
    public static boolean isHindi() {
        return "hi".equalsIgnoreCase(currentLocale.getLanguage());
    }

    /**
     * Sets the active Locale and notifies all registered UI listeners to refresh.
     */
    public static void setLocale(Locale locale) {
        if (locale == null || locale.equals(currentLocale)) return;
        currentLocale = locale;
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Registers a listener to be notified when language changes.
     */
    public static void addListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /**
     * Retrieves a localized string by key with automatic English fallback.
     */
    public static String get(String key) {
        if (key == null) return "";
        ResourceBundle active = isHindi() ? bundleHi : bundleEn;
        if (active != null && active.containsKey(key)) {
            return active.getString(key);
        }
        if (bundleEn != null && bundleEn.containsKey(key)) {
            return bundleEn.getString(key);
        }
        return key;
    }

    /**
     * Retrieves a formatted localized string by key.
     */
    public static String get(String key, Object... args) {
        String pattern = get(key);
        if (args == null || args.length == 0) return pattern;
        try {
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }

    /**
     * Returns a font capable of rendering Devanagari script cleanly on Windows and other OS.
     */
    public static Font getFont(int style, float size) {
        if (isHindi()) {
            return getDevanagariFont(style, size);
        }
        return com.smartparking.ui.theme.UITheme.font(style, size);
    }

    private static Font getDevanagariFont(int style, float size) {
        String[] preferredFonts = {"Nirmala UI", "Kohinoor Devanagari", "Noto Sans Devanagari", "Devanagari Sangam MN", "Devanagari MT", "Mangal", "Kokila", "Utsaah", "Arial Unicode MS"};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> available = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));

        for (String f : preferredFonts) {
            if (available.contains(f)) {
                return new Font(f, style, (int) size);
            }
        }
        return new Font(Font.SANS_SERIF, style, (int) size);
    }
}
