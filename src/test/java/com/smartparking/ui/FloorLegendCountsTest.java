package com.smartparking.ui;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.SlotType;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Floor Legend & Filter Counts Validation")
class FloorLegendCountsTest {

    private static ParkingSlotDao slotDao;

    @BeforeAll
    static void setUp() {
        String testDb = "jdbc:sqlite:test_floor_counts.db";
        new File("test_floor_counts.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);
        DatabaseInitializer.initializeDatabase();
        slotDao = new ParkingSlotDao();
    }

    @Test
    @DisplayName("Floor tab slot counts match 50 per floor from DB")
    void testFloorTabCounts() {
        List<ParkingSlot> f1 = slotDao.findByFloor(1);
        List<ParkingSlot> f2 = slotDao.findByFloor(2);
        List<ParkingSlot> f3 = slotDao.findByFloor(3);

        assertEquals(50, f1.size(), "Floor 1 should have exactly 50 slots");
        assertEquals(50, f2.size(), "Floor 2 should have exactly 50 slots");
        assertEquals(50, f3.size(), "Floor 3 should have exactly 50 slots");
        assertEquals(150, f1.size() + f2.size() + f3.size(), "Total across all floors must equal 150");
    }

    @Test
    @DisplayName("Floor 1 bay category filter counts partition all slots cleanly")
    void testFloor1BayFilterCounts() {
        List<ParkingSlot> f1 = slotDao.findByFloor(1);

        long std = f1.stream().filter(s -> s.getSlotType() == SlotType.REGULAR || s.getSlotType() == SlotType.COMPACT || s.getSlotType() == SlotType.LARGE).count();
        long ev = f1.stream().filter(s -> s.getSlotType() == SlotType.ELECTRIC).count();
        long acc = f1.stream().filter(s -> s.getSlotType() == SlotType.ACCESSIBLE || s.getSlotType() == SlotType.HANDICAPPED).count();

        assertEquals(f1.size(), std + ev + acc, "Categories must sum to total floor count");
        assertTrue(std > 0, "Standard bays must exist");
        assertTrue(ev > 0, "EV bays must exist");
        assertTrue(acc > 0, "Accessible bays must exist");
    }
}
