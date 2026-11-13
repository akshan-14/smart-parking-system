package com.smartparking.ui;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.service.ParkingService;
import com.smartparking.ui.panels.DashboardPanel;
import com.smartparking.ui.panels.FloorMapPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Visual Render Verification for Dashboard and Floor Map")
class VisualVerificationTest {

    private static ParkingLot parkingLot;
    private static ParkingService parkingService;

    @BeforeAll
    static void setUp() {
        String testDb = "jdbc:sqlite:test_visual_verification.db";
        new File("test_visual_verification.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);
        DatabaseInitializer.initializeDatabase();

        parkingLot = new ParkingLot("LOT-001", "Grand Tech Park Garage", "100 Innovation Blvd, Uttar Pradesh");
        parkingLot.addFloor(new ParkingFloor(1, "Floor 1 (Ground)"));
        parkingLot.addFloor(new ParkingFloor(2, "Floor 2 (EV Hub)"));
        parkingLot.addFloor(new ParkingFloor(3, "Floor 3 (Fleet & VIP)"));
        parkingService = ParkingService.createDefault(parkingLot);
    }

    private static void layoutHierarchy(java.awt.Component comp) {
        comp.doLayout();
        if (comp instanceof java.awt.Container cont) {
            for (java.awt.Component child : cont.getComponents()) {
                layoutHierarchy(child);
            }
        }
    }

    @Test
    @DisplayName("Render DashboardPanel to image and verify UI components")
    void testRenderDashboardPanel() throws Exception {
        DashboardPanel panel = new DashboardPanel(parkingLot, parkingService, key -> {});
        panel.setSize(1366, 768);
        layoutHierarchy(panel);

        BufferedImage img = new BufferedImage(1366, 768, BufferedImage.TYPE_INT_RGB);
        var g2 = img.createGraphics();
        g2.setColor(panel.getBackground());
        g2.fillRect(0, 0, 1366, 768);
        panel.paint(g2);
        g2.dispose();

        File out = new File(System.getProperty("java.io.tmpdir"), "dashboard_rendered.png");
        ImageIO.write(img, "png", out);
        assertTrue(out.exists(), "dashboard_rendered.png must be saved successfully");
    }

    @Test
    @DisplayName("Render FloorMapPanel to image and verify CAD Blueprint")
    void testRenderFloorMapPanel() throws Exception {
        FloorMapPanel panel = new FloorMapPanel(parkingLot, parkingService, key -> {});
        panel.setSize(1366, 768);
        layoutHierarchy(panel);

        BufferedImage img = new BufferedImage(1366, 768, BufferedImage.TYPE_INT_RGB);
        var g2 = img.createGraphics();
        g2.setColor(panel.getBackground());
        g2.fillRect(0, 0, 1366, 768);
        panel.paint(g2);
        g2.dispose();

        File out = new File(System.getProperty("java.io.tmpdir"), "floormap_rendered.png");
        ImageIO.write(img, "png", out);
        assertTrue(out.exists(), "floormap_rendered.png must be saved successfully");
    }
}
