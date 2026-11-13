package com.smartparking.ui.util;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.VehicleType;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, robust utility for loading, caching, and smoothly scaling
 * image and icon assets from the classpath.
 * Gracefully provides high-quality fallback vector graphics if any asset is missing.
 */
public class ImageLoader {

    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> ICON_CACHE = new ConcurrentHashMap<>();

    /**
     * Loads an Image from resources or cache.
     * @param resourcePath Path relative to classpath (e.g. "images/logo.png" or "/images/logo.png")
     * @return BufferedImage or fallback image
     */
    public static Image loadImage(String resourcePath) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            return createFallbackImage(64, 64, "N/A", Color.GRAY);
        }

        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        if (IMAGE_CACHE.containsKey(normalized)) {
            return IMAGE_CACHE.get(normalized);
        }

        try {
            // First attempt: ClassLoader getResourceAsStream
            InputStream in = ImageLoader.class.getClassLoader().getResourceAsStream(normalized);
            if (in != null) {
                BufferedImage img = ImageIO.read(in);
                if (img != null) {
                    IMAGE_CACHE.put(normalized, img);
                    return img;
                }
            }

            // Second attempt: ClassLoader getResource URL
            URL url = ImageLoader.class.getClassLoader().getResource(normalized);
            if (url != null) {
                BufferedImage img = ImageIO.read(url);
                if (img != null) {
                    IMAGE_CACHE.put(normalized, img);
                    return img;
                }
            }

            // Third attempt: Direct file fallback for development
            java.io.File localFile = new java.io.File("src/main/resources/" + normalized);
            if (localFile.exists()) {
                BufferedImage img = ImageIO.read(localFile);
                if (img != null) {
                    IMAGE_CACHE.put(normalized, img);
                    return img;
                }
            }
        } catch (Exception e) {
            System.err.println("[ImageLoader] Warning: Could not load " + normalized + ": " + e.getMessage());
        }

        // Return fallback image
        Image fallback = createFallbackImage(64, 64, normalized.substring(normalized.lastIndexOf('/') + 1), new Color(99, 102, 241));
        IMAGE_CACHE.put(normalized, fallback);
        return fallback;
    }

    /**
     * Loads and smoothly scales an ImageIcon.
     */
    public static ImageIcon loadIcon(String resourcePath, int width, int height) {
        String cacheKey = resourcePath + "@" + width + "x" + height;
        if (ICON_CACHE.containsKey(cacheKey)) {
            return ICON_CACHE.get(cacheKey);
        }

        Image original = loadImage(resourcePath);
        if (original == null) {
            return new ImageIcon(createFallbackImage(width, height, "?", Color.GRAY));
        }

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(original, 0, 0, width, height, null);
        g2.dispose();

        ImageIcon icon = new ImageIcon(scaled);
        ICON_CACHE.put(cacheKey, icon);
        return icon;
    }

    /**
     * Retrieves icon for specific VehicleType.
     */
    public static ImageIcon getVehicleIcon(VehicleType type, int width, int height) {
        if (type == null) return loadIcon("icons/vehicles/car.png", width, height);
        return switch (type) {
            case MOTORCYCLE, EV_TWO_WHEELER, BIKE -> loadIcon("icons/vehicles/motorcycle.png", width, height);
            case AUTO_RICKSHAW -> loadIcon("icons/vehicles/car.png", width, height);
            case EV_CAR, ELECTRIC_CAR -> loadIcon("icons/vehicles/electric_car.png", width, height);
            case LCV, HCV, TRUCK -> loadIcon("icons/vehicles/truck.png", width, height);
            default -> loadIcon("icons/vehicles/car.png", width, height);
        };
    }

    /**
     * Retrieves icon for PaymentMethodType.
     */
    public static ImageIcon getPaymentIcon(PaymentMethodType type, int width, int height) {
        if (type == null) return loadIcon("icons/payments/cash.png", width, height);
        return switch (type) {
            case CARD -> loadIcon("icons/payments/card.png", width, height);
            case UPI -> loadIcon("icons/payments/upi.png", width, height);
            case WALLET -> loadIcon("icons/payments/wallet.png", width, height);
            default -> loadIcon("icons/payments/cash.png", width, height);
        };
    }

    /**
     * Retrieves icon for parking slot status.
     */
    public static ImageIcon getSlotStatusIcon(String status, int size) {
        String path = switch (status != null ? status.toUpperCase() : "AVAILABLE") {
            case "OCCUPIED" -> "icons/slots/slot_occupied.png";
            case "RESERVED" -> "icons/slots/slot_reserved.png";
            case "OUT_OF_SERVICE" -> "icons/slots/slot_maintenance.png";
            default -> "icons/slots/slot_available.png";
        };
        return loadIcon(path, size, size);
    }

    /**
     * Retrieves dashboard metric icon.
     */
    public static ImageIcon getDashboardIcon(String name, int size) {
        return loadIcon("icons/dashboard/" + name + ".png", size, size);
    }

    /**
     * Retrieves navigation icon.
     */
    public static ImageIcon getNavIcon(String name, int size) {
        return loadIcon("icons/navigation/" + name + ".png", size, size);
    }

    /**
     * Retrieves auth icon.
     */
    public static ImageIcon getAuthIcon(String name, int size) {
        return loadIcon("icons/auth/" + name + ".png", size, size);
    }

    private static BufferedImage createFallbackImage(int width, int height, String label, Color bgColor) {
        BufferedImage img = new BufferedImage(Math.max(16, width), Math.max(16, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(bgColor != null ? bgColor : new Color(99, 102, 241));
        g2.fillRoundRect(2, 2, width - 4, height - 4, 8, 8);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, height / 3)));
        String text = (label != null && !label.isEmpty()) ? label.substring(0, Math.min(3, label.length())).toUpperCase() : "?";
        FontMetrics fm = g2.getFontMetrics();
        int tx = (width - fm.stringWidth(text)) / 2;
        int ty = (height + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, tx, ty);

        g2.dispose();
        return img;
    }
}
