package com.smartparking.service;

import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.tariff.FeeBreakdown;
import com.smartparking.model.tariff.TariffRate;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.repository.AuditLogDao;
import com.smartparking.repository.SqliteTransaction;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.repository.TariffDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Service managing parking tariffs, GST taxes, penalties, multipliers, and automatic checkout fee computation.
 * Sourced directly from the SQLite tariffs and settings tables (Single Source of Truth).
 */
public class TariffService {

    private final SystemSettingsDao settingsDao;
    private final TariffDao tariffDao;
    private final AuditLogDao auditLogDao;

    public TariffService(SystemSettingsDao settingsDao, TariffDao tariffDao, AuditLogDao auditLogDao) {
        this.settingsDao = settingsDao;
        this.tariffDao = tariffDao;
        this.auditLogDao = auditLogDao;
    }

    public TariffService(SystemSettingsDao settingsDao) {
        this(settingsDao, new TariffDao(), new AuditLogDao());
    }

    public TariffService() {
        this(new SystemSettingsDao(), new TariffDao(), new AuditLogDao());
    }

    /**
     * Retrieves the tariff rate configuration for a vehicle type from the tariffs table.
     */
    public TariffRate getTariffRate(VehicleType type) {
        final VehicleType finalType = (type != null) ? type : VehicleType.CAR;
        return tariffDao.findByVehicleType(finalType).orElseGet(() ->
                // Default fallback according to Phase 3 specification table
                switch (finalType) {
                    case MOTORCYCLE, EV_TWO_WHEELER, BIKE -> new TariffRate(finalType, 10.0, 5.0, 40.0, 30.0, 300.0);
                    case AUTO_RICKSHAW -> new TariffRate(finalType, 20.0, 10.0, 80.0, 100.0, 600.0);
                    case CAR, EV_CAR, ELECTRIC_CAR -> new TariffRate(finalType, 40.0, 20.0, 200.0, 100.0, 1500.0);
                    case LCV -> new TariffRate(finalType, 80.0, 40.0, 400.0, 200.0, 3000.0);
                    case HCV, TRUCK -> new TariffRate(finalType, 100.0, 50.0, 500.0, 200.0, 4500.0);
                }
        );
    }

    public double getHourlyRate(VehicleType type) {
        TariffRate tr = getTariffRate(type);
        return tr != null ? tr.getFirstHourRate() : 60.0;
    }

    /**
     * Automatically computes comprehensive checkout fee based on vehicle type, stay duration,
     * overnight status, peak multiplier, active membership pass, and lost-ticket penalty.
     */
    public FeeBreakdown calculateFee(String ticketNumber, String licensePlate, VehicleType vehicleType,
                                     LocalDateTime entryTime, LocalDateTime exitTime,
                                     boolean isLostTicket, MembershipPass activePass) {
        if (entryTime == null) entryTime = LocalDateTime.now();
        if (exitTime == null || exitTime.isBefore(entryTime)) exitTime = entryTime;
        if (vehicleType == null) vehicleType = VehicleType.CAR;

        TariffRate tariff = getTariffRate(vehicleType);
        int graceMinutes = getGracePeriodMinutes();
        long durationMinutes = Math.max(0, Duration.between(entryTime, exitTime).toMinutes());

        double baseFee = 0.0;
        long billedHours = 0;

        // Rule 1: First 15 minutes free
        if (durationMinutes > graceMinutes) {
            // Billed per started hour
            billedHours = (durationMinutes + 59) / 60;
            if (billedHours <= 24) {
                double uncapped = tariff.getFirstHourRate() + (billedHours - 1) * tariff.getExtraHourRate();
                baseFee = Math.min(uncapped, tariff.getDailyCap());
            } else {
                long fullDays = billedHours / 24;
                long remHours = billedHours % 24;
                double remFee = 0.0;
                if (remHours > 0) {
                    double uncappedRem = tariff.getFirstHourRate() + (remHours - 1) * tariff.getExtraHourRate();
                    remFee = Math.min(uncappedRem, tariff.getDailyCap());
                }
                baseFee = (fullDays * tariff.getDailyCap()) + remFee;
            }
        }

        // Rule 2: Overnight charge (flat charge if parked past midnight)
        long midnightsCrossed = Math.max(0, ChronoUnit.DAYS.between(entryTime.toLocalDate(), exitTime.toLocalDate()));
        double overnightFee = midnightsCrossed * tariff.getOvernightRate();

        // Rule 3: Peak multiplier
        boolean peakActive = isPeakEnabled();
        double multiplier = getPeakMultiplier();
        if (peakActive && multiplier > 1.0) {
            baseFee = baseFee * multiplier;
        }

        // Rule 4: Monthly Pass deduction
        double passDiscount = 0.0;
        boolean passActive = false;
        LocalDate passExpiry = null;
        if (activePass != null && activePass.isValid()) {
            passActive = true;
            passExpiry = activePass.getExpiryDate();
            // Pass holders pay ₹0 hourly fee
            passDiscount = baseFee + overnightFee;
        }

        // Rule 5: Lost ticket penalty
        double penalty = isLostTicket ? getLostTicketPenalty() : 0.0;

        double totalAmount = Math.max(0.0, (baseFee + overnightFee - passDiscount) + penalty);

        return new FeeBreakdown(ticketNumber, licensePlate, vehicleType, entryTime, exitTime,
                durationMinutes, billedHours, baseFee, multiplier, peakActive,
                passDiscount, passActive, passExpiry, penalty, overnightFee, totalAmount);
    }

