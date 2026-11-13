package com.smartparking.ui.util;

import com.smartparking.model.enums.VehicleType;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/**
 * Universal, high-resolution vector icon renderer for the Smart Parking System UI.
 * Replaces all emoji, font-glyph, and external CDN dependencies with 100% self-contained,
 * anti-aliased Java 2D vector graphics inspired by the Lucide / Heroicons design standard.
 */
public final class VectorIconUtil {

    public enum IconType {
        CHECK,
        CROSS,
        CLOCK,
        WRENCH,
        WHEELCHAIR,
        PLUG,
        CAR,
        TRUCK,
        MOTORCYCLE,
        AUTO_RICKSHAW,
        SEARCH,
        DICE,
        PRINT,
        DOWNLOAD,
        EYE,
        REFUND,
        ALERT,
        USER,
        LOGOUT,
        MAP,
        PLUS,
        MINUS,
        FIT_SCREEN,
        PHONE,
        CALENDAR,
        GLOBE,
        DASHBOARD,
        TICKET,
        PAYMENT,
        MEMBERSHIP,
        REPORTS,
        ARROW_RIGHT,
        ARROW_LEFT,
        REFRESH,
        GRID,
        SETTINGS,
        BELL,
        LIGHTNING,
        SHIELD,
        USERS,
        DOCUMENT
    }

    private VectorIconUtil() {}

    /**
     * Creates a vector icon with specified dimensions and color.
     */
    public static Icon getIcon(IconType type, int size, Color color) {
        return new VectorIcon(type, size, size, color);
    }

    /**
     * Creates a vector icon with specified width, height, and color.
     */
    public static Icon getIcon(IconType type, int width, int height, Color color) {
        return new VectorIcon(type, width, height, color);
    }

    /**
     * Returns a crisp vector icon for a given vehicle type.
     */
    public static Icon getVehicleIcon(VehicleType type, int size, Color color) {
        if (type == null) return getIcon(IconType.CAR, size, color);
        return switch (type) {
            case MOTORCYCLE, BIKE, EV_TWO_WHEELER -> getIcon(IconType.MOTORCYCLE, size, color);
            case AUTO_RICKSHAW -> getIcon(IconType.AUTO_RICKSHAW, size, color);
            case EV_CAR, ELECTRIC_CAR -> getIcon(IconType.PLUG, size, color);
            case LCV, HCV, TRUCK -> getIcon(IconType.TRUCK, size, color);
            default -> getIcon(IconType.CAR, size, color);
        };
    }

    /**
     * Returns a crisp vector icon for slot status.
     */
    public static Icon getStatusIcon(String status, int size) {
        String s = status != null ? status.toUpperCase() : "AVAILABLE";
        return switch (s) {
            case "OCCUPIED" -> getIcon(IconType.CAR, size, new Color(239, 68, 68));
            case "RESERVED" -> getIcon(IconType.CLOCK, size, new Color(245, 158, 11));
            case "MAINTENANCE", "OUT_OF_SERVICE" -> getIcon(IconType.WRENCH, size, new Color(107, 114, 128));
            default -> getIcon(IconType.CHECK, size, new Color(16, 185, 129));
        };
    }

    /**
     * Vector Icon implementation drawing resolution-independent strokes and paths.
     */
    private static class VectorIcon implements Icon {
        private final IconType type;
        private final int width;
        private final int height;
        private final Color color;

        public VectorIcon(IconType type, int width, int height, Color color) {
            this.type = type != null ? type : IconType.CHECK;
            this.width = Math.max(8, width);
            this.height = Math.max(8, height);
            this.color = color != null ? color : Color.DARK_GRAY;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);

            g2.setColor(color);
            float strokeWidth = Math.max(1.5f, width * 0.085f);
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            float w = width;
            float h = height;

