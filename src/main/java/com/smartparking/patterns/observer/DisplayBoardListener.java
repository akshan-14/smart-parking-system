package com.smartparking.patterns.observer;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concrete Observer: Electronic Display Board at parking entrance.
 * Dynamically tracks available slots per floor and updates incoming drivers.
 */
public class DisplayBoardListener implements ParkingEventListener {
    private final Map<Integer, Integer> floorAvailableSlots = new ConcurrentHashMap<>();

    public void updateFloorAvailability(int floorNumber, int availableCount) {
        floorAvailableSlots.put(floorNumber, availableCount);
    }

    @Override
    public void onVehicleEntry(ParkingTicket ticket, ParkingSlot slot) {
        if (slot != null) {
            floorAvailableSlots.computeIfPresent(slot.getFloorNumber(), (k, v) -> Math.max(0, v - 1));
            System.out.printf("[LED DISPLAY BOARD] Floor %d: Vehicle %s parked at %s. Updated available: %d%n",
                    slot.getFloorNumber(), ticket.getLicensePlate(), slot.getSlotNumber(), floorAvailableSlots.getOrDefault(slot.getFloorNumber(), 0));
        }
    }

    @Override
    public void onVehicleExit(ParkingTicket ticket, ParkingSlot slot) {
        if (slot != null) {
            floorAvailableSlots.compute(slot.getFloorNumber(), (k, v) -> (v == null ? 1 : v + 1));
            System.out.printf("[LED DISPLAY BOARD] Floor %d: Vehicle %s departed slot %s. Updated available: %d%n",
                    slot.getFloorNumber(), ticket.getLicensePlate(), slot.getSlotNumber(), floorAvailableSlots.get(slot.getFloorNumber()));
        }
    }

    @Override
    public void onSlotStateChanged(ParkingSlot slot) {
        // State update logged
    }

    @Override
    public void onAlert(String alertMessage) {
        System.out.println("[LED DISPLAY BOARD ALERT] >>> " + alertMessage + " <<<");
    }

    public int getAvailableSlotsOnFloor(int floorNumber) {
        return floorAvailableSlots.getOrDefault(floorNumber, 0);
    }
}
