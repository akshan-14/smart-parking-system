package com.smartparking.patterns.observer;

import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;

/**
 * Concrete Observer: Dispatches simulated Email notifications to customers and administrators.
 */
public class EmailNotificationListener implements ParkingEventListener {

    @Override
    public void onVehicleEntry(ParkingTicket ticket, ParkingSlot slot) {
        System.out.printf("[EMAIL DISPATCH] To: Driver of %s | Subject: Parking Ticket Issued [%s] | Slot: %s (Floor %d) | Entry: %s%n",
                ticket.getLicensePlate(), ticket.getTicketNumber(), slot.getSlotNumber(), slot.getFloorNumber(), ticket.getEntryTime());
    }

    @Override
    public void onVehicleExit(ParkingTicket ticket, ParkingSlot slot) {
        System.out.printf("[EMAIL DISPATCH] To: Driver of %s | Subject: Parking Receipt [%s] | Total Paid: %s | Exit: %s. Thank you!%n",
                ticket.getLicensePlate(), ticket.getTicketNumber(),
                com.smartparking.util.MoneyFormat.format(ticket.getTotalAmount()),
                com.smartparking.util.DateFormat.display(ticket.getExitTime()));
    }

    @Override
    public void onSlotStateChanged(ParkingSlot slot) {
        // Can be used for maintenance alerts
    }

    @Override
    public void onAlert(String alertMessage) {
        System.out.printf("[EMAIL BROADCAST] Urgent Facility Notice: %s%n", alertMessage);
    }
}
