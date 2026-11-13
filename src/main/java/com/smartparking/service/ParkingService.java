package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.invoice.Invoice;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ElectricSlot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.vehicle.ElectricCar;
import com.smartparking.model.vehicle.Vehicle;
import com.smartparking.patterns.builder.ReceiptBuilder;
import com.smartparking.patterns.builder.TicketBuilder;
import com.smartparking.patterns.observer.DisplayBoardListener;
import com.smartparking.patterns.observer.EmailNotificationListener;
import com.smartparking.patterns.observer.ParkingEventManager;
import com.smartparking.patterns.observer.SmsNotificationListener;
import com.smartparking.patterns.strategy.allocation.NearestFirstAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.SlotAllocationStrategy;
import com.smartparking.repository.PaymentDao;
import com.smartparking.repository.ParkingSlotDao;
import com.smartparking.repository.ReservationDao;
import com.smartparking.repository.TicketDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;
import com.smartparking.util.PlateUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Core Facade and Orchestrator Service for the Smart Parking Management System.
 * Coordinates all entry/exit workflows, strategy allocations, state transitions,
 * observer notifications, and database persistence.
 *
 * Thread-safe: Methods that alter lot/slot state are synchronized to eliminate race conditions.
 */
public class ParkingService {
    public record CheckoutResult(ParkingTicket ticket, PaymentTransaction payment, Invoice invoice) {}

    private final ParkingLot parkingLot;
    private final ParkingSlotDao slotDao;
    private final TicketDao ticketDao;
    private final ReservationDao reservationDao;
    private final PaymentDao paymentDao;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final EvChargingService evChargingService;
    private final PenaltyService penaltyService;
    private final ParkingEventManager eventManager;
    private final FastagService fastagService;
    private final InvoiceService invoiceService;
    private final GstCalculator gstCalculator;
    private SlotAllocationStrategy allocationStrategy;

    public ParkingService(ParkingLot parkingLot, ParkingSlotDao slotDao, TicketDao ticketDao,
                          ReservationDao reservationDao, PricingService pricingService,
                          PaymentService paymentService, EvChargingService evChargingService,
                          PenaltyService penaltyService, ParkingEventManager eventManager,
                          SlotAllocationStrategy allocationStrategy) {
        this.parkingLot = parkingLot;
        this.slotDao = slotDao;
        this.ticketDao = ticketDao;
        this.reservationDao = reservationDao;
        this.paymentDao = new PaymentDao();
        this.pricingService = pricingService;
        this.paymentService = paymentService;
        this.evChargingService = evChargingService;
        this.penaltyService = penaltyService;
        this.eventManager = eventManager;
        this.fastagService = new FastagService();
        this.invoiceService = new InvoiceService();
        this.gstCalculator = new GstCalculator();
        this.allocationStrategy = allocationStrategy;

        // Auto-initialize standard observers
        DisplayBoardListener displayBoard = new DisplayBoardListener();
        for (ParkingFloor floor : parkingLot.getFloors()) {
            displayBoard.updateFloorAvailability(floor.getFloorNumber(), floor.getAvailableSlotsCount());
        }
        eventManager.subscribe(displayBoard);
        eventManager.subscribe(new EmailNotificationListener());
        eventManager.subscribe(new SmsNotificationListener());
    }

    /**
     * Helper factory constructor creating a production-configured service.
     */
    public static ParkingService createDefault(ParkingLot lot) {
        ParkingSlotDao slotDao = new ParkingSlotDao();
        TicketDao ticketDao = new TicketDao();
        ReservationDao resDao = new ReservationDao();
        PricingService pricingService = new PricingService();
        PaymentService paymentService = new PaymentService();
        EvChargingService evService = new EvChargingService();
        PenaltyService penaltyService = new PenaltyService(ticketDao);
        ParkingEventManager eventManager = new ParkingEventManager();
        SlotAllocationStrategy strategy = new NearestFirstAllocationStrategy();

        return new ParkingService(lot, slotDao, ticketDao, resDao, pricingService,
                paymentService, evService, penaltyService, eventManager, strategy);
    }

