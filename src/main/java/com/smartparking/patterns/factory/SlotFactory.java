package com.smartparking.patterns.factory;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.slot.*;

/**
 * Factory Pattern implementation for creating concrete ParkingSlot instances.
 * Encapsulates slot creation logic and decouples clients from concrete slot subclasses.
 */
public class SlotFactory {

    public static ParkingSlot createSlot(String slotId, int floorNumber, String slotNumber, SlotType type, double hourlyRate) {
        if (type == null) {
            throw new IllegalArgumentException("SlotType cannot be null.");
        }
        return switch (type) {
            case COMPACT -> new CompactSlot(slotId, floorNumber, slotNumber, hourlyRate);
            case REGULAR -> new RegularSlot(slotId, floorNumber, slotNumber, hourlyRate);
            case LARGE -> new LargeSlot(slotId, floorNumber, slotNumber, hourlyRate);
            case ELECTRIC -> new ElectricSlot(slotId, floorNumber, slotNumber, hourlyRate, 15.0);
            case ACCESSIBLE, HANDICAPPED -> new AccessibleSlot(slotId, floorNumber, slotNumber, hourlyRate);
        };
    }

    public static ParkingSlot createSlot(String slotId, int floorNumber, String slotNumber, SlotType type) {
        return createSlot(slotId, floorNumber, slotNumber, type, type.getDefaultHourlyRate());
    }
}
