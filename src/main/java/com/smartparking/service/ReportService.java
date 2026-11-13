package com.smartparking.service;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.PaymentDao;
import com.smartparking.repository.ReservationDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.util.DateFormat;
import com.smartparking.util.MoneyFormat;
import com.smartparking.util.PlateUtil;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Service generating administrative analytics, occupancy statistics, and financial audit reports.
 * Sourced directly from SQLite slots, tickets, reservations, and payments tables (Single Source of Truth).
 */
public class ReportService {
    private final ParkingLot parkingLot;
    private final TicketDao ticketDao;
    private final PaymentDao paymentDao;
    private final ParkingSlotDao slotDao;
    private final ReservationDao reservationDao;
    private final TariffService tariffService;

    public ReportService(ParkingLot parkingLot, TicketDao ticketDao, PaymentDao paymentDao,
                         ParkingSlotDao slotDao, ReservationDao reservationDao, TariffService tariffService) {
        this.parkingLot = parkingLot;
        this.ticketDao = ticketDao;
        this.paymentDao = paymentDao;
        this.slotDao = slotDao;
        this.reservationDao = reservationDao;
        this.tariffService = tariffService;
    }

    public ReportService(ParkingLot parkingLot) {
        this(parkingLot, new TicketDao(), new PaymentDao(), new ParkingSlotDao(), new ReservationDao(), new TariffService());
    }

    /**
     * Generates a comprehensive summary report string for Admin display or CLI.
     */
    public String generateFullReport() {
        int totalCapacity = slotDao.countTotalSlots();
        int availableSlots = slotDao.countByStatus("AVAILABLE");
        int occupiedSlots = slotDao.countByStatus("OCCUPIED");
        int reservedSlots = slotDao.countByStatus("RESERVED");
        int maintenanceSlots = slotDao.countByStatus("MAINTENANCE");
        double occupancyRate = totalCapacity > 0 ? ((double) occupiedSlots / totalCapacity) * 100.0 : 0.0;
        String cur = tariffService.getCurrencySymbol();

        StringBuilder sb = new StringBuilder();
        sb.append("====================================================\n");
        sb.append("      SMART PARKING FACILITY MANAGEMENT REPORT      \n");
        sb.append("====================================================\n");
        sb.append(String.format("Facility Name: %s (%s)\n", parkingLot.getName(), parkingLot.getLotId()));
        sb.append(String.format("Total Floors: %d\n", parkingLot.getFloors().size()));
        sb.append(String.format("Total Capacity: %d slots\n", totalCapacity));
        sb.append(String.format("Occupied Slots: %d slots\n", occupiedSlots));
        sb.append(String.format("Available Slots: %d slots\n", availableSlots));
        sb.append(String.format("Reserved Slots: %d slots (Shown Separately)\n", reservedSlots));
        sb.append(String.format("Maintenance Slots: %d slots\n", maintenanceSlots));
        sb.append(String.format("Current Occupancy Rate: %.2f%%\n\n", occupancyRate));

        sb.append("--- Floor Utilization Breakdown ---\n");
        for (ParkingFloor floor : parkingLot.getFloors()) {
            List<ParkingSlot> floorSlots = slotDao.findByFloor(floor.getFloorNumber());
            int fTotal = floorSlots.size();
            int fOcc = (int) floorSlots.stream().filter(s -> "OCCUPIED".equalsIgnoreCase(s.getStatus())).count();
            int fAvail = (int) floorSlots.stream().filter(ParkingSlot::isAvailable).count();
            int fRes = (int) floorSlots.stream().filter(s -> "RESERVED".equalsIgnoreCase(s.getStatus())).count();
            int fMaint = (int) floorSlots.stream().filter(s -> "MAINTENANCE".equalsIgnoreCase(s.getStatus()) || "OUT_OF_SERVICE".equalsIgnoreCase(s.getStatus())).count();
            double fPct = fTotal > 0 ? ((double) fOcc / fTotal) * 100.0 : 0.0;

            sb.append(String.format("  * Floor %d ('%s'): %d/%d occupied (%.1f%%) | Avail: %d, Res: %d, Maint: %d\n",
                    floor.getFloorNumber(), floor.getFloorName(), fOcc, fTotal, fPct, fAvail, fRes, fMaint));
        }

        sb.append("\n--- Financial & Revenue Audit ---\n");
        double totalRev = paymentDao.getTotalRevenue();
        double todayRev = paymentDao.getTodayRevenue();
        sb.append(String.format("Total Completed Revenue: %s\n", MoneyFormat.format(totalRev)));
        sb.append(String.format("Today's Revenue (IST): %s\n", MoneyFormat.format(todayRev)));

        Map<PaymentMethodType, Double> breakdown = getRevenueByPaymentMethod();
        for (Map.Entry<PaymentMethodType, Double> entry : breakdown.entrySet()) {
            sb.append(String.format("  * %s: %s\n", entry.getKey().getDescription(), MoneyFormat.format(entry.getValue())));
        }

        List<ParkingTicket> activeTickets = ticketDao.findAllActive();
        sb.append(String.format("\nCurrently Active Vehicles Parked: %d (Matches Occupied Slots: %d)\n", activeTickets.size(), occupiedSlots));
        for (ParkingTicket t : activeTickets) {
            sb.append(String.format("  - Ticket %s | Plate: %s [%s] | Slot: %s (Floor %d) | In: %s\n",
                    t.getTicketNumber(), PlateUtil.format(t.getLicensePlate()), PlateUtil.district(t.getLicensePlate()), t.getSlotId(), t.getFloorNumber(), DateFormat.display(t.getEntryTime())));
        }

        List<Reservation> activeReservations = reservationDao.findAll().stream()
                .filter(r -> r.getStatus() == com.smartparking.model.enums.ReservationStatus.CONFIRMED)
                .toList();
        sb.append(String.format("\nActive Confirmed Advance Reservations: %d\n", activeReservations.size()));
        for (Reservation r : activeReservations) {
            sb.append(String.format("  - Res #%s | Plate: %s [%s] | Slot: %s | Start: %s | End: %s\n",
                    r.getReservationCode(), PlateUtil.format(r.getLicensePlate()), PlateUtil.district(r.getLicensePlate()), r.getSlotId(), DateFormat.display(r.getStartTime()), DateFormat.display(r.getEndTime())));
        }

        sb.append("====================================================\n");
        return sb.toString();
    }

    public Map<PaymentMethodType, Double> getRevenueByPaymentMethod() {
        Map<PaymentMethodType, Double> map = new EnumMap<>(PaymentMethodType.class);
        for (PaymentMethodType method : PaymentMethodType.values()) {
            map.put(method, 0.0);
        }

        List<PaymentTransaction> transactions = paymentDao.findAll();
        for (PaymentTransaction txn : transactions) {
            if (txn.getStatus() == com.smartparking.model.enums.PaymentStatus.COMPLETED) {
                map.put(txn.getPaymentMethod(), map.get(txn.getPaymentMethod()) + txn.getAmount());
            }
        }
        return map;
    }
}