    public FeeBreakdown calculateFee(ParkingTicket ticket, LocalDateTime exitTime, boolean isLostTicket, MembershipPass activePass) {
        String tNum = ticket != null ? ticket.getTicketNumber() : "TKT-UNKNOWN";
        String plate = ticket != null ? ticket.getLicensePlate() : "UNKNOWN";
        VehicleType vType = ticket != null ? ticket.getVehicleType() : VehicleType.CAR;
        LocalDateTime inTime = ticket != null ? ticket.getEntryTime() : LocalDateTime.now();
        return calculateFee(tNum, plate, vType, inTime, exitTime, isLostTicket, activePass);
    }

    /**
     * Authorises and records an administrative fee override. Callers must apply the new fee only after this returns:
     * it throws (and nothing may be applied) if the user lacks OVERRIDE_FEE or the audit entry cannot be written.
     * The entry is attributed to the signed-in user; {@code performedBy} is used only for in-process calls without
     * a session.
     */
    public void recordFeeOverride(String ticketNumber, double oldFee, double newFee, String reason, String performedBy) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.OVERRIDE_FEE);
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("A reason of at least 5 characters is required for a fee override.");
        }
        if (newFee < 0 || Double.isNaN(newFee) || Double.isInfinite(newFee)) {
            throw new IllegalArgumentException("The overridden fee must be a non-negative amount.");
        }
        String oldStr = String.format("₹%.2f", oldFee);
        String newStr = String.format("₹%.2f", newFee);
        AuditLogService audit = AuditLogService.getInstance();
        if (UserSession.getInstance().isAuthenticated()) {
            audit.log("FEE_OVERRIDE", "TICKET", ticketNumber, oldStr, newStr, reason.trim(), "SUCCESS");
        } else {
            audit.logWithActor(null, performedBy != null ? performedBy : "SYSTEM", "SYSTEM", "FEE_OVERRIDE",
                    "TICKET", ticketNumber, oldStr, newStr, reason.trim(), "SUCCESS");
        }
    }

    // --- Configuration Accessors ---

    public double getBaseHourlyRate(SlotType type) {
        if (type == null) return 40.0;
        return switch (type) {
            case COMPACT -> settingsDao.getDouble("rate_compact", 20.0);
            case REGULAR -> settingsDao.getDouble("rate_regular", 40.0);
            case LARGE -> settingsDao.getDouble("rate_large", 80.0);
            case ELECTRIC -> settingsDao.getDouble("rate_electric", 60.0);
            case ACCESSIBLE, HANDICAPPED -> settingsDao.getDouble("rate_handicapped", 30.0);
        };
    }

    public double getGstPercentage() {
        return settingsDao.getDouble("gst_percentage", 18.0);
    }

    public int getGracePeriodMinutes() {
        return settingsDao.getInt("grace_period_minutes", 15);
    }

    public int getReservationGraceMinutes() {
        return settingsDao.getInt("reservation_grace_minutes", 15);
    }

    public double getLostTicketPenalty() {
        return settingsDao.getDouble("lost_ticket_penalty", 250.0);
    }

    public double getOverstayPenaltyPerHour() {
        return settingsDao.getDouble("overstay_penalty_per_hour", 50.0);
    }

    public double getPeakMultiplier() {
        return settingsDao.getDouble("peak_multiplier", 1.0);
    }

    public boolean isPeakEnabled() {
        return settingsDao.getBoolean("peak_enabled", false);
    }

    public void setPeakSettings(double multiplier, boolean enabled) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.UPDATE_TARIFFS);
        }
        // Both settings and the TARIFF_UPDATED entry commit together, or none does.
        SqliteTransaction.immediate(conn -> {
            String old = "multiplier=" + settingsDao.get(conn, "peak_multiplier").orElse("unset")
                    + ", enabled=" + settingsDao.get(conn, "peak_enabled").orElse("unset");
            settingsDao.set(conn, "peak_multiplier", String.valueOf(multiplier), "Peak Multiplier");
            settingsDao.set(conn, "peak_enabled", String.valueOf(enabled), "Peak Enabled");
            AuditLogService.getInstance().recordInTransactionAsSessionUser(conn, "TARIFF_UPDATED", "SETTINGS", "PEAK_SETTINGS",
                    old, "multiplier=" + multiplier + ", enabled=" + enabled, "Admin updated peak multiplier settings", "SUCCESS");
            return null;
        });
    }

    public double getPeakMultiplier(double occupancyPercentage) {
        if (isPeakEnabled()) {
            return getPeakMultiplier();
        }
        if (occupancyPercentage >= 90.0) {
            return settingsDao.getDouble("peak_multiplier_90", 2.0);
        } else if (occupancyPercentage >= 80.0) {
            return settingsDao.getDouble("peak_multiplier_80", 1.5);
        } else if (occupancyPercentage >= 70.0) {
            return settingsDao.getDouble("peak_multiplier_70", 1.25);
        }
        return 1.0;
    }

    public double getEvChargingRatePerKwh() {
        return settingsDao.getDouble("ev_charging_rate_per_kwh", 15.0);
    }

    public String getCurrencySymbol() {
        return settingsDao.getString("currency_symbol", "₹");
    }

    public String getCurrencyCode() {
        return settingsDao.getString("currency_code", "INR");
    }

    public ZoneId getTimezone() {
        return ZoneId.of(settingsDao.getString("timezone", "Asia/Kolkata"));
    }
}
