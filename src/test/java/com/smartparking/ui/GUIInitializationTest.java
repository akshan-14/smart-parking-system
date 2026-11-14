package com.smartparking.ui;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.User;
import com.smartparking.service.ParkingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GUIInitializationTest {

    /** Isolated throw-away database: never touch the real smart_parking.db or another class's file. */
    @BeforeAll
    static void isolateDatabase() {
        TestDatabase.useFresh("test_gui_initialization");
    }

    @Test
    void testGUIInitialization() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("[GUI TEST] Running in headless environment; skipping window peer realization.");
            return;
        }

        SwingUtilities.invokeAndWait(() -> {
            LoginFrame loginFrame = new LoginFrame();
            assertNotNull(loginFrame.getContentPane());
            loginFrame.dispose();

            ParkingLot lot = new ParkingLot("LOT-001", "Test Garage", "Test Address");
            lot.addFloor(new ParkingFloor(1, "Floor 1"));
            ParkingService parkingService = ParkingService.createDefault(lot);
            User admin = new Admin(1, "admin", "hash", "Admin User", "admin@test.com", "1234567890");

            SmartParkingMainFrame mainFrame = new SmartParkingMainFrame(admin, lot, parkingService);
            assertNotNull(mainFrame.getContentPane());

            // Test navigation to all cards
            mainFrame.navigateTo("DASHBOARD");
            mainFrame.navigateTo("FLOORS");
            mainFrame.navigateTo("ENTRY");
            mainFrame.navigateTo("EXIT");
            mainFrame.navigateTo("RESERVATIONS");
            mainFrame.navigateTo("MEMBERSHIP");
            mainFrame.navigateTo("SEARCH");
            mainFrame.navigateTo("REPORTS");

            mainFrame.dispose();
        });
    }
}
