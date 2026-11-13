package com.smartparking.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * High-quality vector asset generator that produces copyright-safe, professional,
 * modern PNG images and icons for the Smart Parking Management System.
 */
public class AssetGenerator {

    private static final String RES_DIR = "src/main/resources/";
    private static final String IMAGES_DIR = RES_DIR + "images/";
    private static final String ICONS_DIR = RES_DIR + "icons/";

    public static void main(String[] args) {
        System.out.println("[ASSET GENERATOR] Starting visual asset generation...");
        new File(IMAGES_DIR).mkdirs();
        new File(ICONS_DIR + "vehicles/").mkdirs();
        new File(ICONS_DIR + "payments/").mkdirs();
        new File(ICONS_DIR + "dashboard/").mkdirs();
        new File(ICONS_DIR + "navigation/").mkdirs();
        new File(ICONS_DIR + "slots/").mkdirs();
        new File(ICONS_DIR + "auth/").mkdirs();

        // 1. Application Logos
        generateLogo(IMAGES_DIR + "logo.png", 256);
        generateLogo(IMAGES_DIR + "logo_small.png", 64);
        generateLogo(IMAGES_DIR + "logo_nav.png", 40);

        // 2. Backgrounds & Banners
        generateParkingBackground(IMAGES_DIR + "parking_background.png", 900, 650);
        generateDashboardBanner(IMAGES_DIR + "dashboard_banner.png", 1000, 160);

        // 3. Vehicle Icons (Preserve custom uploaded badge assets if present)
        if (!new File(ICONS_DIR + "vehicles/motorcycle.png").exists()) generateMotorcycleIcon(ICONS_DIR + "vehicles/motorcycle.png", 64);
        if (!new File(ICONS_DIR + "vehicles/car.png").exists()) generateCarIcon(ICONS_DIR + "vehicles/car.png", 64);
        if (!new File(ICONS_DIR + "vehicles/truck.png").exists()) generateTruckIcon(ICONS_DIR + "vehicles/truck.png", 64);
        if (!new File(ICONS_DIR + "vehicles/electric_car.png").exists()) generateElectricCarIcon(ICONS_DIR + "vehicles/electric_car.png", 64);

        // Also save root aliases
        if (!new File(ICONS_DIR + "motorcycle.png").exists()) generateMotorcycleIcon(ICONS_DIR + "motorcycle.png", 64);
        if (!new File(ICONS_DIR + "car.png").exists()) generateCarIcon(ICONS_DIR + "car.png", 64);
        if (!new File(ICONS_DIR + "truck.png").exists()) generateTruckIcon(ICONS_DIR + "truck.png", 64);
        if (!new File(ICONS_DIR + "electric_car.png").exists()) generateElectricCarIcon(ICONS_DIR + "electric_car.png", 64);

        // 4. Payment Icons
        generateCashIcon(ICONS_DIR + "payments/cash.png", 64);
        generateCardIcon(ICONS_DIR + "payments/card.png", 64);
        generateUpiIcon(ICONS_DIR + "payments/upi.png", 64);
        generateWalletIcon(ICONS_DIR + "payments/wallet.png", 64);

        // Root aliases
        generateCashIcon(ICONS_DIR + "cash.png", 64);
        generateCardIcon(ICONS_DIR + "card.png", 64);
        generateUpiIcon(ICONS_DIR + "upi.png", 64);
        generateWalletIcon(ICONS_DIR + "wallet.png", 64);

        // 5. Dashboard Metric Icons
        generateTotalSlotsIcon(ICONS_DIR + "dashboard/total_slots.png", 48);
        generateAvailableSlotsIcon(ICONS_DIR + "dashboard/available_slots.png", 48);
        generateOccupiedSlotsIcon(ICONS_DIR + "dashboard/occupied_slots.png", 48);
        generateRevenueIcon(ICONS_DIR + "dashboard/revenue.png", 48);
        generateOccupancyIcon(ICONS_DIR + "dashboard/occupancy.png", 48);

        // 6. Navigation Icons
        generateNavDashboard(ICONS_DIR + "navigation/dashboard.png", 32);
        generateNavEntry(ICONS_DIR + "navigation/entry.png", 32);
        generateNavExit(ICONS_DIR + "navigation/exit.png", 32);
        generateNavFloors(ICONS_DIR + "navigation/floors.png", 32);
        generateNavReservation(ICONS_DIR + "navigation/reservation.png", 32);
        generateNavMembership(ICONS_DIR + "navigation/membership.png", 32);
        generateNavSearch(ICONS_DIR + "navigation/search.png", 32);
        generateNavReports(ICONS_DIR + "navigation/reports.png", 32);
        generateNavUsers(ICONS_DIR + "navigation/users.png", 32);
        generateNavLogout(ICONS_DIR + "navigation/logout.png", 32);

        // 7. Slot Indicators & Badges
        generateSlotStatusIcon(ICONS_DIR + "slots/slot_available.png", 40, new Color(16, 185, 129), "A");
        generateSlotStatusIcon(ICONS_DIR + "slots/slot_occupied.png", 40, new Color(239, 68, 68), "O");
        generateSlotStatusIcon(ICONS_DIR + "slots/slot_reserved.png", 40, new Color(245, 158, 11), "R");
        generateSlotStatusIcon(ICONS_DIR + "slots/slot_maintenance.png", 40, new Color(107, 114, 128), "X");
        generateEvBadge(ICONS_DIR + "slots/ev_charging.png", 40);
        generateHandicappedBadge(ICONS_DIR + "slots/handicapped_badge.png", 40);

        // 8. Auth Icons
        generateUserIcon(ICONS_DIR + "auth/user.png", 32);
        generatePasswordIcon(ICONS_DIR + "auth/password.png", 32);
        generateShieldIcon(ICONS_DIR + "auth/shield.png", 32);

        System.out.println("[ASSET GENERATOR] All visual assets generated successfully!");
    }

