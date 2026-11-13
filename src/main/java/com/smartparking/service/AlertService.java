package com.smartparking.service;

import com.smartparking.model.alert.Alert;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.ev.EvCharger;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.user.User;
import com.smartparking.repository.*;
import com.smartparking.security.UserSession;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service managing System & Operational Alerts, Scheduled Anomaly Detection,
 * De-duplication, Status Transitions, and Role-Based Resolution.
 */
public class AlertService {

    public static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");

    private final AlertDao alertDao;
    private final TicketDao ticketDao;
    private final ParkingSlotDao slotDao;
    private final ReservationDao reservationDao;
    private final MembershipDao membershipDao;
    private final EvChargerDao chargerDao;
    private final SystemSettingsDao settingsDao;
    private final AuditLogService auditLogService;

    public AlertService(AlertDao alertDao, TicketDao ticketDao, ParkingSlotDao slotDao,
                        ReservationDao reservationDao, MembershipDao membershipDao,
                        EvChargerDao chargerDao, SystemSettingsDao settingsDao,
                        AuditLogService auditLogService) {
        this.alertDao = alertDao;
        this.ticketDao = ticketDao;
        this.slotDao = slotDao;
        this.reservationDao = reservationDao;
        this.membershipDao = membershipDao;
        this.chargerDao = chargerDao;
        this.settingsDao = settingsDao;
        this.auditLogService = auditLogService;
    }

    public AlertService() {
        this(new AlertDao(), new TicketDao(), new ParkingSlotDao(), new ReservationDao(),
                new MembershipDao(), new EvChargerDao(), new SystemSettingsDao(), AuditLogService.getInstance());
    }

    /**
     * Scans live database state and generates or auto-resolves alerts.
     * Enforces de-duplication: Never creates duplicate open alerts for the same entity and type.
     */
    public synchronized void runScheduledAnomalyCheck() {
        LocalDateTime now = LocalDateTime.now(ZONE_IST);

        // 1. OVERSTAY: Tickets parked longer than configurable threshold (default: 24 hours)
        int overstayHours = settingsDao.getInt("alert.overstay_hours", 24);
        List<ParkingTicket> overstayTickets = ticketDao.findOverstayTickets(overstayHours);
        for (ParkingTicket t : overstayTickets) {
            String entityId = String.valueOf(t.getTicketId());
            if (alertDao.findOpenByTypeAndEntity("OVERSTAY", entityId).isEmpty()) {
                long durationHrs = Duration.between(t.getEntryTime(), now).toHours();
                String msg = String.format("Vehicle %s in bay %s has exceeded stay limit (%d hrs parked).",
                        t.getLicensePlate(), t.getSlotId(), durationHrs);
                alertDao.createAlert("OVERSTAY", "WARNING", "TICKET", entityId, msg);
            }
        }

        // 2. MAINTENANCE_BAY: Slots under maintenance
        List<ParkingSlot> maintSlots = slotDao.findAllByStatus("MAINTENANCE");
        for (ParkingSlot s : maintSlots) {
            if (alertDao.findOpenByTypeAndEntity("MAINTENANCE_BAY", s.getSlotId()).isEmpty()) {
                String msg = String.format("Parking Bay %s on Level %d is currently offline for maintenance.",
                        s.getSlotId(), s.getFloorNumber());
                alertDao.createAlert("MAINTENANCE_BAY", "WARNING", "SLOT", s.getSlotId(), msg);
            }
        }
        // Auto-resolve maintenance alerts if slot is now active
        List<Alert> openMaintAlerts = alertDao.findFiltered(null, "MAINTENANCE_BAY", "OPEN", 100);
        for (Alert a : openMaintAlerts) {
            Optional<ParkingSlot> sOpt = slotDao.findById(a.getEntityId());
            if (sOpt.isPresent() && !"MAINTENANCE".equalsIgnoreCase(sOpt.get().getStatus())) {
                alertDao.resolveAlert(a.getId(), "SYSTEM", now, "Auto-resolved: Slot " + a.getEntityId() + " returned to operational status.");
            }
        }

        // 3. CHARGER_FAULT: EV Chargers marked as FAULT
        List<EvCharger> chargers = chargerDao.findAll();
        for (EvCharger c : chargers) {
            if (c.isFault()) {
                String entityId = String.valueOf(c.getId());
                if (alertDao.findOpenByTypeAndEntity("CHARGER_FAULT", entityId).isEmpty()) {
                    String msg = String.format("EV Charger #%d on Bay %s reported hardware fault (%s connector).",
                            c.getId(), c.getSlotId(), c.getConnectorType());
                    alertDao.createAlert("CHARGER_FAULT", "CRITICAL", "CHARGER", entityId, msg);
                }
            } else {
                // Auto-resolve if charger is back to available/charging
                alertDao.resolveOpenAlertsForEntity("CHARGER_FAULT", String.valueOf(c.getId()), "SYSTEM",
                        "Auto-resolved: Charger #" + c.getId() + " status is " + c.getStatus());
            }
        }

        // 4. CAPACITY_HIGH & CAPACITY_CRITICAL
        int totalSlots = slotDao.countTotalSlots();
        int occupiedSlots = slotDao.countByStatus("OCCUPIED");
        double occPct = totalSlots > 0 ? ((double) occupiedSlots / totalSlots) * 100.0 : 0.0;
        double alertThreshold = settingsDao.getDouble("alert.occupancy_high_percent", 90.0);

        if (occupiedSlots >= totalSlots && totalSlots > 0) {
            if (alertDao.findOpenByTypeAndEntity("CAPACITY_CRITICAL", "GARAGE").isEmpty()) {
                alertDao.createAlert("CAPACITY_CRITICAL", "CRITICAL", "FACILITY", "GARAGE",
                        String.format("Garage is at 100%% capacity (%d/%d slots occupied). All entry gates restricted.", occupiedSlots, totalSlots));
            }
        } else if (occPct >= alertThreshold) {
            if (alertDao.findOpenByTypeAndEntity("CAPACITY_HIGH", "GARAGE").isEmpty()) {
                alertDao.createAlert("CAPACITY_HIGH", "WARNING", "FACILITY", "GARAGE",
                        String.format("High occupancy load: %.1f%% (%d/%d slots occupied).", occPct, occupiedSlots, totalSlots));
            }
        } else {
            // Auto-resolve capacity alerts when load drops
            alertDao.resolveOpenAlertsForEntity("CAPACITY_HIGH", "GARAGE", "SYSTEM", "Auto-resolved: Load dropped to " + String.format("%.1f%%", occPct));
            alertDao.resolveOpenAlertsForEntity("CAPACITY_CRITICAL", "GARAGE", "SYSTEM", "Auto-resolved: Available slots exist.");
        }

        // 5. PASS_EXPIRING: Passes expiring within 7 days
        LocalDate today = LocalDate.now(ZONE_IST);
        List<MembershipPass> activePasses = membershipDao.findAll().stream().filter(MembershipPass::isActive).toList();
        for (MembershipPass p : activePasses) {
            if (p.getExpiryDate() != null) {
                long daysLeft = ChronoUnit.DAYS.between(today, p.getExpiryDate());
                if (daysLeft >= 0 && daysLeft <= 7) {
                    String entityId = String.valueOf(p.getMembershipId());
                    if (alertDao.findOpenByTypeAndEntity("PASS_EXPIRING", entityId).isEmpty()) {
                        String msg = String.format("Monthly Pass #%d (%s) expires in %d days (%s).",
                                p.getMembershipId(), p.getLicensePlate(), daysLeft, p.getExpiryDate());
                        alertDao.createAlert("PASS_EXPIRING", "INFO", "PASS", entityId, msg);
                    }
                }
            }
        }
    }

