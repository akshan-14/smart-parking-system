package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.ReservationDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reservation Overlap Validation Tests")
class ReservationOverlapValidationTest {

    private static ReservationDao reservationDao;
    private static ReservationService reservationService;

    @BeforeAll
    static void setUp() {
        String testDb = "jdbc:sqlite:test_overlap.db";
        new File("test_overlap.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);
        DatabaseInitializer.initializeDatabase();

        reservationDao = new ReservationDao();
        ParkingSlotDao slotDao = new ParkingSlotDao();
        reservationService = new ReservationService(reservationDao, slotDao);
    }

    @Test
    @DisplayName("Overlapping reservation windows on the same bay are strictly rejected")
    void testOverlapDetection() {
        LocalDateTime baseTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime baseEnd = baseTime.plusHours(2); // 10:00 to 12:00

        // Create base reservation on available regular bay A25
        Reservation res1 = reservationService.makeReservation(1, "A25", "UP32AA1111", VehicleType.CAR, baseTime, baseEnd);
        assertNotNull(res1);

        // 1. Exact match overlap: 10:00 to 12:00
        assertTrue(reservationDao.hasOverlap("A25", baseTime, baseEnd));

        // 2. Overlapping start: 09:30 to 10:30
        assertTrue(reservationDao.hasOverlap("A25", baseTime.minusMinutes(30), baseTime.plusMinutes(30)));

        // 3. Overlapping end: 11:30 to 12:30
        assertTrue(reservationDao.hasOverlap("A25", baseEnd.minusMinutes(30), baseEnd.plusMinutes(30)));

        // 4. Encompassing: 09:00 to 13:00
        assertTrue(reservationDao.hasOverlap("A25", baseTime.minusHours(1), baseEnd.plusHours(1)));

        // 5. Completely inside: 10:30 to 11:30
        assertTrue(reservationDao.hasOverlap("A25", baseTime.plusMinutes(30), baseEnd.minusMinutes(30)));

        // 6. Before (Disjoint): 08:00 to 10:00 (ends at start)
        assertFalse(reservationDao.hasOverlap("A25", baseTime.minusHours(2), baseTime));

        // 7. After (Disjoint): 12:00 to 14:00 (starts at end)
        assertFalse(reservationDao.hasOverlap("A25", baseEnd, baseEnd.plusHours(2)));

        // 8. Different bay (Disjoint): same time on A26
        assertFalse(reservationDao.hasOverlap("A26", baseTime, baseEnd));
    }
}
