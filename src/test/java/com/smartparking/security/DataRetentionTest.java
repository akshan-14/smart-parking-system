package com.smartparking.security;

import com.smartparking.support.TestDatabase;
import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.patterns.builder.TicketBuilder;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.TicketDao;
import com.smartparking.service.DataRetentionService;
import com.smartparking.util.DateFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: DPDP Act 2023 Data Retention & Anonymization Tests")
public class DataRetentionTest {

    /** Isolated throw-away database: never touch the real smart_parking.db or another class's file. */
    @BeforeAll
    static void isolateDatabase() {
        TestDatabase.useFresh("test_data_retention");
    }

    private TicketDao ticketDao;
    private DataRetentionService retentionService;

    @BeforeEach
    void setUp() {
        DatabaseInitializer.initializeDatabase();
        this.ticketDao = new TicketDao();
        this.retentionService = new DataRetentionService();
    }

    @Test
    @DisplayName("Tickets older than 365 days are anonymized while preserving amounts and recent tickets are untouched")
    void testHistoricalTicketAnonymization() throws Exception {
        // Create an old ticket (400 days ago, exited)
        LocalDateTime oldEntry = LocalDateTime.now().minusDays(400);
        LocalDateTime oldExit = LocalDateTime.now().minusDays(399);

        ParkingTicket oldTicket = TicketBuilder.newInstance()
                .setLicensePlate("UP32OLD123")
                .setVehicleType(VehicleType.CAR)
                .setSlotId("A1")
                .setFloorNumber(1)
                .setEntryTime(oldEntry)
                .setStatus(TicketStatus.EXITED)
                .build();
        oldTicket.setExitTime(oldExit);
        oldTicket.setTotalAmount(120.0);

        int oldTicketId = ticketDao.save(oldTicket);
        // Ensure exit_time in DB
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE tickets SET exit_time = ?, total_amount = 120.0 WHERE ticket_id = ?;")) {
            ps.setString(1, DateFormat.toDbString(oldExit));
            ps.setInt(2, oldTicketId);
            ps.executeUpdate();
        }

        // Create a recent ticket (5 days ago, exited)
        LocalDateTime recentExit = LocalDateTime.now().minusDays(5);
        ParkingTicket recentTicket = TicketBuilder.newInstance()
                .setLicensePlate("UP32NEW456")
                .setVehicleType(VehicleType.CAR)
                .setSlotId("A2")
                .setFloorNumber(1)
                .setEntryTime(LocalDateTime.now().minusDays(6))
                .setStatus(TicketStatus.EXITED)
                .build();
        recentTicket.setExitTime(recentExit);
        recentTicket.setTotalAmount(80.0);

        int recentTicketId = ticketDao.save(recentTicket);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE tickets SET exit_time = ?, total_amount = 80.0 WHERE ticket_id = ?;")) {
            ps.setString(1, DateFormat.toDbString(recentExit));
            ps.setInt(2, recentTicketId);
            ps.executeUpdate();
        }

        // Run DPDP Retention Job
        DataRetentionService.RetentionReport report = retentionService.runRetentionJob();
        assertTrue(report.ticketsAnonymized() >= 1, "At least 1 old ticket should be anonymized");

        // Verify old ticket was anonymized to masked token while preserving financial amount
        ParkingTicket checkedOld = ticketDao.findById(oldTicketId).orElseThrow();
        assertEquals("UP00XX0000", checkedOld.getLicensePlate(), "Old plate must be anonymized to UP00XX0000");
        assertEquals(120.0, checkedOld.getTotalAmount(), 0.01, "Accounting amount must be preserved");

        // Verify recent ticket remains untouched
        ParkingTicket checkedRecent = ticketDao.findById(recentTicketId).orElseThrow();
        assertEquals("UP32NEW456", checkedRecent.getLicensePlate(), "Recent plate must not be modified");
        assertEquals(80.0, checkedRecent.getTotalAmount(), 0.01);
    }
}
