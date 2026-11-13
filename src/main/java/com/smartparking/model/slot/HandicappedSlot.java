package com.smartparking.model.slot;

import com.smartparking.model.enums.SlotType;

/**
 * Concrete ParkingSlot: Persons with Disabilities (Divyang) Accessible Slot.
 * Retained for backwards compatibility with legacy tests.
 */
public class HandicappedSlot extends AccessibleSlot {

    public HandicappedSlot(String slotId, int floorNumber, String slotNumber, double hourlyRate) {
        super(slotId, floorNumber, slotNumber, hourlyRate);
    }

    public HandicappedSlot(String slotId, int floorNumber, String slotNumber) {
        super(slotId, floorNumber, slotNumber);
    }
}
