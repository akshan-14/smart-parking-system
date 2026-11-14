package com.smartparking.concurrency;

import com.smartparking.support.TestDatabase;
import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.vehicle.Car;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.service.ParkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: High-Concurrency Multi-Gate Slot Allocation & Data Integrity Tests")
public class SlotAllocationConcurrencyTest {

    /** Isolated throw-away database: never touch the real smart_parking.db or another class's file. */
    @BeforeAll
    static void isolateDatabase() {
        TestDatabase.useFresh("test_slot_concurrency");
    }

    private ParkingLot parkingLot;
    private ParkingService parkingService;
    private ParkingSlotDao slotDao;
    private TicketDao ticketDao;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseInitializer.initializeDatabase();
        this.slotDao = new ParkingSlotDao();
        this.ticketDao = new TicketDao();

        // 1. Prepare exactly 5 free AVAILABLE REGULAR slots, setting all other slots to MAINTENANCE
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM invoices;");
            stmt.execute("DELETE FROM payments;");
            stmt.execute("DELETE FROM penalties;");
            stmt.execute("DELETE FROM tickets;");
            stmt.execute("DELETE FROM reservations;");
            stmt.execute("UPDATE slots SET status = 'MAINTENANCE', current_vehicle_plate = NULL;");
            stmt.execute("UPDATE slots SET status = 'AVAILABLE' WHERE slot_id IN ('A11', 'A12', 'A13', 'A14', 'A15');");
        }

        // 2. Build in-memory ParkingLot mirroring SQLite
        this.parkingLot = new ParkingLot("LOT-CONC", "Concurrency Test Lot", "Expressway Hub");
        List<ParkingSlot> allSlots = slotDao.findAll();

        ParkingFloor f1 = new ParkingFloor(1, "Ground Floor");
        ParkingFloor f2 = new ParkingFloor(2, "Level 2");
        ParkingFloor f3 = new ParkingFloor(3, "Level 3");

        for (ParkingSlot s : allSlots) {
            switch (s.getFloorNumber()) {
                case 1 -> f1.addSlot(s);
                case 2 -> f2.addSlot(s);
                case 3 -> f3.addSlot(s);
            }
        }
        parkingLot.addFloor(f1);
        parkingLot.addFloor(f2);
        parkingLot.addFloor(f3);

        this.parkingService = ParkingService.createDefault(parkingLot);
    }

    @Test
    @DisplayName("20 threads concurrently competing for 5 slots: exactly 5 succeed, 15 fail, zero double-parking")
    void testTwentyThreadsCompetingForFiveSlots() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        List<ParkingTicket> successfulTickets = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> failureReasons = Collections.synchronizedList(new ArrayList<>());

        for (int i = 1; i <= threadCount; i++) {
            final String plate = String.format("UP32CC%04d", i);
            executor.submit(() -> {
                try {
                    // Wait until all 20 threads are primed to fire simultaneously
                    startGate.await();
                    ParkingTicket ticket = parkingService.processVehicleEntry(new Car(plate));
                    successfulTickets.add(ticket);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                    failureReasons.add(ex.getMessage());
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Release the barrier: all 20 threads execute concurrently
        startGate.countDown();
        boolean completedInTime = endGate.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completedInTime, "All 20 concurrent threads should finish within 15 seconds");
        assertEquals(5, successCount.get(), "Exactly 5 threads must succeed in acquiring the 5 available slots");
        assertEquals(15, failureCount.get(), "Exactly 15 threads must fail when capacity is exhausted");

        // Verify that all 15 failures received the standard capacity exhaustion message
        for (String reason : failureReasons) {
            assertTrue(reason.contains("No suitable slot available") || reason.contains("already parked"),
                    "Expected slot exhaustion error but received: " + reason);
        }

        // Verify no duplicate slot assignments (each successful ticket got a distinct slot)
        Set<String> assignedSlots = new HashSet<>();
        Set<String> assignedPlates = new HashSet<>();
        for (ParkingTicket t : successfulTickets) {
            assertNotNull(t.getSlotId());
            assertTrue(assignedSlots.add(t.getSlotId()), "Duplicate slot assignment detected: " + t.getSlotId());
            assertTrue(assignedPlates.add(t.getLicensePlate()), "Duplicate plate assignment detected: " + t.getLicensePlate());
        }

        // Verify persistent SQLite slots state: 0 available slots remaining among candidate bays
        assertEquals(0, slotDao.countAvailableSlots(), "Zero available slots must remain in database");
    }

    @Test
    @DisplayName("Active ticket uniqueness constraint blocks double parking for identical plate")
    void testActiveTicketPlateUniquenessConstraint() {
        parkingService.processVehicleEntry(new Car("UP32UN1111"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                parkingService.processVehicleEntry(new Car("UP32UN1111")));

        assertTrue(ex.getMessage().contains("already parked"));
    }
}
