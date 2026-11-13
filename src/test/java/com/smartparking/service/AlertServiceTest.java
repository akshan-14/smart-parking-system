package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.alert.Alert;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.Attendant;
import com.smartparking.model.user.Customer;
import com.smartparking.model.user.User;
import com.smartparking.repository.*;
import com.smartparking.security.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Priority 3: AlertService Unit & Scheduled Detection Tests")
class AlertServiceTest {

    private AlertService alertService;
    private AlertDao alertDao;
    private TicketDao ticketDao;
    private ParkingSlotDao slotDao;
    private ReservationDao reservationDao;
    private MembershipDao membershipDao;
    private EvChargerDao chargerDao;
    private SystemSettingsDao settingsDao;
    private AuditLogService auditLogService;

    private User adminUser;
    private User attendantUser;
    private User customerUser;
    private final String dbUrl = "jdbc:sqlite:test_alert_service.db";

    @BeforeEach
    void setUp() {
        new File("test_alert_service.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(dbUrl);
        DatabaseInitializer.initializeDatabase();

        alertDao = new AlertDao();
        ticketDao = new TicketDao();
        slotDao = new ParkingSlotDao();
        reservationDao = new ReservationDao();
        membershipDao = new MembershipDao();
        chargerDao = new EvChargerDao();
        settingsDao = new SystemSettingsDao();
        auditLogService = AuditLogService.getInstance();

        alertService = new AlertService(alertDao, ticketDao, slotDao, reservationDao,
                membershipDao, chargerDao, settingsDao, auditLogService);

        adminUser = new Admin(1, "admin", "hash", "salt", "Admin", "admin@parking.in", "9876543210");
        attendantUser = new Attendant(2, "attendant1", "hash", "salt", "Attendant", "att@parking.in", "9876543211");
        customerUser = new Customer(3, "cust1", "hash", "salt", "Customer", "cust@parking.in", "9876543212");

        UserSession.getInstance().startSession(adminUser, 15);
    }

    @AfterEach
    void tearDown() {
        UserSession.getInstance().clearSession();
        new File("test_alert_service.db").delete();
    }

    @Test
    @DisplayName("Overstay detection reads threshold from settings and triggers alert")
    void testOverstayAlertGeneration() {
        // Set threshold to 12 hours
        settingsDao.set("alert.overstay_hours", "12", "Overstay threshold");

        // Create ticket parked 15 hours ago
        ParkingTicket tkt = new ParkingTicket(0, "TKT-ALERT-001", "UP32AL0001", VehicleType.CAR, "A2",
                LocalDateTime.now().minusHours(15), null, TicketStatus.ACTIVE, 50.0, 0.0, 0.0, 50.0);
        ticketDao.save(tkt);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-ALERT-001").orElseThrow();

        alertService.runScheduledAnomalyCheck();

        List<Alert> alerts = alertDao.findFiltered(null, "OVERSTAY", "OPEN", 10);
        assertFalse(alerts.isEmpty(), "Overstay alert must be created");
        assertEquals(String.valueOf(saved.getTicketId()), alerts.get(0).getEntityId());
        assertEquals("WARNING", alerts.get(0).getSeverity());
    }

    @Test
    @DisplayName("Alert deduplication: Repeated scan does not create duplicate open alert")
    void testAlertDeduplication() {
        ParkingTicket tkt = new ParkingTicket(0, "TKT-ALERT-002", "UP32AL0002", VehicleType.CAR, "A3",
                LocalDateTime.now().minusHours(30), null, TicketStatus.ACTIVE, 50.0, 0.0, 0.0, 50.0);
        ticketDao.save(tkt);

        // Run detection multiple times
        alertService.runScheduledAnomalyCheck();
        alertService.runScheduledAnomalyCheck();
        alertService.runScheduledAnomalyCheck();

        List<Alert> alerts = alertDao.findFiltered(null, "OVERSTAY", "OPEN", 10);
        long count = alerts.stream()
                .filter(a -> a.getMessage().contains("UP32AL0002"))
                .count();

        assertEquals(1, count, "Only exactly one open alert should exist for the same entity and type");
    }

    @Test
    @DisplayName("Status lifecycle: OPEN -> ACKNOWLEDGED -> RESOLVED with resolution note")
    void testAlertLifecycleTransitions() {
        Alert alert = alertDao.createAlert("MAINTENANCE_BAY", "WARNING", "SLOT", "B10", "Slot B10 sensor down");
        assertEquals("OPEN", alert.getStatus());

        // Acknowledge by Attendant
        alertService.acknowledgeAlert(alert.getId(), attendantUser);
        Alert acked = alertDao.findById(alert.getId()).orElseThrow();
        assertEquals("ACKNOWLEDGED", acked.getStatus());
        assertEquals("attendant1", acked.getAcknowledgedBy());
        assertNotNull(acked.getAcknowledgedAt());

        // Resolve by Admin with note
        alertService.resolveAlert(alert.getId(), adminUser, "Replaced ultrasonic barrier sensor");
        Alert resolved = alertDao.findById(alert.getId()).orElseThrow();
        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals("admin", resolved.getResolvedBy());
        assertEquals("Replaced ultrasonic barrier sensor", resolved.getResolutionNote());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    @DisplayName("Auto-resolves alert when underlying condition clears")
    void testAutoResolveWhenConditionClears() {
        // Slot B12 under maintenance triggers alert
        slotDao.updateStatus("B12", "MAINTENANCE", null);
        alertService.runScheduledAnomalyCheck();

        List<Alert> alerts = alertDao.findFiltered(null, "MAINTENANCE_BAY", "OPEN", 10);
        assertTrue(alerts.stream().anyMatch(a -> "B12".equals(a.getEntityId())));

        // Slot repaired back to AVAILABLE
        slotDao.updateStatus("B12", "AVAILABLE", null);
        alertService.runScheduledAnomalyCheck();

        List<Alert> openAlerts = alertDao.findFiltered(null, "MAINTENANCE_BAY", "OPEN", 10);
        assertFalse(openAlerts.stream().anyMatch(a -> "B12".equals(a.getEntityId())),
                "Maintenance alert for B12 must be auto-resolved");
    }

    @Test
    @DisplayName("Customer role is denied from acknowledging or resolving alerts")
    void testCustomerDeniedFromAlertActions() {
        Alert alert = alertDao.createAlert("OVERSTAY", "WARNING", "TICKET", "100", "Overstay ticket 100");

        assertThrows(SecurityException.class, () -> {
            alertService.acknowledgeAlert(alert.getId(), customerUser);
        });

        assertThrows(SecurityException.class, () -> {
            alertService.resolveAlert(alert.getId(), customerUser, "Customer try to resolve");
        });
    }
}
