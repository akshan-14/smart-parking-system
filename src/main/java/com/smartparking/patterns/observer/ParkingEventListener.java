package com.smartparking.patterns.observer;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;

/**
 * Observer Pattern interface for receiving system parking lifecycle events.
 */
public interface ParkingEventListener {

    /**
     * Triggered when a vehicle successfully checks in and a ticket is issued.
     */
    void onVehicleEntry(ParkingTicket ticket, ParkingSlot slot);

    /**
     * Triggered when a vehicle pays and checks out of the facility.
     */
    void onVehicleExit(ParkingTicket ticket, ParkingSlot slot);

    /**
     * Triggered when a slot changes state (Available, Occupied, Reserved, Maintenance).
     */
    void onSlotStateChanged(ParkingSlot slot);

    /**
     * Triggered during facility-wide announcements or alerts.
     */
    void onAlert(String alertMessage);
}