            switch (type) {
                case CHECK -> {
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(w * 0.22f, h * 0.52f);
                    p.lineTo(w * 0.42f, h * 0.72f);
                    p.lineTo(w * 0.78f, h * 0.28f);
                    g2.draw(p);
                }
                case CROSS -> {
                    g2.draw(new Line2D.Float(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f));
                    g2.draw(new Line2D.Float(w * 0.75f, h * 0.25f, w * 0.25f, h * 0.75f));
                }
                case CLOCK -> {
                    g2.draw(new Ellipse2D.Float(w * 0.15f, h * 0.15f, w * 0.70f, h * 0.70f));
                    g2.draw(new Line2D.Float(w * 0.50f, h * 0.30f, w * 0.50f, h * 0.50f));
                    g2.draw(new Line2D.Float(w * 0.50f, h * 0.50f, w * 0.68f, h * 0.50f));
                }
                case WRENCH -> {
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(w * 0.30f, h * 0.25f);
                    p.lineTo(w * 0.45f, h * 0.40f);
                    p.lineTo(w * 0.75f, h * 0.70f);
                    p.lineTo(w * 0.70f, h * 0.75f);
                    p.lineTo(w * 0.40f, h * 0.45f);
                    p.lineTo(w * 0.25f, h * 0.30f);
                    g2.draw(p);
                    g2.draw(new Line2D.Float(w * 0.65f, h * 0.65f, w * 0.82f, h * 0.82f));
                }
                case WHEELCHAIR -> {
                    // Head
                    g2.draw(new Ellipse2D.Float(w * 0.42f, h * 0.15f, w * 0.18f, h * 0.18f));
                    // Wheel arc
                    g2.draw(new Arc2D.Float(w * 0.20f, h * 0.45f, w * 0.45f, h * 0.45f, 40, 240, Arc2D.OPEN));
                    // Body / spine
                    g2.draw(new Line2D.Float(w * 0.42f, h * 0.36f, w * 0.42f, h * 0.65f));
                    // Legs / footrest
                    g2.draw(new Line2D.Float(w * 0.42f, h * 0.65f, w * 0.65f, h * 0.65f));
                    g2.draw(new Line2D.Float(w * 0.65f, h * 0.65f, w * 0.65f, h * 0.82f));
                }
                case PLUG -> {
                    // Two prongs
                    g2.draw(new Line2D.Float(w * 0.35f, h * 0.15f, w * 0.35f, h * 0.35f));
                    g2.draw(new Line2D.Float(w * 0.65f, h * 0.15f, w * 0.65f, h * 0.35f));
                    // Plug body
                    g2.draw(new RoundRectangle2D.Float(w * 0.25f, h * 0.35f, w * 0.50f, h * 0.35f, 4, 4));
                    // Cord
                    g2.draw(new Line2D.Float(w * 0.50f, h * 0.70f, w * 0.50f, h * 0.88f));
                }
                case CAR -> {
                    // Car body profile
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(w * 0.12f, h * 0.62f);
                    p.lineTo(w * 0.22f, h * 0.42f);
                    p.lineTo(w * 0.40f, h * 0.30f);
                    p.lineTo(w * 0.68f, h * 0.30f);
                    p.lineTo(w * 0.82f, h * 0.48f);
                    p.lineTo(w * 0.90f, h * 0.62f);
                    p.closePath();
                    g2.draw(p);
                    // Wheels
                    g2.draw(new Ellipse2D.Float(w * 0.22f, h * 0.58f, w * 0.18f, h * 0.18f));
                    g2.draw(new Ellipse2D.Float(w * 0.64f, h * 0.58f, w * 0.18f, h * 0.18f));
                }
                case TRUCK -> {
                    // Cab + cargo bed
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(w * 0.10f, h * 0.68f);
                    p.lineTo(w * 0.10f, h * 0.32f);
                    p.lineTo(w * 0.60f, h * 0.32f);
                    p.lineTo(w * 0.60f, h * 0.44f);
                    p.lineTo(w * 0.78f, h * 0.44f);
                    p.lineTo(w * 0.90f, h * 0.56f);
                    p.lineTo(w * 0.90f, h * 0.68f);
                    p.closePath();
                    g2.draw(p);
                    // Divider
                    g2.draw(new Line2D.Float(w * 0.60f, h * 0.32f, w * 0.60f, h * 0.68f));
                    // Wheels
                    g2.draw(new Ellipse2D.Float(w * 0.22f, h * 0.64f, w * 0.18f, h * 0.18f));
                    g2.draw(new Ellipse2D.Float(w * 0.70f, h * 0.64f, w * 0.18f, h * 0.18f));
                }
                case MOTORCYCLE -> {
                    // Two wheels
                    g2.draw(new Ellipse2D.Float(w * 0.12f, h * 0.52f, w * 0.26f, h * 0.26f));
                    g2.draw(new Ellipse2D.Float(w * 0.62f, h * 0.52f, w * 0.26f, h * 0.26f));
                    // Frame and handle
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(w * 0.25f, h * 0.65f);
                    p.lineTo(w * 0.48f, h * 0.65f);
                    p.lineTo(w * 0.68f, h * 0.40f);
                    p.lineTo(w * 0.60f, h * 0.30f);
                    g2.draw(p);
                    g2.draw(new Line2D.Float(w * 0.48f, h * 0.65f, w * 0.38f, h * 0.45f));
                    g2.draw(new Line2D.Float(w * 0.38f, h * 0.45f, w * 0.55f, h * 0.45f));
                }
                case AUTO_RICKSHAW -> {
                    // Rear wheel and front wheel
                    g2.draw(new Ellipse2D.Float(w * 0.14f, h * 0.58f, w * 0.22f, h * 0.22f));
                    g2.draw(new Ellipse2D.Float(w * 0.66f, h * 0.58f, w * 0.22f, h * 0.22f));
                    // Slanted rickshaw roof canopy
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(w * 0.15f, h * 0.62f);
                    p.lineTo(w * 0.15f, h * 0.38f);
                    p.lineTo(w * 0.35f, h * 0.26f);
                    p.lineTo(w * 0.65f, h * 0.26f);
                    p.lineTo(w * 0.85f, h * 0.50f);
                    p.lineTo(w * 0.85f, h * 0.62f);
                    p.closePath();
                    g2.draw(p);
                }
                case SEARCH -> {
                    g2.draw(new Ellipse2D.Float(w * 0.18f, h * 0.18f, w * 0.48f, h * 0.48f));
                    g2.draw(new Line2D.Float(w * 0.54f, h * 0.54f, w * 0.82f, h * 0.82f));
                }
                case DICE -> {
                    // Rounded die cube
                    g2.draw(new RoundRectangle2D.Float(w * 0.18f, h * 0.18f, w * 0.64f, h * 0.64f, 8, 8));
                    // Dots
                    float d = Math.max(2.5f, w * 0.08f);
                    g2.fill(new Ellipse2D.Float(w * 0.32f - d / 2, h * 0.32f - d / 2, d, d));
                    g2.fill(new Ellipse2D.Float(w * 0.50f - d / 2, h * 0.50f - d / 2, d, d));
                    g2.fill(new Ellipse2D.Float(w * 0.68f - d / 2, h * 0.68f - d / 2, d, d));
                }
                case PRINT -> {
                    // Printer chassis
                    g2.draw(new RoundRectangle2D.Float(w * 0.15f, h * 0.35f, w * 0.70f, h * 0.40f, 6, 6));
                    // Top paper
                    g2.draw(new Line2D.Float(w * 0.30f, h * 0.35f, w * 0.30f, h * 0.18f));
                    g2.draw(new Line2D.Float(w * 0.30f, h * 0.18f, w * 0.70f, h * 0.18f));
                    g2.draw(new Line2D.Float(w * 0.70f, h * 0.18f, w * 0.70f, h * 0.35f));
                    // Bottom output tray
                    g2.draw(new Line2D.Float(w * 0.28f, h * 0.65f, w * 0.72f, h * 0.65f));
                    g2.draw(new Line2D.Float(w * 0.28f, h * 0.78f, w * 0.72f, h * 0.78f));
                }
                case DOWNLOAD -> {
                    // Arrow down + baseline tray
                    g2.draw(new Line2D.Float(w * 0.50f, h * 0.18f, w * 0.50f, h * 0.62f));
                    Path2D.Float arr = new Path2D.Float();
                    arr.moveTo(w * 0.32f, h * 0.48f);
                    arr.lineTo(w * 0.50f, h * 0.64f);
                    arr.lineTo(w * 0.68f, h * 0.48f);
                    g2.draw(arr);
                    g2.draw(new Line2D.Float(w * 0.20f, h * 0.82f, w * 0.80f, h * 0.82f));
                }
                case EYE -> {
                    // Eye contour
                    g2.draw(new Arc2D.Float(w * 0.12f, h * 0.18f, w * 0.76f, h * 0.64f, 35, 110, Arc2D.OPEN));
                    g2.draw(new Arc2D.Float(w * 0.12f, h * 0.18f, w * 0.76f, h * 0.64f, 215, 110, Arc2D.OPEN));
                    // Pupil
                    g2.fill(new Ellipse2D.Float(w * 0.41f, h * 0.41f, w * 0.18f, h * 0.18f));
                }
                case REFUND -> {
                    // Undo arc arrow
                    g2.draw(new Arc2D.Float(w * 0.18f, h * 0.22f, w * 0.64f, h * 0.64f, 40, 260, Arc2D.OPEN));
                    Path2D.Float a = new Path2D.Float();
                    a.moveTo(w * 0.18f, h * 0.18f);
                    a.lineTo(w * 0.18f, h * 0.38f);
                    a.lineTo(w * 0.38f, h * 0.38f);
                    g2.draw(a);
                }
                case ALERT -> {
                    Path2D.Float tri = new Path2D.Float();
                    tri.moveTo(w * 0.50f, h * 0.14f);
                    tri.lineTo(w * 0.88f, h * 0.82f);
                    tri.lineTo(w * 0.12f, h * 0.82f);
                    tri.closePath();
                    g2.draw(tri);
                    g2.draw(new Line2D.Float(w * 0.50f, h * 0.38f, w * 0.50f, h * 0.58f));
                    float d = Math.max(2.0f, w * 0.08f);
                    g2.fill(new Ellipse2D.Float(w * 0.50f - d / 2, h * 0.68f, d, d));
                }
                case USER -> {
                    g2.draw(new Ellipse2D.Float(w * 0.34f, h * 0.16f, w * 0.32f, h * 0.32f));
                    g2.draw(new Arc2D.Float(w * 0.18f, h * 0.48f, w * 0.64f, h * 0.46f, 0, 180, Arc2D.OPEN));
                }
                case LOGOUT -> {
                    // Bracket
                    Path2D.Float b = new Path2D.Float();
                    b.moveTo(w * 0.45f, h * 0.20f);
                    b.lineTo(w * 0.22f, h * 0.20f);
                    b.lineTo(w * 0.22f, h * 0.80f);
                    b.lineTo(w * 0.45f, h * 0.80f);
                    g2.draw(b);
                    // Right arrow
                    g2.draw(new Line2D.Float(w * 0.38f, h * 0.50f, w * 0.82f, h * 0.50f));
                    Path2D.Float a = new Path2D.Float();
                    a.moveTo(w * 0.65f, h * 0.34f);
                    a.lineTo(w * 0.82f, h * 0.50f);
                    a.lineTo(w * 0.65f, h * 0.66f);
                    g2.draw(a);
                }
                case MAP -> {
                    Path2D.Float m = new Path2D.Float();
                    m.moveTo(w * 0.15f, h * 0.22f);
                    m.lineTo(w * 0.38f, h * 0.16f);
                    m.lineTo(w * 0.62f, h * 0.22f);
                    m.lineTo(w * 0.85f, h * 0.16f);
                    m.lineTo(w * 0.85f, h * 0.78f);
                    m.lineTo(w * 0.62f, h * 0.84f);
                    m.lineTo(w * 0.38f, h * 0.78f);
                    m.lineTo(w * 0.15f, h * 0.84f);
                    m.closePath();
                    g2.draw(m);
                    g2.draw(new Line2D.Float(w * 0.38f, h * 0.16f, w * 0.38f, h * 0.78f));
                    g2.draw(new Line2D.Float(w * 0.62f, h * 0.22f, w * 0.62f, h * 0.84f));
                }
                case PLUS -> {
                    g2.draw(new Line2D.Float(w * 0.50f, h * 0.20f, w * 0.50f, h * 0.80f));
                    g2.draw(new Line2D.Float(w * 0.20f, h * 0.50f, w * 0.80f, h * 0.50f));
                }
                case MINUS -> {
                    g2.draw(new Line2D.Float(w * 0.20f, h * 0.50f, w * 0.80f, h * 0.50f));
                }
                case FIT_SCREEN -> {
                    // 4 corner brackets
                    // Top-left
                    g2.draw(new Line2D.Float(w * 0.18f, h * 0.32f, w * 0.18f, h * 0.18f));
                    g2.draw(new Line2D.Float(w * 0.18f, h * 0.18f, w * 0.32f, h * 0.18f));
                    // Top-right
                    g2.draw(new Line2D.Float(w * 0.68f, h * 0.18f, w * 0.82f, h * 0.18f));
                    g2.draw(new Line2D.Float(w * 0.82f, h * 0.18f, w * 0.82f, h * 0.32f));
                    // Bottom-left
                    g2.draw(new Line2D.Float(w * 0.18f, h * 0.68f, w * 0.18f, h * 0.82f));
                    g2.draw(new Line2D.Float(w * 0.18f, h * 0.82f, w * 0.32f, h * 0.82f));
                    // Bottom-right
                    g2.draw(new Line2D.Float(w * 0.68f, h * 0.82f, w * 0.82f, h * 0.82f));
                    g2.draw(new Line2D.Float(w * 0.82f, h * 0.82f, w * 0.82f, h * 0.68f));
                }
                case PHONE -> {
                    g2.draw(new RoundRectangle2D.Float(w * 0.26f, h * 0.14f, w * 0.48f, h * 0.72f, 8, 8));
                    g2.draw(new Line2D.Float(w * 0.42f, h * 0.22f, w * 0.58f, h * 0.22f));
                    float d = Math.max(2.0f, w * 0.08f);
                    g2.fill(new Ellipse2D.Float(w * 0.50f - d / 2, h * 0.74f, d, d));
                }
                case CALENDAR -> {
                    g2.draw(new RoundRectangle2D.Float(w * 0.16f, h * 0.22f, w * 0.68f, h * 0.64f, 6, 6));
                    g2.draw(new Line2D.Float(w * 0.16f, h * 0.40f, w * 0.84f, h * 0.40f));
                    g2.draw(new Line2D.Float(w * 0.34f, h * 0.14f, w * 0.34f, h * 0.26f));
                    g2.draw(new Line2D.Float(w * 0.66f, h * 0.14f, w * 0.66f, h * 0.26f));
                }
                case GLOBE -> {
                    g2.draw(new Ellipse2D.Float(w * 0.15f, h * 0.15f, w * 0.70f, h * 0.70f));
                    g2.draw(new Line2D.Float(w * 0.15f, h * 0.50f, w * 0.85f, h * 0.50f));
                    g2.draw(new Arc2D.Float(w * 0.30f, h * 0.15f, w * 0.40f, h * 0.70f, 0, 360, Arc2D.OPEN));
                }
                case DASHBOARD -> {
                    g2.draw(new RoundRectangle2D.Float(w * 0.15f, h * 0.15f, w * 0.32f, h * 0.32f, 4, 4));
                    g2.draw(new RoundRectangle2D.Float(w * 0.53f, h * 0.15f, w * 0.32f, h * 0.44f, 4, 4));
                    g2.draw(new RoundRectangle2D.Float(w * 0.15f, h * 0.53f, w * 0.32f, h * 0.32f, 4, 4));
                    g2.draw(new RoundRectangle2D.Float(w * 0.53f, h * 0.65f, w * 0.32f, h * 0.20f, 4, 4));
                }
                case TICKET -> {
                    Path2D.Float p = new Path2D.Float();
                    p.moveTo(w * 0.15f, h * 0.25f);
                    p.lineTo(w * 0.85f, h * 0.25f);
                    p.lineTo(w * 0.85f, h * 0.44f);
                    p.curveTo(w * 0.75f, h * 0.44f, w * 0.75f, h * 0.56f, w * 0.85f, h * 0.56f);
                    p.lineTo(w * 0.85f, h * 0.75f);
                    p.lineTo(w * 0.15f, h * 0.75f);
                    p.lineTo(w * 0.15f, h * 0.56f);
                    p.curveTo(w * 0.25f, h * 0.56f, w * 0.25f, h * 0.44f, w * 0.15f, h * 0.44f);
                    p.closePath();
                    g2.draw(p);
                    g2.draw(new Line2D.Float(w * 0.35f, h * 0.38f, w * 0.65f, h * 0.38f));
                    g2.draw(new Line2D.Float(w * 0.35f, h * 0.50f, w * 0.55f, h * 0.50f));
                }
                case PAYMENT -> {
                    g2.draw(new RoundRectangle2D.Float(w * 0.15f, h * 0.25f, w * 0.70f, h * 0.50f, 6, 6));
                    g2.draw(new Line2D.Float(w * 0.15f, h * 0.40f, w * 0.85f, h * 0.40f));
                    g2.draw(new Line2D.Float(w * 0.25f, h * 0.58f, w * 0.45f, h * 0.58f));
                }
                case MEMBERSHIP -> {
                    // ID Badge card with ribbon
                    g2.draw(new RoundRectangle2D.Float(w * 0.22f, h * 0.22f, w * 0.56f, h * 0.66f, 6, 6));
                    g2.draw(new Ellipse2D.Float(w * 0.40f, h * 0.35f, w * 0.20f, h * 0.20f));
                    g2.draw(new Line2D.Float(w * 0.32f, h * 0.66f, w * 0.68f, h * 0.66f));
                    g2.draw(new Line2D.Float(w * 0.42f, h * 0.12f, w * 0.58f, h * 0.12f));
                }
                case REPORTS -> {
                    g2.draw(new RoundRectangle2D.Float(w * 0.18f, h * 0.16f, w * 0.64f, h * 0.72f, 4, 4));
                    g2.draw(new Line2D.Float(w * 0.30f, h * 0.34f, w * 0.70f, h * 0.34f));
                    g2.draw(new Line2D.Float(w * 0.30f, h * 0.48f, w * 0.70f, h * 0.48f));
                    g2.draw(new Line2D.Float(w * 0.30f, h * 0.62f, w * 0.55f, h * 0.62f));
                }
                case ARROW_RIGHT -> {
                    g2.draw(new Line2D.Float(w * 0.20f, h * 0.50f, w * 0.80f, h * 0.50f));
                    Path2D.Float a = new Path2D.Float();
                    a.moveTo(w * 0.55f, h * 0.28f);
                    a.lineTo(w * 0.80f, h * 0.50f);
                    a.lineTo(w * 0.55f, h * 0.72f);
                    g2.draw(a);
                }
                case ARROW_LEFT -> {
                    g2.draw(new Line2D.Float(w * 0.80f, h * 0.50f, w * 0.20f, h * 0.50f));
                    Path2D.Float a = new Path2D.Float();
                    a.moveTo(w * 0.45f, h * 0.28f);
                    a.lineTo(w * 0.20f, h * 0.50f);
                    a.lineTo(w * 0.45f, h * 0.72f);
                    g2.draw(a);
                }
                case REFRESH -> {
                    g2.draw(new Arc2D.Float(w * 0.20f, h * 0.20f, w * 0.60f, h * 0.60f, 35, 270, Arc2D.OPEN));
                    Path2D.Float a = new Path2D.Float();
                    a.moveTo(w * 0.55f, h * 0.12f);
                    a.lineTo(w * 0.80f, h * 0.20f);
                    a.lineTo(w * 0.65f, h * 0.38f);
                    g2.draw(a);
                }
                case GRID -> {
                    int s = (int) (w * 0.28f);
                    int gap = (int) (w * 0.12f);
                    int sx = (int) ((w - (2 * s + gap)) / 2);
                    int sy = (int) ((h - (2 * s + gap)) / 2);
                    g2.fillRoundRect(sx, sy, s, s, 3, 3);
                    g2.fillRoundRect(sx + s + gap, sy, s, s, 3, 3);
                    g2.fillRoundRect(sx, sy + s + gap, s, s, 3, 3);
                    g2.fillRoundRect(sx + s + gap, sy + s + gap, s, s, 3, 3);
                }
                case SETTINGS -> {
                    g2.draw(new Ellipse2D.Float(w * 0.35f, h * 0.35f, w * 0.30f, h * 0.30f));
                    for (int i = 0; i < 8; i++) {
                        double angle = i * Math.PI / 4.0;
                        float x1 = (float) (w * 0.50f + Math.cos(angle) * (w * 0.25f));
                        float y1 = (float) (h * 0.50f + Math.sin(angle) * (h * 0.25f));
                        float x2 = (float) (w * 0.50f + Math.cos(angle) * (w * 0.40f));
                        float y2 = (float) (h * 0.50f + Math.sin(angle) * (h * 0.40f));
                        g2.draw(new Line2D.Float(x1, y1, x2, y2));
                    }
                }
                case BELL -> {
                    Path2D.Float bell = new Path2D.Float();
                    bell.moveTo(w * 0.50f, h * 0.15f);
                    bell.curveTo(w * 0.30f, h * 0.20f, w * 0.25f, h * 0.55f, w * 0.20f, h * 0.75f);
                    bell.lineTo(w * 0.80f, h * 0.75f);
                    bell.curveTo(w * 0.75f, h * 0.55f, w * 0.70f, h * 0.20f, w * 0.50f, h * 0.15f);
                    g2.draw(bell);
                    g2.draw(new Line2D.Float(w * 0.15f, h * 0.75f, w * 0.85f, h * 0.75f));
                    g2.draw(new Arc2D.Float(w * 0.42f, h * 0.76f, w * 0.16f, h * 0.14f, 180, 180, Arc2D.OPEN));
                }
                case SHIELD -> {
                    Path2D.Float sh = new Path2D.Float();
                    sh.moveTo(w * 0.50f, h * 0.12f);
                    sh.lineTo(w * 0.80f, h * 0.24f);
                    sh.curveTo(w * 0.80f, h * 0.58f, w * 0.68f, h * 0.78f, w * 0.50f, h * 0.88f);
                    sh.curveTo(w * 0.32f, h * 0.78f, w * 0.20f, h * 0.58f, w * 0.20f, h * 0.24f);
                    sh.closePath();
                    g2.draw(sh);
                    Path2D.Float tick = new Path2D.Float();
                    tick.moveTo(w * 0.37f, h * 0.50f);
                    tick.lineTo(w * 0.47f, h * 0.60f);
                    tick.lineTo(w * 0.64f, h * 0.40f);
                    g2.draw(tick);
                }
                case USERS -> {
                    g2.draw(new Ellipse2D.Float(w * 0.20f, h * 0.20f, w * 0.28f, h * 0.28f));
                    g2.draw(new Arc2D.Float(w * 0.08f, h * 0.54f, w * 0.52f, h * 0.40f, 0, 180, Arc2D.OPEN));
                    g2.draw(new Arc2D.Float(w * 0.56f, h * 0.22f, w * 0.24f, h * 0.24f, -60, 240, Arc2D.OPEN));
                    g2.draw(new Arc2D.Float(w * 0.58f, h * 0.54f, w * 0.34f, h * 0.36f, 0, 90, Arc2D.OPEN));
                }
                case DOCUMENT -> {
                    Path2D.Float d = new Path2D.Float();
                    d.moveTo(w * 0.24f, h * 0.12f);
                    d.lineTo(w * 0.60f, h * 0.12f);
                    d.lineTo(w * 0.78f, h * 0.30f);
                    d.lineTo(w * 0.78f, h * 0.88f);
                    d.lineTo(w * 0.24f, h * 0.88f);
                    d.closePath();
                    g2.draw(d);
                    g2.draw(new Line2D.Float(w * 0.36f, h * 0.48f, w * 0.66f, h * 0.48f));
                    g2.draw(new Line2D.Float(w * 0.36f, h * 0.64f, w * 0.66f, h * 0.64f));
                }
                case LIGHTNING -> {
                    Path2D.Float bolt = new Path2D.Float();
                    bolt.moveTo(w * 0.55f, h * 0.12f);
                    bolt.lineTo(w * 0.28f, h * 0.52f);
                    bolt.lineTo(w * 0.50f, h * 0.52f);
                    bolt.lineTo(w * 0.42f, h * 0.88f);
                    bolt.lineTo(w * 0.72f, h * 0.45f);
                    bolt.lineTo(w * 0.52f, h * 0.45f);
                    bolt.closePath();
                    g2.fill(bolt);
                }
            }

            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
