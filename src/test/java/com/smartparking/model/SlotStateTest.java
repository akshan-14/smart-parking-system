package com.smartparking.model;

import com.smartparking.model.slot.CompactSlot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.vehicle.Bike;
import com.smartparking.model.vehicle.Truck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Slot State Pattern & Lifecycle Tests")
class SlotStateTest {

    @Test
    @DisplayName("Should successfully transition from Available to Occupied and Vacate")
    void testStateTransitions() {
        ParkingSlot slot = new CompactSlot("S-101", 1, "01", 20.0);
        assertTrue(slot.isAvailable());
        assertEquals("AVAILABLE", slot.getStatus());

        Bike bike = new Bike("B-100");
        slot.occupy(bike);

        assertFalse(slot.isAvailable());
        assertEquals("OCCUPIED", slot.getStatus());
        assertEquals(bike, slot.getCurrentVehicle());

        // Attempting to occupy an already occupied slot must fail
        assertThrows(IllegalStateException.class, () -> slot.occupy(new Bike("B-200")));

        // Vacating returns slot to available
        slot.vacate();
        assertTrue(slot.isAvailable());
        assertEquals("AVAILABLE", slot.getStatus());
        assertNull(slot.getCurrentVehicle());
    }

    @Test
    @DisplayName("Should reject incompatible vehicle parking attempt")
    void testIncompatibleVehicleRejection() {
        ParkingSlot compactSlot = new CompactSlot("S-102", 1, "02", 20.0);
        Truck truck = new Truck("TRUCK-1");

        assertThrows(IllegalArgumentException.class, () -> compactSlot.occupy(truck));
        assertTrue(compactSlot.isAvailable());
    }

    @Test
    @DisplayName("Should handle reservation and out-of-service states")
    void testReservationAndMaintenanceStates() {
        ParkingSlot slot = new CompactSlot("S-103", 1, "03", 20.0);

        // Reserve
        slot.reserve();
        assertFalse(slot.isAvailable());
        assertEquals("RESERVED", slot.getStatus());

        // Cannot reserve twice
        assertThrows(IllegalStateException.class, slot::reserve);

        // Mark out of service
        slot.markOutOfService();
        assertEquals("OUT_OF_SERVICE", slot.getStatus());
        assertThrows(IllegalStateException.class, () -> slot.occupy(new Bike("B-300")));

        // Restore to service via vacate
        slot.vacate();
        assertTrue(slot.isAvailable());
        assertEquals("AVAILABLE", slot.getStatus());
    }
}
