package com.smartparking.service;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.ReservationStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.ReservationDao;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reservation No-Show Expiration Tests")
class ReservationExpiryTest {

    private static ReservationService reservationService;
    private static ReservationDao reservationDao;
    private static ParkingSlotDao slotDao;

    /** ReservationService starts a 30 s auto-expiry daemon; stop it so it cannot write into later test classes' databases. */
    @AfterAll
    static void stopBackgroundExpiry() {
        if (reservationService != null) reservationService.stopAutoExpiryScheduler();
    }

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_reservation_expiry");
        DatabaseInitializer.initializeDatabase();

        reservationDao = new ReservationDao();
        slotDao = new ParkingSlotDao();
        reservationService = new ReservationService(reservationDao, slotDao);
    }

    @Test
    @DisplayName("Purge cancels no-show reservation past start time and releases slot")
    void testPurgeExpiredReservation() {
        // Create an overdue reservation: started 60 mins ago
        LocalDateTime pastStart = LocalDateTime.now().minusMinutes(60);
        LocalDateTime pastEnd = pastStart.plusHours(2);

        Reservation res = reservationService.makeReservation(3, "B3", "UP32EX1212",
                VehicleType.CAR, pastStart, pastEnd);
        assertNotNull(res);
        assertEquals(ReservationStatus.CONFIRMED, res.getStatus());

        // Verify slot is RESERVED
        Optional<ParkingSlot> reservedSlot = slotDao.findById("B3");
        assertTrue(reservedSlot.isPresent());
        assertEquals("RESERVED", reservedSlot.get().getStatus());

        // Run purge with 15 minutes grace period (cutoff = now - 15m; 60m ago < cutoff, so expired)
        int purgedCount = reservationService.purgeExpiredReservations(15);
        assertTrue(purgedCount >= 1, "At least 1 reservation must be purged");

        // Verify reservation status in DB
        Optional<Reservation> purgedRes = reservationDao.findByCode(res.getReservationCode());
        assertTrue(purgedRes.isPresent());
        assertEquals(ReservationStatus.EXPIRED, purgedRes.get().getStatus());

        // Verify slot is now AVAILABLE again
        Optional<ParkingSlot> freedSlot = slotDao.findById("B3");
        assertTrue(freedSlot.isPresent());
        assertEquals("AVAILABLE", freedSlot.get().getStatus());
    }

    @Test
    @DisplayName("Future reservation is NOT purged by purgeExpiredReservations")
    void testFutureReservationNotPurged() {
        LocalDateTime futureStart = LocalDateTime.now().plusHours(2);
        LocalDateTime futureEnd = futureStart.plusHours(2);

        Reservation futureRes = reservationService.makeReservation(3, "C1", "UP16FT4321",
                VehicleType.CAR, futureStart, futureEnd);
        assertNotNull(futureRes);

        int purgedCount = reservationService.purgeExpiredReservations(15);
        // The future reservation should remain CONFIRMED
        Optional<Reservation> check = reservationDao.findByCode(futureRes.getReservationCode());
        assertTrue(check.isPresent());
        assertEquals(ReservationStatus.CONFIRMED, check.get().getStatus());
    }
}
