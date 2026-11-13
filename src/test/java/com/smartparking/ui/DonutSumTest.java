package com.smartparking.ui;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.TicketDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Donut Chart Vehicle Distribution Sum Validation")
class DonutSumTest {

    private static ParkingSlotDao slotDao;
    private static TicketDao ticketDao;

    @BeforeAll
    static void setUp() {
        String testDb = "jdbc:sqlite:test_donut_sum.db";
        new File("test_donut_sum.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);
        DatabaseInitializer.initializeDatabase();
        slotDao = new ParkingSlotDao();
        ticketDao = new TicketDao();
    }

    @Test
    @DisplayName("Donut chart category counts strictly sum to occupied count")
    void testDonutSumMatchesOccupiedSlots() {
        int occupiedInSlots = slotDao.countByStatus("OCCUPIED");
        Map<VehicleType, Integer> typeCounts = ticketDao.getOccupiedVehicleTypeCounts();

        int sumCategories = typeCounts.values().stream().mapToInt(Integer::intValue).sum();

        // In test database seed, active tickets correspond directly to occupied slots
        assertEquals(occupiedInSlots, sumCategories,
                "Donut chart category counts must strictly equal the occupied slot count (" + occupiedInSlots + ")");
    }
}
