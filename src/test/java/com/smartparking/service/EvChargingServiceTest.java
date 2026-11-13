package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ev.ChargingSession;
import com.smartparking.model.ev.EvCharger;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.User;
import com.smartparking.repository.*;
import com.smartparking.security.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Priority 2: EV Charging Service Unit & Business Logic Tests")
class EvChargingServiceTest {

    private EvChargingService evChargingService;
    private EvChargerDao chargerDao;
    private ChargingSessionDao sessionDao;
    private TicketDao ticketDao;
    private ParkingSlotDao slotDao;
    private SystemSettingsDao settingsDao;
    private AuditLogService auditLogService;

    private User adminUser;
    private final String dbUrl = "jdbc:sqlite:test_ev_service.db";

    @BeforeEach
    void setUp() {
        new File("test_ev_service.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(dbUrl);
        DatabaseInitializer.initializeDatabase();

        chargerDao = new EvChargerDao();
        sessionDao = new ChargingSessionDao();
        ticketDao = new TicketDao();
        slotDao = new ParkingSlotDao();
        settingsDao = new SystemSettingsDao();
        auditLogService = AuditLogService.getInstance();

        evChargingService = new EvChargingService(chargerDao, sessionDao, ticketDao, slotDao, settingsDao, auditLogService);

        adminUser = new Admin(1, "admin", "hash", "salt", "Admin", "admin@parking.in", "9876543210");
        UserSession.getInstance().startSession(adminUser, 15);
    }

    @AfterEach
    void tearDown() {
        UserSession.getInstance().clearSession();
        new File("test_ev_service.db").delete();
    }

    @Test
    @DisplayName("Starting EV charging on a non-EV bay is rejected")
    void testStartChargingOnNonEvBayRejected() {
        // Slot A1 is a regular bay (not ELECTRIC)
        ParkingTicket ticket = new ParkingTicket(0, "TKT-EV-001", "UP32EV0001", VehicleType.EV_CAR, "A1",
                LocalDateTime.now().minusHours(1), null, TicketStatus.ACTIVE, 30.0, 0.0, 0.0, 30.0);
        ticketDao.save(ticket);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-EV-001").orElseThrow();

        // Try to start with charger 1 (which belongs to A43)
        assertThrows(IllegalArgumentException.class, () -> {
            evChargingService.startCharging(saved.getTicketId(), 1, adminUser);
        });
    }

    @Test
    @DisplayName("Starting EV charging for a non-EV vehicle (petrol/diesel car) is rejected")
    void testStartChargingForNonEvVehicleRejected() {
        // Bay A43 is EV bay, but vehicle is standard petrol CAR
        ParkingTicket ticket = new ParkingTicket(0, "TKT-EV-002", "UP32AB9991", VehicleType.CAR, "A43",
                LocalDateTime.now().minusHours(1), null, TicketStatus.ACTIVE, 30.0, 0.0, 0.0, 30.0);
        ticketDao.save(ticket);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-EV-002").orElseThrow();

        EvCharger charger = chargerDao.findBySlotId("A43").orElseThrow();

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);
        });
        assertTrue(ex.getMessage().contains("not an Electric Vehicle"));
    }

    @Test
    @DisplayName("Starting charging on a FAULT or OFFLINE charger is rejected")
    void testStartChargingOnFaultyChargerRejected() {
        ParkingTicket ticket = new ParkingTicket(0, "TKT-EV-003", "UP32EV0003", VehicleType.EV_CAR, "A43",
                LocalDateTime.now().minusHours(1), null, TicketStatus.ACTIVE, 30.0, 0.0, 0.0, 30.0);
        ticketDao.save(ticket);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-EV-003").orElseThrow();

        EvCharger charger = chargerDao.findBySlotId("A43").orElseThrow();
        chargerDao.updateStatus(charger.getId(), "FAULT");

        Exception ex = assertThrows(IllegalStateException.class, () -> {
            evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);
        });
        assertTrue(ex.getMessage().contains("FAULT"));

        chargerDao.updateStatus(charger.getId(), "OFFLINE");
        assertThrows(IllegalStateException.class, () -> {
            evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);
        });
    }

    @Test
    @DisplayName("Starting charging twice for the same ticket is blocked")
    void testBlockDuplicateActiveSession() {
        ParkingTicket ticket = new ParkingTicket(0, "TKT-EV-004", "UP32EV0004", VehicleType.EV_CAR, "A43",
                LocalDateTime.now().minusHours(1), null, TicketStatus.ACTIVE, 30.0, 0.0, 0.0, 30.0);
        ticketDao.save(ticket);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-EV-004").orElseThrow();

        EvCharger charger = chargerDao.findBySlotId("A43").orElseThrow();

        ChargingSession session1 = evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);
        assertNotNull(session1);
        assertTrue(session1.isActive());

        // Try to start second session on same ticket
        assertThrows(IllegalStateException.class, () -> {
            evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);
        });
    }

    @Test
    @DisplayName("Energy kWh simulation, cost calculation, and charger status transition")
    void testChargingEnergyCalculationAndCompletion() {
        ParkingTicket ticket = new ParkingTicket(0, "TKT-EV-005", "UP32EV0005", VehicleType.EV_CAR, "A43",
                LocalDateTime.now().minusHours(2), null, TicketStatus.ACTIVE, 50.0, 0.0, 0.0, 50.0);
        ticketDao.save(ticket);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-EV-005").orElseThrow();

        EvCharger charger = chargerDao.findBySlotId("A43").orElseThrow();
        ChargingSession session = evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);

        // Charger is now CHARGING
        EvCharger updatedCharger = chargerDao.findById(charger.getId()).orElseThrow();
        assertEquals("CHARGING", updatedCharger.getStatus());

        // Stop charging
        ChargingSession completed = evChargingService.stopCharging(session.getId(), adminUser);
        assertTrue(completed.isCompleted());
        assertTrue(completed.getKwh() > 0.0, "Simulated kWh must be greater than zero");
        assertTrue(completed.getEnergyCharge() > 0.0, "Energy charge must be computed");

        // Charger status restored to AVAILABLE
        updatedCharger = chargerDao.findById(charger.getId()).orElseThrow();
        assertEquals("AVAILABLE", updatedCharger.getStatus());
    }

    @Test
    @DisplayName("Idle fee is calculated when vehicle remains plugged in beyond grace period")
    void testIdleFeeCalculation() {
        ParkingTicket ticket = new ParkingTicket(0, "TKT-EV-006", "UP32EV0006", VehicleType.EV_CAR, "A43",
                LocalDateTime.now().minusHours(3), null, TicketStatus.ACTIVE, 50.0, 0.0, 0.0, 50.0);
        ticketDao.save(ticket);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-EV-006").orElseThrow();

        EvCharger charger = chargerDao.findBySlotId("A43").orElseThrow();
        ChargingSession session = evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);
        ChargingSession completed = evChargingService.stopCharging(session.getId(), adminUser);

        // Manually set endTime to 45 minutes ago (grace period is 15 mins -> 30 mins excess)
        LocalDateTime completionTime = LocalDateTime.now().minusMinutes(45);
        completed.setEndTime(completionTime);

        LocalDateTime checkoutTime = LocalDateTime.now();
        double idleFee = evChargingService.calculateIdleFee(completed, checkoutTime);

        // 45 - 15 = 30 minutes excess * ₹2.0/min = ₹60.00
        assertEquals(60.0, idleFee, 1.0);
        assertEquals(60.0, completed.getIdleFee(), 1.0);
    }

    @Test
    @DisplayName("Active charging session blocks vehicle checkout")
    void testActiveChargingBlocksCheckout() {
        ParkingTicket ticket = new ParkingTicket(0, "TKT-EV-007", "UP32EV0007", VehicleType.EV_CAR, "A43",
                LocalDateTime.now().minusHours(1), null, TicketStatus.ACTIVE, 30.0, 0.0, 0.0, 30.0);
        ticketDao.save(ticket);
        ParkingTicket saved = ticketDao.findByTicketNumber("TKT-EV-007").orElseThrow();

        EvCharger charger = chargerDao.findBySlotId("A43").orElseThrow();
        evChargingService.startCharging(saved.getTicketId(), charger.getId(), adminUser);

        assertTrue(evChargingService.isChargingActive(saved.getTicketId()));
    }
}
