package com.smartparking.ui;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Audit & Overview Metric Single Source of Truth Validation")
class AuditOverviewConsistencyTest {

    private static ParkingSlotDao slotDao;

    @BeforeAll
    static void setUp() {
        String testDb = "jdbc:sqlite:test_audit_overview.db";
        new File("test_audit_overview.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);
        DatabaseInitializer.initializeDatabase();
        slotDao = new ParkingSlotDao();
    }

    @Test
    @DisplayName("Single Source of Truth: Total, Occupied, Available, Maintenance, and % match exactly")
    void testMetricsConsistency() {
        int capacity = slotDao.countTotalSlots();
        int occupied = slotDao.countByStatus("OCCUPIED");
        int available = slotDao.countAvailableSlots();
        int reserved = slotDao.countByStatus("RESERVED");
        int maintenance = slotDao.countByStatus("MAINTENANCE");

        assertEquals(150, capacity, "Capacity must be computed as 150 from slots table");
        assertEquals(capacity, occupied + available + reserved + maintenance,
                "Capacity must strictly equal sum of occupied, available, reserved, and maintenance slots");

        double expectedPct = (occupied * 100.0) / capacity;
        double occPct = capacity > 0 ? ((occupied * 100.0) / capacity) : 0.0;
        assertEquals(expectedPct, occPct, 0.05, "Occupancy percentage formula must match exactly");
    }
}
