package com.smartparking.service;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.ev.ChargingSession;
import com.smartparking.model.ev.EvCharger;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.user.User;
import com.smartparking.repository.*;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Service managing Electric Vehicle (EV) Charging Sessions, Energy Calculations,
 * Overstay Idle Fees, and Gate Checkout validations.
 *
 * NOTE: Energy metering (kWh), connector status transitions, and power flow are
 * [Simulated] for presentation & demonstration in academic viva.
 * In a production deployment, this service integrates with OCPP 1.6J/2.0.1 or Open Charge Point Protocol gateways.
 *
 * Tax Note: EV Charging energy service GST rate is configurable via 'ev_charging_gst_rate_percent'
 * (Default: 18.0%). Please verify applicable GST council notification for energy vs parking composite supplies.
 */
public class EvChargingService {

    public static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");

    private final EvChargerDao chargerDao;
    private final ChargingSessionDao sessionDao;
    private final TicketDao ticketDao;
    private final ParkingSlotDao slotDao;
    private final SystemSettingsDao settingsDao;
    private final AuditLogService auditLogService;

    public EvChargingService(EvChargerDao chargerDao, ChargingSessionDao sessionDao,
                             TicketDao ticketDao, ParkingSlotDao slotDao,
                             SystemSettingsDao settingsDao, AuditLogService auditLogService) {
        this.chargerDao = chargerDao;
        this.sessionDao = sessionDao;
        this.ticketDao = ticketDao;
        this.slotDao = slotDao;
        this.settingsDao = settingsDao;
        this.auditLogService = auditLogService;
    }

    public EvChargingService() {
        this(new EvChargerDao(), new ChargingSessionDao(), new TicketDao(),
                new ParkingSlotDao(), new SystemSettingsDao(), AuditLogService.getInstance());
    }

    /**
     * Starts an EV charging session for an active parking ticket.
     * Blocks if:
     * - Vehicle is not an EV class (EV_CAR, EV_TWO_WHEELER)
     * - Slot is not an EV charging bay (ELECTRIC)
     * - Charger is in FAULT, OFFLINE, or CHARGING status
     * - Ticket already has an active charging session
     */
    public synchronized ChargingSession startCharging(int ticketId, int chargerId, User actor) {
        // 1. RBAC check
        if (actor != null && UserSession.getInstance().isAuthenticated()) {
            // Admin, Attendant, or Customer initiating charging
            boolean isAllowed = AccessControl.hasPermission(actor, Permission.PARK_VEHICLE_ENTRY)
                    || AccessControl.hasPermission(actor, Permission.CUSTOMER_BOOK_RESERVATION);
            if (!isAllowed) {
                throw new SecurityException("User " + actor.getUsername() + " is not authorized to start EV charging.");
            }
        }

        // 2. Fetch ticket
        ParkingTicket ticket = ticketDao.findById(ticketId).orElse(null);
        if (ticket == null || ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot start EV charging: Ticket #" + ticketId + " is not active.");
        }

        // 3. Validate vehicle type is EV
        VehicleType vType = ticket.getVehicleType();
        if (!isEvVehicle(vType)) {
            throw new IllegalArgumentException("Vehicle " + ticket.getLicensePlate() + " of type " + vType +
                    " is not an Electric Vehicle (EV). Charging is restricted to EV vehicles only.");
        }

        // 4. Validate slot is EV bay
        ParkingSlot slot = slotDao.findById(ticket.getSlotId()).orElse(null);
        if (slot == null || slot.getSlotType() != SlotType.ELECTRIC) {
            throw new IllegalArgumentException("Slot " + ticket.getSlotId() + " is not an EV charging bay.");
        }

        // 5. Fetch charger
        EvCharger charger = chargerDao.findById(chargerId)
                .orElseThrow(() -> new IllegalArgumentException("EV Charger #" + chargerId + " not found."));

        if (!charger.getSlotId().equalsIgnoreCase(slot.getSlotId())) {
            throw new IllegalArgumentException("Charger #" + chargerId + " belongs to slot " + charger.getSlotId() +
                    ", but vehicle is parked in " + slot.getSlotId());
        }

        if (charger.isFault()) {
            throw new IllegalStateException("EV Charger #" + chargerId + " is currently in FAULT state and cannot be used.");
        }
        if (charger.isOffline()) {
            throw new IllegalStateException("EV Charger #" + chargerId + " is currently OFFLINE.");
        }
        if (charger.isCharging()) {
            throw new IllegalStateException("EV Charger #" + chargerId + " is already active with another vehicle.");
        }

        // 6. Block double starting on same ticket
        Optional<ChargingSession> existingActive = sessionDao.findActiveByTicketId(ticketId);
        if (existingActive.isPresent()) {
            throw new IllegalStateException("Ticket #" + ticketId + " already has an active EV charging session (#" +
                    existingActive.get().getId() + ").");
        }

        // 7. Read per-kWh tariff
        double unitPrice = settingsDao.getDouble("ev_charging_unit_price_per_kwh", 15.0);
        LocalDateTime now = LocalDateTime.now(ZONE_IST);

        ChargingSession session = sessionDao.createSession(ticketId, chargerId, now, unitPrice);
        chargerDao.updateStatus(chargerId, "CHARGING");

        String username = actor != null ? actor.getUsername() : "SYSTEM";
        auditLogService.log("EV_CHARGING_STARTED", "CHARGING_SESSION", String.valueOf(session.getId()),
                "AVAILABLE", "CHARGING",
                "Simulated EV charging started for ticket #" + ticketId + " on charger #" + chargerId + " (" + charger.getConnectorType() + ")",
                "SUCCESS");

        return session;
    }

