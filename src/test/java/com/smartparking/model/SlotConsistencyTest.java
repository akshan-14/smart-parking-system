package com.smartparking.model;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.util.PlateUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test Suite verifying Data Consistency across the entire Smart Parking System.
 * Validates Phase 1 requirements:
 * 1. Single unified 150-slot layout across 3 floors (50 per floor).
 * 2. Mathematical invariant: Available + Occupied + Reserved + Maintenance == Total Capacity (150).
 * 3. Donut chart vehicle distribution sum strictly equals occupied vehicle count.
 * 4. Active vehicle plates conform to Uttar Pradesh (UP) registration format.
 */
@DisplayName("Slot Data Consistency & Overview Math Invariant Tests")
class SlotConsistencyTest {

    private static ParkingSlotDao slotDao;
    private static TicketDao ticketDao;

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_slot_consistency");
        DatabaseInitializer.initializeDatabase();

        slotDao = new ParkingSlotDao();
        ticketDao = new TicketDao();
    }

    @Test
    @DisplayName("Should verify exactly 150 slots seeded across 3 floors (50 per floor)")
    void testTotalSlotCapacity() {
        int total = slotDao.countTotalSlots();
        assertEquals(150, total, "Database slots table must contain exactly 150 slots");

        List<ParkingSlot> floor1 = slotDao.findByFloor(1);
        List<ParkingSlot> floor2 = slotDao.findByFloor(2);
        List<ParkingSlot> floor3 = slotDao.findByFloor(3);

        assertEquals(50, floor1.size(), "Floor 1 must contain exactly 50 bays (A1..A50)");
        assertEquals(50, floor2.size(), "Floor 2 must contain exactly 50 bays (B1..B50)");
        assertEquals(50, floor3.size(), "Floor 3 must contain exactly 50 bays (C1..C50)");
    }

    @Test
    @DisplayName("Should verify math invariant: Available + Occupied + Reserved + Maintenance == 150")
    void testOverviewMathInvariant() {
        int total = slotDao.countTotalSlots();
        int available = slotDao.countByStatus("AVAILABLE");
        int occupied = slotDao.countByStatus("OCCUPIED");
        int reserved = slotDao.countByStatus("RESERVED");
        int maintenance = slotDao.countByStatus("MAINTENANCE");

        assertTrue(available > 0, "Available bays must be greater than zero");
        assertTrue(occupied > 0, "Occupied bays must be greater than zero");
        assertTrue(reserved > 0, "Reserved bays must be greater than zero");
        assertTrue(maintenance > 0, "Maintenance bays must be greater than zero");

        int sum = available + occupied + reserved + maintenance;
        assertEquals(total, sum, String.format("Invariant violation: %d (Avail) + %d (Occ) + %d (Res) + %d (Maint) != %d (Total)",
                available, occupied, reserved, maintenance, total));

        // Percentage check
        double occPct = ((double) occupied / total) * 100.0;
        double availPct = ((double) available / total) * 100.0;
        double resPct = ((double) reserved / total) * 100.0;
        double maintPct = ((double) maintenance / total) * 100.0;

        assertEquals(100.0, occPct + availPct + resPct + maintPct, 0.01, "Sum of percentages must equal 100.0%");
    }

    @Test
    @DisplayName("Should verify Donut Chart vehicle type sum strictly matches occupied count")
    void testVehicleTypeDistributionMatchesOccupiedCount() {
        int occupiedSlots = slotDao.countByStatus("OCCUPIED");
        Map<VehicleType, Integer> distribution = ticketDao.getOccupiedVehicleTypeCounts();

        int cars = distribution.getOrDefault(VehicleType.CAR, 0) + distribution.getOrDefault(VehicleType.AUTO_RICKSHAW, 0);
        int bikes = distribution.getOrDefault(VehicleType.MOTORCYCLE, 0) + distribution.getOrDefault(VehicleType.EV_TWO_WHEELER, 0) + distribution.getOrDefault(VehicleType.BIKE, 0);
        int trucks = distribution.getOrDefault(VehicleType.HCV, 0) + distribution.getOrDefault(VehicleType.LCV, 0) + distribution.getOrDefault(VehicleType.TRUCK, 0);
        int evs = distribution.getOrDefault(VehicleType.EV_CAR, 0) + distribution.getOrDefault(VehicleType.ELECTRIC_CAR, 0);

        int totalParked = cars + bikes + trucks + evs;
        assertEquals(occupiedSlots, totalParked,
                String.format("Donut sum (%d) must exactly equal occupied slot count (%d)", totalParked, occupiedSlots));
    }

    @Test
    @DisplayName("Should verify all active parked vehicles carry Uttar Pradesh (UP) license plates")
    void testUttarPradeshPlates() {
        List<ParkingTicket> activeTickets = ticketDao.findAllActive();
        assertFalse(activeTickets.isEmpty(), "Active tickets must be seeded");

        for (ParkingTicket t : activeTickets) {
            String plate = t.getLicensePlate();
            assertTrue(PlateUtil.isValid(plate), "License plate '" + plate + "' must be a valid UP or Bharat plate");
        }
    }
}