    /**
     * Acknowledges an alert by an authorized user (ADMIN or ATTENDANT).
     */
    public synchronized void acknowledgeAlert(int alertId, User actor) {
        enforceOperationalPermission(actor);
        Alert alert = alertDao.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert #" + alertId + " not found."));

        if (!alert.isOpen()) {
            throw new IllegalStateException("Alert #" + alertId + " is already " + alert.getStatus());
        }

        String username = actor != null ? actor.getUsername() : "SYSTEM";
        LocalDateTime now = LocalDateTime.now(ZONE_IST);
        alertDao.acknowledgeAlert(alertId, username, now);

        auditLogService.log("ALERT_ACKNOWLEDGED", "ALERT", String.valueOf(alertId),
                "OPEN", "ACKNOWLEDGED", "User " + username + " acknowledged alert: " + alert.getMessage(), "SUCCESS");
    }

    /**
     * Resolves an alert with an explanation note.
     */
    public synchronized void resolveAlert(int alertId, User actor, String resolutionNote) {
        enforceOperationalPermission(actor);
        Alert alert = alertDao.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert #" + alertId + " not found."));

        if (alert.isResolved()) {
            throw new IllegalStateException("Alert #" + alertId + " is already resolved.");
        }

        String username = actor != null ? actor.getUsername() : "SYSTEM";
        LocalDateTime now = LocalDateTime.now(ZONE_IST);
        alertDao.resolveAlert(alertId, username, now, resolutionNote);

        auditLogService.log("ALERT_RESOLVED", "ALERT", String.valueOf(alertId),
                alert.getStatus(), "RESOLVED", "User " + username + " resolved alert: " + resolutionNote, "SUCCESS");
    }

    public List<Alert> getFilteredAlerts(String severity, String type, String status, int limit) {
        return alertDao.findFiltered(severity, type, status, limit);
    }

    public int getOpenAlertCount() {
        return alertDao.countOpenAlerts();
    }

    private void enforceOperationalPermission(User actor) {
        if (actor == null) return;
        if (actor.getRole() == UserRole.CUSTOMER) {
            throw new SecurityException("Customers are not permitted to manage operational facility alerts.");
        }
    }
}