    /**
     * Stops an active EV charging session, simulates accumulated kWh energy,
     * computes energy charges, updates ticket fee, and frees the charger.
     */
    public synchronized ChargingSession stopCharging(int sessionId, User actor) {
        ChargingSession session = sessionDao.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Charging session #" + sessionId + " not found."));

        if (!session.isActive()) {
            throw new IllegalStateException("Charging session #" + sessionId + " is already completed or inactive.");
        }

        EvCharger charger = chargerDao.findById(session.getChargerId())
                .orElseThrow(() -> new IllegalArgumentException("EV Charger #" + session.getChargerId() + " not found."));

        LocalDateTime endTime = LocalDateTime.now(ZONE_IST);

        // Simulated energy calculation: power_kw * elapsed_hours * 0.90 efficiency
        double elapsedHours = Math.max(1.0 / 60.0, Duration.between(session.getStartTime(), endTime).toMillis() / 3600000.0);
        double simulatedKwh = Math.round(charger.getPowerKw() * elapsedHours * 0.90 * 100.0) / 100.0;
        if (simulatedKwh <= 0.0) simulatedKwh = 0.50; // minimum simulated bump for fast demo tests

        double energyCharge = Math.round(simulatedKwh * session.getUnitPrice() * 100.0) / 100.0;

        sessionDao.completeSession(sessionId, endTime, simulatedKwh, energyCharge, 0.0);
        chargerDao.updateStatus(charger.getId(), "AVAILABLE");

        // Update ticket with EV fee
        ParkingTicket ticket = ticketDao.findById(session.getTicketId()).orElse(null);
        if (ticket != null) {
            ticket.setEvChargingFee(energyCharge);
            ticketDao.update(ticket);
        }

        String username = actor != null ? actor.getUsername() : "SYSTEM";
        auditLogService.log("EV_CHARGING_STOPPED", "CHARGING_SESSION", String.valueOf(sessionId),
                "ACTIVE", "COMPLETED",
                "Simulated EV charging stopped: " + simulatedKwh + " kWh, ₹" + energyCharge,
                "SUCCESS");

        session.setEndTime(endTime);
        session.setKwh(simulatedKwh);
        session.setEnergyCharge(energyCharge);
        session.setStatus("COMPLETED");
        return session;
    }

