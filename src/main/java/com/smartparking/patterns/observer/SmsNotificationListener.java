package com.smartparking.patterns.observer;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;

/**
 * Concrete Observer: Dispatches simulated SMS alerts for immediate mobile updates.
 */
public class SmsNotificationListener implements ParkingEventListener {

    @Override
    public void onVehicleEntry(ParkingTicket ticket, ParkingSlot slot) {
        System.out.printf("[SMS GATEWAY] Ticket %s generated for %s at Slot %s. Welcome!%n",
                ticket.getTicketNumber(), ticket.getLicensePlate(), slot.getSlotId());
    }

    @Override
    public void onVehicleExit(ParkingTicket ticket, ParkingSlot slot) {
        System.out.printf("[SMS GATEWAY] Vehicle %s checked out. Total paid: %s. Safe travels!%n",
                ticket.getLicensePlate(), com.smartparking.util.MoneyFormat.format(ticket.getTotalAmount()));
    }

    @Override
    public void onSlotStateChanged(ParkingSlot slot) {
        // Ignored for SMS
    }

    @Override
    public void onAlert(String alertMessage) {
        System.out.printf("[SMS BROADCAST] ALERT: %s%n", alertMessage);
    }
}