    /**
     * Processes vehicle arrival at the parking entry gate.
     * Synchronized to prevent concurrent slot allocation race conditions.
     */
    public synchronized ParkingTicket processVehicleEntry(Vehicle vehicle) {
        return processVehicleEntry(vehicle, LocalDateTime.now());
    }

    /**
     * Overloaded method with explicit entry timestamp for testing simulations.
     */
    public synchronized ParkingTicket processVehicleEntry(Vehicle vehicle, LocalDateTime customEntryTime) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.PARK_VEHICLE_ENTRY);
        }

        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle cannot be null.");
        }

        String rawPlate = vehicle.getLicensePlate();
        String normPlate = PlateUtil.normalize(rawPlate);
        if (!PlateUtil.isValid(rawPlate)) {
            throw new IllegalArgumentException("Enter a valid UP plate, e.g. UP32 AB 1234");
        }

        // 1. Check if vehicle already has an active ticket inside the lot or is currently parked
        Optional<ParkingTicket> existing = ticketDao.findActiveByLicensePlate(normPlate);
        if (existing.isPresent()) {
            throw new IllegalStateException("This vehicle is already parked in the facility.");
        }
        for (ParkingFloor floor : parkingLot.getFloors()) {
            for (ParkingSlot s : floor.getSlots()) {
                if (normPlate.equalsIgnoreCase(PlateUtil.normalize(s.getCurrentVehiclePlate()))) {
                    throw new IllegalStateException("This vehicle is already parked in the facility.");
                }
            }
        }

        // 2. Check if vehicle has an active advance reservation
        Optional<Reservation> resOpt = reservationDao.findActiveByPlate(normPlate);
        ParkingSlot targetSlot = null;

        if (resOpt.isPresent()) {
            Reservation res = resOpt.get();
            targetSlot = findSlotInLot(res.getSlotId()).orElse(null);
            res.checkIn();
            reservationDao.updateStatus(res.getReservationId(), com.smartparking.model.enums.ReservationStatus.CHECKED_IN);
        }

        // 3. If no reservation, allocate automatically using atomic conditional update in a retry loop
        if (targetSlot == null) {
            List<ParkingSlot> candidates = parkingLot.getFloors().stream()
                    .sorted(Comparator.comparingInt(ParkingFloor::getFloorNumber))
                    .flatMap(floor -> floor.getAllSlots().stream())
                    .filter(slot -> slot.canAccommodate(vehicle))
                    .toList();

            for (ParkingSlot candidate : candidates) {
                if (slotDao.tryAllocateSlotAtomic(candidate.getSlotId(), normPlate)) {
                    targetSlot = candidate;
                    break;
                }
            }

            if (targetSlot == null) {
                throw new IllegalStateException("No suitable slot available for this vehicle type");
            }
        } else {
            slotDao.updateStatus(targetSlot.getSlotId(), "OCCUPIED", normPlate);
        }

        // 4. Apply State Pattern: Occupy slot
        targetSlot.occupy(vehicle);

        // 5. Activate EV charger if applicable
        if (targetSlot instanceof ElectricSlot evSlot && vehicle instanceof ElectricCar ev && ev.isChargingRequested()) {
            evSlot.setChargerActive(true);
        }

        // 6. Generate and persist ticket using Builder Pattern
        ParkingTicket ticket = TicketBuilder.newInstance()
                .setLicensePlate(normPlate)
                .setVehicleType(vehicle.getType())
                .setSlotId(targetSlot.getSlotId())
                .setFloorNumber(targetSlot.getFloorNumber())
                .setEntryTime(customEntryTime != null ? customEntryTime : LocalDateTime.now())
                .setStatus(TicketStatus.ACTIVE)
                .build();
        try {
            int ticketId = ticketDao.save(ticket);
            ticket = ticketDao.findByTicketNumber(ticket.getTicketNumber()).orElse(ticket);
        } catch (Exception ex) {
            // Roll back slot occupancy if ticket creation fails (e.g. concurrent duplicate plate)
            slotDao.updateStatus(targetSlot.getSlotId(), "AVAILABLE", null);
            targetSlot.vacate();
            throw new IllegalStateException("This vehicle is already parked in the facility.", ex);
        }

        // 7. Notify Observer Pattern listeners (LED display board, SMS, Email)
        eventManager.notifyVehicleEntry(ticket, targetSlot);

        return ticket;
    }

    /**
     * Processes vehicle departure at the exit gate: computes dynamic fee, applies penalties,
     * charges payment, vacates slot, creates sequential GST Tax Invoice, and notifies observers atomically.
     */
    public synchronized CheckoutResult processVehicleCheckout(String ticketNumber, PaymentMethodType paymentMethod) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.CHECKOUT_VEHICLE);
        }

        if (ticketNumber == null || ticketNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket number cannot be null or empty.");
        }

        ParkingTicket ticket = ticketDao.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketNumber));

        if (ticket.getStatus() == TicketStatus.EXITED || ticket.getStatus() == TicketStatus.PAID) {
            throw new IllegalStateException("Ticket " + ticketNumber + " has already been settled and exited.");
        }

        // Priority 2: Cannot check out while a session is ACTIVE: prompt to stop charging first
        if (evChargingService.isChargingActive(ticket.getTicketId())) {
            throw new IllegalStateException("Vehicle is currently charging (Active EV Session). Please stop the EV charging session before checkout.");
        }

        ticket.setExitTime(LocalDateTime.now());

        // 1. Locate slot
        ParkingSlot slot = findSlotInLot(ticket.getSlotId())
                .orElseThrow(() -> new IllegalStateException("Slot " + ticket.getSlotId() + " not found."));

        // 2. Calculate dynamic pricing base fee
        double occupancy = parkingLot.getOccupancyPercentage();
        double baseFee = pricingService.calculateTotalParkingFee(ticket, slot.getHourlyRate(), occupancy);
        ticket.setBaseFee(baseFee);

        // 3. Calculate EV Charging fee & idle fee if applicable
        var evSessionOpt = evChargingService.getLatestSession(ticket.getTicketId());
        if (evSessionOpt.isPresent()) {
            var evSession = evSessionOpt.get();
            double idleFee = evChargingService.calculateIdleFee(evSession, ticket.getExitTime());
            double totalEvCharge = evSession.getEnergyCharge() + idleFee;
            ticket.setEvChargingFee(totalEvCharge);
        } else if (slot instanceof ElectricSlot && slot.getCurrentVehicle() != null) {
            double evFee = evChargingService.calculateChargingFee(ticket, slot, slot.getCurrentVehicle());
            ticket.setEvChargingFee(evFee);
        }

        // 4. Check and apply overstay penalties (e.g. over 24 hours)
        penaltyService.checkAndApplyOverstay(ticket, 24);

        ticket.recalculateTotal();

        // Server-side GST and round-off calculation
        double taxableValue = ticket.getTotalAmount();
        GstCalculator.GstResult gst = gstCalculator.calculate(taxableValue);
        ticket.setTotalAmount(gst.grandTotal());

        PaymentTransaction paymentTxn;
        Invoice invoice;

        // 5. Execute payment and atomic persistence across tickets, payments, slots, and invoices
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // If paymentMethod is FASTAG, perform automated debit
                if (paymentMethod == PaymentMethodType.FASTAG) {
                    fastagService.debit(conn, ticket.getLicensePlate(), gst.grandTotal());
                }

                String txnRef = "TXN-" + paymentMethod.name() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                invoice = invoiceService.createTicketInvoice(conn, ticket, gst, paymentMethod.name(), txnRef);
                paymentTxn = paymentService.processTicketPaymentWithConnection(conn, ticket.getTicketId(), gst, paymentMethod, invoice.getInvoiceNumber());

                ticket.markPaid();
                ticket.markExited();
                ticketDao.updateWithConnection(conn, ticket);
                slotDao.updateStatusWithConnection(conn, slot.getSlotId(), "AVAILABLE", null);

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw new RuntimeException("Atomic transaction failed during exit: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during checkout transaction: " + e.getMessage(), e);
        }

        // 6. Apply State Pattern: Vacate slot
        slot.vacate();

        // 7. Notify Observer Pattern listeners
        eventManager.notifyVehicleExit(ticket, slot);

        return new CheckoutResult(ticket, paymentTxn, invoice);
    }

    public synchronized ParkingTicket processVehicleExit(String ticketNumber, PaymentMethodType paymentMethod) {
        return processVehicleCheckout(ticketNumber, paymentMethod).ticket();
    }

    /**
     * Reports lost ticket: calculates stay, applies lost ticket penalty, and completes checkout with GST invoice.
     */
    public synchronized CheckoutResult processLostTicketCheckout(String licensePlate, PaymentMethodType paymentMethod) {
        String normPlate = PlateUtil.normalize(licensePlate);
        ParkingTicket ticket = ticketDao.findActiveByLicensePlate(normPlate)
                .orElseThrow(() -> new IllegalArgumentException("No active ticket found for license plate: " + PlateUtil.format(licensePlate)));

        penaltyService.assessLostTicketPenalty(ticket);
        return processVehicleCheckout(ticket.getTicketNumber(), paymentMethod);
    }

    public synchronized ParkingTicket processLostTicketExit(String licensePlate, PaymentMethodType paymentMethod) {
        return processLostTicketCheckout(licensePlate, paymentMethod).ticket();
    }

    /**
     * Generates an itemized customer receipt using ReceiptBuilder.
     */
    public String generateReceipt(ParkingTicket ticket, PaymentTransaction txn) {
        ReceiptBuilder builder = ReceiptBuilder.newInstance()
                .setFacilityName(parkingLot.getName())
                .setTicket(ticket)
                .setPaymentTransaction(txn)
                .setPricingPolicy("Automated Indian Garage Tariff");

        pricingService.getMembershipService().getActiveMembership(ticket.getLicensePlate()).ifPresent(pass -> {
            builder.setMonthlyPass(pass.getExpiryDate());
        });

        return builder.build();
    }

    /**
     * Generates an itemized customer receipt for a ticket by retrieving its latest payment transaction.
     */
    public String generateReceipt(ParkingTicket ticket) {
        PaymentTransaction txn = paymentService.getTicketPayments(ticket.getTicketId()).stream().findFirst().orElse(null);
        return generateReceipt(ticket, txn);
    }

    /**
     * Searches vehicle by registration number across current active slots and historical tickets.
     */
    public Optional<ParkingTicket> searchVehicle(String licensePlate) {
        String normPlate = PlateUtil.normalize(licensePlate);
        return ticketDao.findActiveByLicensePlate(normPlate);
    }

    /**
     * Searches all active vehicles matching partial plate query (e.g. "AB1234", "8192", "UP32").
     */
    public List<ParkingTicket> searchVehicles(String query) {
        return ticketDao.searchActiveByPlate(query);
    }

    public Optional<ParkingSlot> findSlotInLot(String slotId) {
        for (ParkingFloor floor : parkingLot.getFloors()) {
            Optional<ParkingSlot> slot = floor.getSlotById(slotId);
            if (slot.isPresent()) return slot;
        }
        return Optional.empty();
    }

    public ParkingLot getParkingLot() {
        return parkingLot;
    }

    public SlotAllocationStrategy getAllocationStrategy() {
        return allocationStrategy;
    }

    public void setAllocationStrategy(SlotAllocationStrategy allocationStrategy) {
        if (allocationStrategy == null) {
            throw new IllegalArgumentException("Allocation strategy cannot be null.");
        }
        this.allocationStrategy = allocationStrategy;
    }

    public ParkingEventManager getEventManager() {
        return eventManager;
    }

    public PricingService getPricingService() {
        return pricingService;
    }

    public PaymentService getPaymentService() {
        return paymentService;
    }

    public FastagService getFastagService() {
        return fastagService;
    }

    public InvoiceService getInvoiceService() {
        return invoiceService;
    }

    public GstCalculator getGstCalculator() {
        return gstCalculator;
    }

    public EvChargingService getEvChargingService() {
        return evChargingService;
    }

    public PaymentDao getPaymentDao() {
        return paymentDao;
    }

    public boolean refundPayment(int paymentId, String reason, String adminUser) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.REFUND_PAYMENT);
        }
        boolean res = paymentDao.refund(paymentId, reason, adminUser);
        if (res) {
            AuditLogService.getInstance().log("REFUND_PAYMENT", "PAYMENT", String.valueOf(paymentId),
                    "COMPLETED", "REFUNDED", reason, "SUCCESS");
        }
        return res;
    }
}