    /**
     * Calculates idle overstay fee if the vehicle remains plugged into the EV bay
     * beyond the configured grace minutes after charging is complete.
     */
    public double calculateIdleFee(ChargingSession session, LocalDateTime checkoutTime) {
        if (session == null || !session.isCompleted() || session.getEndTime() == null) {
            return 0.0;
        }

        if (checkoutTime == null || checkoutTime.isBefore(session.getEndTime())) {
            return 0.0;
        }

        int graceMinutes = settingsDao.getInt("ev_charging_grace_minutes", 15);
        double idleFeePerMin = settingsDao.getDouble("ev_charging_idle_fee_per_min", 2.0);

        long pluggedMinutesAfterCompletion = Duration.between(session.getEndTime(), checkoutTime).toMinutes();
        if (pluggedMinutesAfterCompletion <= graceMinutes) {
            return 0.0;
        }

        long excessMinutes = pluggedMinutesAfterCompletion - graceMinutes;
        double idleFee = Math.round(excessMinutes * idleFeePerMin * 100.0) / 100.0;

        sessionDao.updateIdleFee(session.getId(), idleFee);
        session.setIdleFee(idleFee);

        // Update ticket fee total
        ParkingTicket ticket = ticketDao.findById(session.getTicketId()).orElse(null);
        if (ticket != null) {
            double totalEvCharge = session.getEnergyCharge() + idleFee;
            ticket.setEvChargingFee(totalEvCharge);
            ticketDao.update(ticket);
        }

        return idleFee;
    }

    /**
     * Validates whether checkout is permitted.
     * Checkout is blocked if the vehicle is currently charging.
     */
    public boolean isChargingActive(int ticketId) {
        return sessionDao.findActiveByTicketId(ticketId).isPresent();
    }

    public Optional<ChargingSession> getActiveSession(int ticketId) {
        return sessionDao.findActiveByTicketId(ticketId);
    }

    public Optional<ChargingSession> getLatestSession(int ticketId) {
        return sessionDao.findLatestByTicketId(ticketId);
    }

    public Optional<EvCharger> getChargerForSlot(String slotId) {
        return chargerDao.findBySlotId(slotId);
    }

    public List<EvCharger> getAllChargers() {
        return chargerDao.findAll();
    }

    public void updateChargerStatus(int chargerId, String status) {
        chargerDao.updateStatus(chargerId, status);
        auditLogService.log("CHARGER_STATUS_UPDATED", "EV_CHARGER", String.valueOf(chargerId),
                "", status, "Charger status changed to " + status, "SUCCESS");
    }

    /**
     * Returns true if the vehicle class is electric.
     */
    public boolean isEvVehicle(VehicleType vType) {
        return vType == VehicleType.EV_CAR || vType == VehicleType.EV_TWO_WHEELER
                || vType == VehicleType.ELECTRIC_CAR;
    }

    /**
     * Backward-compatible legacy fee calculator helper.
     */
    public double calculateChargingFee(ParkingTicket ticket, ParkingSlot slot, com.smartparking.model.vehicle.Vehicle vehicle) {
        if (ticket == null || slot == null || vehicle == null) return 0.0;
        if (!isEvVehicle(vehicle.getType()) || slot.getSlotType() != SlotType.ELECTRIC) return 0.0;
        return ticket.getEvChargingFee();
    }

    /**
     * Live Simulated Meter Progress (for UI Stall Inspector).
     */
    public record SimulatedMeterProgress(
            double currentKwh,
            double currentCost,
            long elapsedSeconds,
            double powerKw,
            String connectorType
    ) {}

    public SimulatedMeterProgress getSimulatedProgress(ChargingSession session, EvCharger charger) {
        if (session == null || charger == null) {
            return new SimulatedMeterProgress(0, 0, 0, 0, "NONE");
        }
        LocalDateTime now = LocalDateTime.now(ZONE_IST);
        LocalDateTime end = session.isActive() ? now : (session.getEndTime() != null ? session.getEndTime() : now);
        long elapsedSec = Math.max(0, Duration.between(session.getStartTime(), end).toSeconds());
        double elapsedHours = elapsedSec / 3600.0;
        double kwh = Math.round(charger.getPowerKw() * elapsedHours * 0.90 * 100.0) / 100.0;
        double cost = Math.round(kwh * session.getUnitPrice() * 100.0) / 100.0;
        return new SimulatedMeterProgress(kwh, cost, elapsedSec, charger.getPowerKw(), charger.getConnectorType());
    }
}