    private static Graphics2D createAntiAliasedGraphics(BufferedImage img) {
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g2;
    }

    private static void saveImage(BufferedImage img, String path) {
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            ImageIO.write(img, "png", file);
            System.out.println("  + Created: " + path);
        } catch (IOException e) {
            System.err.println("  ! Failed to save: " + path + " - " + e.getMessage());
        }
    }

    // ==========================================
    // 1. APPLICATION LOGO
    // ==========================================
    public static void generateLogo(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        // Background rounded hexagon/shield
        RoundRectangle2D bg = new RoundRectangle2D.Float(size * 0.05f, size * 0.05f, size * 0.9f, size * 0.9f, size * 0.25f, size * 0.25f);
        GradientPaint gp = new GradientPaint(0, 0, new Color(30, 58, 138), size, size, new Color(14, 165, 233));
        g2.setPaint(gp);
        g2.fill(bg);

        // Subtle glow border
        g2.setColor(new Color(255, 255, 255, 70));
        g2.setStroke(new BasicStroke(size * 0.02f));
        g2.draw(bg);

        // IoT Wireless Signal Arches at top
        g2.setColor(new Color(56, 189, 248));
        g2.setStroke(new BasicStroke(size * 0.035f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc((int)(size * 0.28), (int)(size * 0.12), (int)(size * 0.44), (int)(size * 0.35), 40, 100);
        g2.drawArc((int)(size * 0.36), (int)(size * 0.19), (int)(size * 0.28), (int)(size * 0.22), 40, 100);

        // Central Bold Letter "P"
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int)(size * 0.42)));
        FontMetrics fm = g2.getFontMetrics();
        int px = (size - fm.stringWidth("P")) / 2;
        int py = (int)(size * 0.58);
        g2.drawString("P", px, py);

        // Modern Sleek Car Silhouette at bottom
        int carY = (int)(size * 0.65);
        Path2D car = new Path2D.Float();
        car.moveTo(size * 0.22, carY + size * 0.12);
        car.lineTo(size * 0.28, carY + size * 0.05);
        car.lineTo(size * 0.40, carY + size * 0.04);
        car.lineTo(size * 0.48, carY);
        car.lineTo(size * 0.62, carY);
        car.lineTo(size * 0.72, carY + size * 0.05);
        car.lineTo(size * 0.78, carY + size * 0.12);
        car.closePath();

        g2.setColor(new Color(248, 250, 252));
        g2.fill(car);

        // Wheels
        g2.setColor(new Color(15, 23, 42));
        int wheelR = (int)(size * 0.07);
        g2.fillOval((int)(size * 0.32), (int)(carY + size * 0.08), wheelR, wheelR);
        g2.fillOval((int)(size * 0.61), (int)(carY + size * 0.08), wheelR, wheelR);

        // Wheel Hubs (Cyan accent)
        g2.setColor(new Color(56, 189, 248));
        int hubR = (int)(size * 0.03);
        g2.fillOval((int)(size * 0.34), (int)(carY + size * 0.10), hubR, hubR);
        g2.fillOval((int)(size * 0.63), (int)(carY + size * 0.10), hubR, hubR);

        g2.dispose();
        saveImage(img, path);
    }

    // ==========================================
    // 2. PARKING FACILITY BACKGROUND
    // ==========================================
    public static void generateParkingBackground(String path, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        // Night / Modern Slate gradient
        GradientPaint sky = new GradientPaint(0, 0, new Color(15, 23, 42), 0, height, new Color(30, 41, 59));
        g2.setPaint(sky);
        g2.fillRect(0, 0, width, height);

        // Grid lines / Digital smart city overlay
        g2.setColor(new Color(255, 255, 255, 12));
        g2.setStroke(new BasicStroke(1.0f));
        for (int x = 0; x < width; x += 40) {
            g2.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += 40) {
            g2.drawLine(0, y, width, y);
        }

        // Architectural Multi-Deck Garage Structure (Perspective)
        int deckBaseY = height - 120;
        int deckHeight = 110;

        for (int floor = 0; floor < 3; floor++) {
            int y = deckBaseY - (floor * deckHeight);

            // Floor concrete slab
            g2.setColor(new Color(51, 65, 85));
            g2.fillRect(60, y, width - 120, 16);

            // Floor neon edge lighting
            Color neon = (floor == 1) ? new Color(16, 185, 129, 180) : new Color(56, 189, 248, 160);
            g2.setColor(neon);
            g2.fillRect(60, y - 2, width - 120, 3);

            // Support Pillars
            g2.setColor(new Color(30, 41, 59));
            for (int p = 120; p < width - 120; p += 160) {
                g2.fillRect(p, y - deckHeight + 16, 24, deckHeight - 16);
                // Pillar LED strip
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRect(p + 10, y - deckHeight + 20, 4, deckHeight - 24);
                g2.setColor(new Color(30, 41, 59));
            }

            // Bay slot markings & parked vehicle silhouettes
            for (int s = 80; s < width - 140; s += 80) {
                // Bay ceiling light
                g2.setColor(new Color(255, 255, 200, 100));
                g2.fillOval(s + 30, y - deckHeight + 18, 20, 6);

                // Slot sensor LED (Green or Red)
                boolean occupied = ((s + floor * 50) % 130 == 0);
                g2.setColor(occupied ? new Color(239, 68, 68) : new Color(16, 185, 129));
                g2.fillOval(s + 37, y - deckHeight + 24, 6, 6);
            }

            // Floor Label Banner
            g2.setColor(new Color(148, 163, 184));
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            g2.drawString("LEVEL 0" + (3 - floor), 75, y - deckHeight + 45);
        }

        // Entrance Gate Canopy at ground
        g2.setColor(new Color(37, 99, 235));
        g2.fillRect(width - 320, height - 120, 240, 12);
        // Boom barrier arm (striped yellow/black)
        g2.setColor(new Color(245, 158, 11));
        g2.setStroke(new BasicStroke(6.0f));
        g2.drawLine(width - 290, height - 70, width - 130, height - 70);

        // Ground pavement road markings
        g2.setColor(new Color(15, 23, 42));
        g2.fillRect(0, height - 50, width, 50);
        g2.setColor(new Color(245, 158, 11));
        g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{20, 15}, 0));
        g2.drawLine(0, height - 25, width, height - 25);

        // Glowing Title watermark
        g2.setColor(new Color(255, 255, 255, 35));
        g2.setFont(new Font("SansSerif", Font.BOLD, 48));
        g2.drawString("SMART PARKING FACILITY", 80, 80);

        g2.dispose();
        saveImage(img, path);
    }

    // ==========================================
    // 3. DASHBOARD BANNER
    // ==========================================
    public static void generateDashboardBanner(String path, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        // Elegant deep navy-indigo gradient
        GradientPaint gp = new GradientPaint(0, 0, new Color(30, 27, 75), width, height, new Color(15, 23, 42));
        g2.setPaint(gp);
        g2.fillRect(0, 0, width, height);

        // Tech geometric patterns / Hexagon accents
        g2.setColor(new Color(99, 102, 241, 35));
        for (int i = 0; i < 6; i++) {
            int cx = width - 260 + (i * 45);
            int cy = 40 + (i % 2 * 50);
            drawHexagon(g2, cx, cy, 35);
        }

        // Radar / WiFi Smart Signal Arches
        g2.setColor(new Color(56, 189, 248, 80));
        g2.setStroke(new BasicStroke(2.5f));
        g2.drawArc(width - 120, -40, 220, 220, 180, 90);
        g2.drawArc(width - 90, -10, 160, 160, 180, 90);
        g2.drawArc(width - 60, 20, 100, 100, 180, 90);

        // Banner Text Title & Subtitle
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        g2.drawString("Grand Tech Park Smart Garage", 40, 55);

        g2.setColor(new Color(148, 163, 184));
        g2.setFont(new Font("SansSerif", Font.PLAIN, 15));
        g2.drawString("Multi-Floor Intelligent Parking Management & Automation System", 40, 85);

        // Live status pill badge
        RoundRectangle2D pill = new RoundRectangle2D.Float(40, 105, 190, 28, 14, 14);
        g2.setColor(new Color(16, 185, 129, 40));
        g2.fill(pill);
        g2.setColor(new Color(16, 185, 129));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(pill);

        // Green glowing dot
        g2.fillOval(52, 115, 8, 8);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString("SYSTEM OPERATIONAL", 68, 124);

        g2.dispose();
        saveImage(img, path);
    }

    private static void drawHexagon(Graphics2D g2, int x, int y, int radius) {
        Path2D hex = new Path2D.Float();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 3 * i;
            double hx = x + radius * Math.cos(angle);
            double hy = y + radius * Math.sin(angle);
            if (i == 0) hex.moveTo(hx, hy);
            else hex.lineTo(hx, hy);
        }
        hex.closePath();
        g2.fill(hex);
    }

    // ==========================================
    // 4. VEHICLE ICONS
    // ==========================================
    public static void generateMotorcycleIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        // Blue / Indigo circular background badge
        g2.setColor(new Color(238, 242, 255));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(99, 102, 241));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        // Two Wheels
        int wheelRadius = (int)(size * 0.22);
        int wheelY = (int)(size * 0.55);
        g2.setColor(new Color(30, 41, 59));
        g2.fillOval((int)(size * 0.12), wheelY, wheelRadius, wheelRadius);
        g2.fillOval((int)(size * 0.62), wheelY, wheelRadius, wheelRadius);

        // Rim centres
        g2.setColor(new Color(226, 232, 240));
        g2.fillOval((int)(size * 0.18), wheelY + (int)(wheelRadius * 0.25), (int)(wheelRadius * 0.5), (int)(wheelRadius * 0.5));
        g2.fillOval((int)(size * 0.68), wheelY + (int)(wheelRadius * 0.25), (int)(wheelRadius * 0.5), (int)(wheelRadius * 0.5));

        // Motorcycle Frame & Handlebar
        g2.setColor(new Color(79, 70, 229));
        g2.setStroke(new BasicStroke(size * 0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int)(size * 0.23), wheelY + wheelRadius / 2, (int)(size * 0.45), wheelY + wheelRadius / 2);
        g2.drawLine((int)(size * 0.45), wheelY + wheelRadius / 2, (int)(size * 0.38), (int)(size * 0.42));
        g2.drawLine((int)(size * 0.38), (int)(size * 0.42), (int)(size * 0.56), (int)(size * 0.40));
        g2.drawLine((int)(size * 0.56), (int)(size * 0.40), (int)(size * 0.73), wheelY + wheelRadius / 2);
        g2.drawLine((int)(size * 0.58), (int)(size * 0.32), (int)(size * 0.54), (int)(size * 0.25));

        g2.dispose();
        saveImage(img, path);
    }

    public static void generateCarIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        g2.setColor(new Color(240, 253, 244));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(16, 185, 129));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        // Car Body Profile
        Path2D car = new Path2D.Float();
        int base = (int)(size * 0.68);
        car.moveTo(size * 0.12, base);
        car.lineTo(size * 0.18, base - size * 0.15);
        car.lineTo(size * 0.35, base - size * 0.17);
        car.lineTo(size * 0.45, base - size * 0.32);
        car.lineTo(size * 0.68, base - size * 0.32);
        car.lineTo(size * 0.78, base - size * 0.15);
        car.lineTo(size * 0.88, base - size * 0.12);
        car.lineTo(size * 0.88, base);
        car.closePath();

        GradientPaint gp = new GradientPaint(0, 0, new Color(16, 185, 129), size, size, new Color(5, 150, 105));
        g2.setPaint(gp);
        g2.fill(car);

        // Windows (Glass)
        Path2D window = new Path2D.Float();
        window.moveTo(size * 0.38, base - size * 0.18);
        window.lineTo(size * 0.46, base - size * 0.30);
        window.lineTo(size * 0.66, base - size * 0.30);
        window.lineTo(size * 0.74, base - size * 0.18);
        window.closePath();
        g2.setColor(new Color(209, 250, 229));
        g2.fill(window);

        // Wheels
        int wSize = (int)(size * 0.20);
        g2.setColor(new Color(30, 41, 59));
        g2.fillOval((int)(size * 0.24), base - wSize / 2, wSize, wSize);
        g2.fillOval((int)(size * 0.66), base - wSize / 2, wSize, wSize);
        g2.setColor(new Color(226, 232, 240));
        g2.fillOval((int)(size * 0.29), base - wSize / 2 + wSize / 4, wSize / 2, wSize / 2);
        g2.fillOval((int)(size * 0.71), base - wSize / 2 + wSize / 4, wSize / 2, wSize / 2);

        g2.dispose();
        saveImage(img, path);
    }

    public static void generateTruckIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        g2.setColor(new Color(254, 243, 199));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(245, 158, 11));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        int base = (int)(size * 0.68);

        // Cargo Trailer Box
        g2.setColor(new Color(217, 119, 6));
        g2.fillRoundRect((int)(size * 0.12), (int)(base - size * 0.38), (int)(size * 0.44), (int)(size * 0.38), 4, 4);

        // Cab
        Path2D cab = new Path2D.Float();
        cab.moveTo(size * 0.58, base);
        cab.lineTo(size * 0.58, base - size * 0.34);
        cab.lineTo(size * 0.74, base - size * 0.34);
        cab.lineTo(size * 0.85, base - size * 0.16);
        cab.lineTo(size * 0.85, base);
        cab.closePath();
        g2.setColor(new Color(245, 158, 11));
        g2.fill(cab);

        // Cab Window
        Path2D win = new Path2D.Float();
        win.moveTo(size * 0.62, base - size * 0.31);
        win.lineTo(size * 0.72, base - size * 0.31);
        win.lineTo(size * 0.80, base - size * 0.18);
        win.lineTo(size * 0.62, base - size * 0.18);
        win.closePath();
        g2.setColor(new Color(254, 243, 199));
        g2.fill(win);

        // Wheels
        int wSize = (int)(size * 0.18);
        g2.setColor(new Color(30, 41, 59));
        g2.fillOval((int)(size * 0.18), base - wSize / 2, wSize, wSize);
        g2.fillOval((int)(size * 0.36), base - wSize / 2, wSize, wSize);
        g2.fillOval((int)(size * 0.68), base - wSize / 2, wSize, wSize);

        g2.dispose();
        saveImage(img, path);
    }

    public static void generateElectricCarIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        g2.setColor(new Color(240, 249, 255));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(14, 165, 233));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        int base = (int)(size * 0.68);

        Path2D ev = new Path2D.Float();
        ev.moveTo(size * 0.12, base);
        ev.lineTo(size * 0.18, base - size * 0.14);
        ev.lineTo(size * 0.35, base - size * 0.16);
        ev.lineTo(size * 0.46, base - size * 0.32);
        ev.lineTo(size * 0.70, base - size * 0.32);
        ev.lineTo(size * 0.80, base - size * 0.15);
        ev.lineTo(size * 0.88, base - size * 0.10);
        ev.lineTo(size * 0.88, base);
        ev.closePath();

        GradientPaint gp = new GradientPaint(0, 0, new Color(14, 165, 233), size, size, new Color(2, 132, 199));
        g2.setPaint(gp);
        g2.fill(ev);

        Path2D win = new Path2D.Float();
        win.moveTo(size * 0.38, base - size * 0.17);
        win.lineTo(size * 0.47, base - size * 0.30);
        win.lineTo(size * 0.68, base - size * 0.30);
        win.lineTo(size * 0.76, base - size * 0.17);
        win.closePath();
        g2.setColor(new Color(224, 242, 254));
        g2.fill(win);

        int wSize = (int)(size * 0.20);
        g2.setColor(new Color(30, 41, 59));
        g2.fillOval((int)(size * 0.24), base - wSize / 2, wSize, wSize);
        g2.fillOval((int)(size * 0.66), base - wSize / 2, wSize, wSize);

        // Lightning Bolt
        Path2D bolt = new Path2D.Float();
        bolt.moveTo(size * 0.58, size * 0.12);
        bolt.lineTo(size * 0.48, size * 0.26);
        bolt.lineTo(size * 0.55, size * 0.26);
        bolt.lineTo(size * 0.46, size * 0.42);
        bolt.lineTo(size * 0.62, size * 0.24);
        bolt.lineTo(size * 0.54, size * 0.24);
        bolt.closePath();

        g2.setColor(new Color(234, 179, 8));
        g2.fill(bolt);

        g2.dispose();
        saveImage(img, path);
    }

    // ==========================================
    // 5. PAYMENT ICONS
    // ==========================================
    public static void generateCashIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        g2.setColor(new Color(236, 253, 245));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(16, 185, 129));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        RoundRectangle2D note = new RoundRectangle2D.Float(size * 0.16f, size * 0.28f, size * 0.68f, size * 0.44f, 8, 8);
        g2.setColor(new Color(5, 150, 105));
        g2.fill(note);
        g2.setColor(new Color(16, 185, 129));
        g2.setStroke(new BasicStroke(size * 0.03f));
        g2.draw(note);

        g2.setColor(new Color(209, 250, 229));
        g2.fillOval((int)(size * 0.40), (int)(size * 0.38), (int)(size * 0.20), (int)(size * 0.20));

        g2.setColor(new Color(6, 78, 59));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int)(size * 0.22)));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("$", (size - fm.stringWidth("$")) / 2, (int)(size * 0.57));

        g2.dispose();
        saveImage(img, path);
    }

    public static void generateCardIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        g2.setColor(new Color(238, 242, 255));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(99, 102, 241));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        RoundRectangle2D card = new RoundRectangle2D.Float(size * 0.16f, size * 0.28f, size * 0.68f, size * 0.44f, 8, 8);
        GradientPaint gp = new GradientPaint(0, 0, new Color(79, 70, 229), size, size, new Color(99, 102, 241));
        g2.setPaint(gp);
        g2.fill(card);

        g2.setColor(new Color(30, 27, 75));
        g2.fillRect((int)(size * 0.16f), (int)(size * 0.36f), (int)(size * 0.68f), (int)(size * 0.08f));

        g2.setColor(new Color(251, 191, 36));
        g2.fillRoundRect((int)(size * 0.24f), (int)(size * 0.50f), (int)(size * 0.15f), (int)(size * 0.12f), 3, 3);

        g2.dispose();
        saveImage(img, path);
    }

    public static void generateUpiIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        g2.setColor(new Color(254, 242, 242));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(239, 68, 68));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        Path2D p1 = new Path2D.Float();
        p1.moveTo(size * 0.25, size * 0.65);
        p1.lineTo(size * 0.55, size * 0.25);
        p1.lineTo(size * 0.45, size * 0.65);
        p1.closePath();
        g2.setColor(new Color(249, 115, 22));
        g2.fill(p1);

        Path2D p2 = new Path2D.Float();
        p2.moveTo(size * 0.50, size * 0.35);
        p2.lineTo(size * 0.75, size * 0.35);
        p2.lineTo(size * 0.45, size * 0.75);
        p2.closePath();
        g2.setColor(new Color(16, 185, 129));
        g2.fill(p2);

        g2.dispose();
        saveImage(img, path);
    }

    public static void generateWalletIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);

        g2.setColor(new Color(250, 245, 255));
        g2.fillOval(2, 2, size - 4, size - 4);
        g2.setColor(new Color(168, 85, 247));
        g2.setStroke(new BasicStroke(2.0f));
        g2.drawOval(2, 2, size - 4, size - 4);

        RoundRectangle2D body = new RoundRectangle2D.Float(size * 0.18f, size * 0.30f, size * 0.64f, size * 0.44f, 10, 10);
        g2.setColor(new Color(147, 51, 234));
        g2.fill(body);

        RoundRectangle2D flap = new RoundRectangle2D.Float(size * 0.52f, size * 0.44f, size * 0.32f, size * 0.18f, 6, 6);
        g2.setColor(new Color(126, 34, 206));
        g2.fill(flap);

        g2.setColor(new Color(251, 191, 36));
        g2.fillOval((int)(size * 0.58f), (int)(size * 0.48f), (int)(size * 0.10f), (int)(size * 0.10f));

        g2.dispose();
        saveImage(img, path);
    }

    // ==========================================
    // 6. DASHBOARD METRICS ICONS
    // ==========================================
    public static void generateTotalSlotsIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(238, 242, 255));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 12, 12);
        g2.setColor(new Color(79, 70, 229));
        g2.setStroke(new BasicStroke(2.5f));
        int s = (int)(size * 0.28);
        g2.drawRect((int)(size * 0.18), (int)(size * 0.18), s, s);
        g2.drawRect((int)(size * 0.54), (int)(size * 0.18), s, s);
        g2.drawRect((int)(size * 0.18), (int)(size * 0.54), s, s);
        g2.drawRect((int)(size * 0.54), (int)(size * 0.54), s, s);
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateAvailableSlotsIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(236, 253, 245));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 12, 12);
        g2.setColor(new Color(16, 185, 129));
        g2.fillOval((int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.60), (int)(size * 0.60));
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(size * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Float();
        p.moveTo(size * 0.35, size * 0.50);
        p.lineTo(size * 0.46, size * 0.62);
        p.lineTo(size * 0.65, size * 0.38);
        g2.draw(p);
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateOccupiedSlotsIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(254, 242, 242));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 12, 12);
        g2.setColor(new Color(239, 68, 68));
        g2.fillOval((int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.60), (int)(size * 0.60));
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(size * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int)(size * 0.36), (int)(size * 0.36), (int)(size * 0.64), (int)(size * 0.64));
        g2.drawLine((int)(size * 0.64), (int)(size * 0.36), (int)(size * 0.36), (int)(size * 0.64));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateRevenueIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(254, 243, 199));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 12, 12);
        g2.setColor(new Color(217, 119, 6));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int)(size * 0.55)));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("$", (size - fm.stringWidth("$")) / 2, (int)(size * 0.70));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateOccupancyIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(240, 249, 255));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 12, 12);
        g2.setColor(new Color(203, 213, 225));
        g2.setStroke(new BasicStroke(size * 0.10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc((int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.60), (int)(size * 0.60), 135, 270);
        g2.setColor(new Color(14, 165, 233));
        g2.drawArc((int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.60), (int)(size * 0.60), 135, 180);
        g2.dispose();
        saveImage(img, path);
    }

    // ==========================================
    // 7. NAVIGATION ICONS
    // ==========================================
    public static void generateNavDashboard(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(148, 163, 184));
        int s = (int)(size * 0.32);
        g2.fillRoundRect((int)(size * 0.12), (int)(size * 0.12), s, s, 4, 4);
        g2.fillRoundRect((int)(size * 0.56), (int)(size * 0.12), s, s, 4, 4);
        g2.fillRoundRect((int)(size * 0.12), (int)(size * 0.56), s, s, 4, 4);
        g2.fillRoundRect((int)(size * 0.56), (int)(size * 0.56), s, s, 4, 4);
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavEntry(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(16, 185, 129));
        g2.setStroke(new BasicStroke(size * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int)(size * 0.15), (int)(size * 0.50), (int)(size * 0.60), (int)(size * 0.50));
        g2.drawLine((int)(size * 0.45), (int)(size * 0.32), (int)(size * 0.60), (int)(size * 0.50));
        g2.drawLine((int)(size * 0.45), (int)(size * 0.68), (int)(size * 0.60), (int)(size * 0.50));
        g2.drawLine((int)(size * 0.80), (int)(size * 0.20), (int)(size * 0.80), (int)(size * 0.80));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavExit(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(239, 68, 68));
        g2.setStroke(new BasicStroke(size * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.80));
        g2.drawLine((int)(size * 0.40), (int)(size * 0.50), (int)(size * 0.85), (int)(size * 0.50));
        g2.drawLine((int)(size * 0.70), (int)(size * 0.32), (int)(size * 0.85), (int)(size * 0.50));
        g2.drawLine((int)(size * 0.70), (int)(size * 0.68), (int)(size * 0.85), (int)(size * 0.50));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavFloors(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(14, 165, 233));
        g2.setStroke(new BasicStroke(size * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            int y = (int)(size * (0.28 + i * 0.22));
            g2.drawLine((int)(size * 0.20), y, (int)(size * 0.80), y);
        }
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavReservation(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(245, 158, 11));
        g2.setStroke(new BasicStroke(size * 0.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawRoundRect((int)(size * 0.18), (int)(size * 0.22), (int)(size * 0.64), (int)(size * 0.62), 6, 6);
        g2.drawLine((int)(size * 0.18), (int)(size * 0.40), (int)(size * 0.82), (int)(size * 0.40));
        g2.drawLine((int)(size * 0.35), (int)(size * 0.14), (int)(size * 0.35), (int)(size * 0.26));
        g2.drawLine((int)(size * 0.65), (int)(size * 0.14), (int)(size * 0.65), (int)(size * 0.26));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavMembership(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(234, 179, 8));
        drawStar(g2, size / 2, size / 2, (int)(size * 0.16), (int)(size * 0.38));
        g2.dispose();
        saveImage(img, path);
    }

    private static void drawStar(Graphics2D g2, int cx, int cy, int innerR, int outerR) {
        Path2D star = new Path2D.Float();
        for (int i = 0; i < 10; i++) {
            double r = (i % 2 == 0) ? outerR : innerR;
            double angle = i * Math.PI / 5 - Math.PI / 2;
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.closePath();
        g2.fill(star);
    }

    public static void generateNavSearch(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(148, 163, 184));
        g2.setStroke(new BasicStroke(size * 0.09f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int r = (int)(size * 0.44);
        g2.drawOval((int)(size * 0.15), (int)(size * 0.15), r, r);
        g2.drawLine((int)(size * 0.52), (int)(size * 0.52), (int)(size * 0.82), (int)(size * 0.82));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavReports(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(99, 102, 241));
        int bw = (int)(size * 0.15);
        g2.fillRect((int)(size * 0.20), (int)(size * 0.55), bw, (int)(size * 0.30));
        g2.fillRect((int)(size * 0.43), (int)(size * 0.32), bw, (int)(size * 0.53));
        g2.fillRect((int)(size * 0.66), (int)(size * 0.18), bw, (int)(size * 0.67));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavUsers(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(148, 163, 184));
        g2.fillOval((int)(size * 0.34), (int)(size * 0.16), (int)(size * 0.32), (int)(size * 0.32));
        g2.fillArc((int)(size * 0.18), (int)(size * 0.48), (int)(size * 0.64), (int)(size * 0.50), 0, 180);
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateNavLogout(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(239, 68, 68));
        g2.setStroke(new BasicStroke(size * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int)(size * 0.55), (int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.20));
        g2.drawLine((int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.20), (int)(size * 0.80));
        g2.drawLine((int)(size * 0.20), (int)(size * 0.80), (int)(size * 0.55), (int)(size * 0.80));
        g2.drawLine((int)(size * 0.40), (int)(size * 0.50), (int)(size * 0.82), (int)(size * 0.50));
        g2.drawLine((int)(size * 0.65), (int)(size * 0.35), (int)(size * 0.82), (int)(size * 0.50));
        g2.drawLine((int)(size * 0.65), (int)(size * 0.65), (int)(size * 0.82), (int)(size * 0.50));
        g2.dispose();
        saveImage(img, path);
    }

    // ==========================================
    // 8. SLOT BADGES & AUTH
    // ==========================================
    public static void generateSlotStatusIcon(String path, int size, Color color, String label) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(color);
        g2.fillRoundRect(2, 2, size - 4, size - 4, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int)(size * 0.52)));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, (size - fm.stringWidth(label)) / 2, (int)(size * 0.68));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateEvBadge(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(14, 165, 233));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 8, 8);
        Path2D bolt = new Path2D.Float();
        bolt.moveTo(size * 0.55, size * 0.15);
        bolt.lineTo(size * 0.35, size * 0.48);
        bolt.lineTo(size * 0.50, size * 0.48);
        bolt.lineTo(size * 0.40, size * 0.85);
        bolt.lineTo(size * 0.68, size * 0.42);
        bolt.lineTo(size * 0.52, size * 0.42);
        bolt.closePath();
        g2.setColor(Color.WHITE);
        g2.fill(bolt);
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateHandicappedBadge(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(37, 99, 235));
        g2.fillRoundRect(2, 2, size - 4, size - 4, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(size * 0.08f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.fillOval((int)(size * 0.48), (int)(size * 0.18), (int)(size * 0.18), (int)(size * 0.18));
        g2.drawArc((int)(size * 0.28), (int)(size * 0.46), (int)(size * 0.36), (int)(size * 0.36), 110, 240);
        g2.drawLine((int)(size * 0.56), (int)(size * 0.38), (int)(size * 0.56), (int)(size * 0.60));
        g2.drawLine((int)(size * 0.56), (int)(size * 0.60), (int)(size * 0.72), (int)(size * 0.60));
        g2.drawLine((int)(size * 0.72), (int)(size * 0.60), (int)(size * 0.72), (int)(size * 0.78));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateUserIcon(String path, int size) {
        generateNavUsers(path, size);
    }

    public static void generatePasswordIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        g2.setColor(new Color(148, 163, 184));
        g2.setStroke(new BasicStroke(size * 0.08f));
        g2.drawArc((int)(size * 0.32), (int)(size * 0.16), (int)(size * 0.36), (int)(size * 0.38), 0, 180);
        g2.fillRoundRect((int)(size * 0.24), (int)(size * 0.38), (int)(size * 0.52), (int)(size * 0.48), 6, 6);
        g2.setColor(Color.WHITE);
        g2.fillOval((int)(size * 0.45), (int)(size * 0.52), (int)(size * 0.10), (int)(size * 0.10));
        g2.dispose();
        saveImage(img, path);
    }

    public static void generateShieldIcon(String path, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = createAntiAliasedGraphics(img);
        Path2D s = new Path2D.Float();
        s.moveTo(size * 0.20, size * 0.20);
        s.lineTo(size * 0.80, size * 0.20);
        s.lineTo(size * 0.80, size * 0.52);
        s.quadTo(size * 0.80, size * 0.85, size * 0.50, size * 0.90);
        s.quadTo(size * 0.20, size * 0.85, size * 0.20, size * 0.52);
        s.closePath();
        g2.setColor(new Color(16, 185, 129));
        g2.fill(s);
        g2.dispose();
        saveImage(img, path);
    }
}
