package com.smartparking.patterns.observer;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;

import java.util.ArrayList;
import java.util.List;

/**
 * Subject / Publisher in the Observer Pattern.
 * Coordinates broadcasting events to all registered listeners.
 */
public class ParkingEventManager {
    private final List<ParkingEventListener> listeners = new ArrayList<>();

    public void subscribe(ParkingEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(ParkingEventListener listener) {
        listeners.remove(listener);
    }

    public void notifyVehicleEntry(ParkingTicket ticket, ParkingSlot slot) {
        for (ParkingEventListener listener : listeners) {
            try {
                listener.onVehicleEntry(ticket, slot);
            } catch (Exception e) {
                System.err.println("Error notifying listener of vehicle entry: " + e.getMessage());
            }
        }
    }

    public void notifyVehicleExit(ParkingTicket ticket, ParkingSlot slot) {
        for (ParkingEventListener listener : listeners) {
            try {
                listener.onVehicleExit(ticket, slot);
            } catch (Exception e) {
                System.err.println("Error notifying listener of vehicle exit: " + e.getMessage());
            }
        }
    }

    public void notifySlotStateChanged(ParkingSlot slot) {
        for (ParkingEventListener listener : listeners) {
            try {
                listener.onSlotStateChanged(slot);
            } catch (Exception e) {
                System.err.println("Error notifying listener of slot state change: " + e.getMessage());
            }
        }
    }

    public void notifyAlert(String message) {
        for (ParkingEventListener listener : listeners) {
            try {
                listener.onAlert(message);
            } catch (Exception e) {
                System.err.println("Error notifying listener of alert: " + e.getMessage());
            }
        }
    }
}
