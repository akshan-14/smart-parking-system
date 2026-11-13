package com.smartparking.service;

import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.tariff.TariffRate;
import com.smartparking.repository.SettingsHistoryDao;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.repository.TariffDao;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;
import com.smartparking.security.UserSession;
import com.smartparking.util.DateFormat;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service managing System Settings, Tariffs, Validation, Audit Logging, and Historical Tracking.
 * Enforces single source of truth across all operational configuration parameters.
 */
public class SettingsService {

    private static final Pattern GSTIN_PATTERN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final Pattern UPI_VPA_PATTERN = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,}@[a-zA-Z]{2,}$");

    private final SystemSettingsDao settingsDao;
    private final TariffDao tariffDao;
    private final SettingsHistoryDao historyDao;
    private final AuditLogService auditLogService;

    public SettingsService(SystemSettingsDao settingsDao, TariffDao tariffDao,
                           SettingsHistoryDao historyDao, AuditLogService auditLogService) {
        this.settingsDao = settingsDao;
        this.tariffDao = tariffDao;
        this.historyDao = historyDao;
        this.auditLogService = auditLogService;
    }

    public SettingsService() {
        this(new SystemSettingsDao(), new TariffDao(), new SettingsHistoryDao(), AuditLogService.getInstance());
    }

    // --- Accessor Methods ---

    public Map<String, String> getAllSettings() {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.VIEW_SETTINGS);
        }
        return settingsDao.getAll();
    }

    public List<TariffRate> getAllTariffs() {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.VIEW_SETTINGS);
        }
        return tariffDao.findAll();
    }

    public String getSetting(String key, String defVal) {
        return settingsDao.getString(key, defVal);
    }

    public double getDoubleSetting(String key, double defVal) {
        return settingsDao.getDouble(key, defVal);
    }

    public int getIntSetting(String key, int defVal) {
        return settingsDao.getInt(key, defVal);
    }

    public boolean getBooleanSetting(String key, boolean defVal) {
        return settingsDao.getBoolean(key, defVal);
    }

    // --- Validation Methods ---

    public void validateGstin(String gstin) {
        if (gstin == null || !GSTIN_PATTERN.matcher(gstin.trim()).matches()) {
            throw new IllegalArgumentException("Invalid GSTIN. Must match 15-character Indian GST format (e.g. 09AAAAA0000A1Z5)");
        }
    }

    public void validateUpiVpa(String upi) {
        if (upi == null || !UPI_VPA_PATTERN.matcher(upi.trim()).matches()) {
            throw new IllegalArgumentException("Invalid UPI VPA address (e.g. parking@upi)");
        }
    }

    public void validateGstRate(double gstRate) {
        if (gstRate < 0.0 || gstRate > 28.0) {
            throw new IllegalArgumentException("GST rate must be between 0% and 28%");
        }
    }

    public void validateMultiplier(double mult) {
        if (mult < 1.0 || mult > 3.0) {
            throw new IllegalArgumentException("Peak multiplier must be between 1.0 and 3.0");
        }
    }

    public void validatePositive(double val, String fieldName) {
        if (val < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be a positive number.");
        }
    }

    public void validatePositiveInt(int val, String fieldName) {
        if (val <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero.");
        }
    }

    // --- Update Methods with Audit Logging & History ---

    public synchronized void updateSetting(String key, String newValue, String description) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.VIEW_SETTINGS);
            AccessControl.checkPermission(Permission.UPDATE_TARIFFS);
        }

        String oldValue = settingsDao.getString(key, "");
        if (oldValue.equals(newValue)) {
            return; // No change
        }

        settingsDao.set(key, newValue, description);

        String currentUser = UserSession.getInstance().getCurrentUser()
                .map(u -> u.getUsername()).orElse("SYSTEM");

        historyDao.recordChange(key, oldValue, newValue, currentUser);

        auditLogService.log("SETTING_UPDATED", "SETTINGS", key,
                oldValue, newValue, "Setting updated: " + description, "SUCCESS");
    }

    public synchronized void updateTariff(TariffRate rate) {
        if (UserSession.getInstance().isAuthenticated()) {
            AccessControl.checkPermission(Permission.VIEW_SETTINGS);
            AccessControl.checkPermission(Permission.UPDATE_TARIFFS);
        }

        if (rate == null) throw new IllegalArgumentException("Tariff rate cannot be null.");
        validatePositive(rate.getFirstHourRate(), "First hour rate");
        validatePositive(rate.getExtraHourRate(), "Extra hour rate");
        validatePositive(rate.getDailyCap(), "Daily cap");
        validatePositive(rate.getOvernightRate(), "Overnight rate");
        validatePositive(rate.getMonthlyPassRate(), "Monthly pass rate");

        Optional<TariffRate> oldRateOpt = tariffDao.findByVehicleType(rate.getVehicleType());
        String oldStr = oldRateOpt.map(TariffRate::toString).orElse("NONE");
        String newStr = rate.toString();

        tariffDao.updateTariff(rate);

        String currentUser = UserSession.getInstance().getCurrentUser()
                .map(u -> u.getUsername()).orElse("SYSTEM");

        historyDao.recordChange("tariff." + rate.getVehicleType().name(), oldStr, newStr, currentUser);

        auditLogService.log("TARIFF_UPDATED", "TARIFF", rate.getVehicleType().name(),
                oldStr, newStr, "Admin updated vehicle tariff for " + rate.getVehicleType(), "SUCCESS");
    }

    // --- Sample Fee Preview Calculation ---

    public record FeePreviewResult(
            double firstHourRate,
            double extraHourRate,
            double dailyCap,
            double overnightRate,
            double baseFee,
            double peakSurge,
            double overnightFee,
            double penaltyFee,
            double totalFee
    ) {}

    public FeePreviewResult previewFee(VehicleType vType, long durationMinutes, boolean isOvernight,
                                       boolean isLostTicket, double firstHour, double extraHour,
                                       double dailyCap, double overnightRate, double multiplier,
                                       boolean peakOn, int graceMinutes, double lostTicketPenalty) {
        if (durationMinutes <= graceMinutes) {
            return new FeePreviewResult(firstHour, extraHour, dailyCap, overnightRate, 0, 0, 0, isLostTicket ? lostTicketPenalty : 0, isLostTicket ? lostTicketPenalty : 0);
        }

        long billedHours = (durationMinutes + 59) / 60;
        double baseFee;
        if (billedHours <= 24) {
            double uncapped = firstHour + (billedHours - 1) * extraHour;
            baseFee = Math.min(uncapped, dailyCap);
        } else {
            long fullDays = billedHours / 24;
            long remHours = billedHours % 24;
            double remFee = 0.0;
            if (remHours > 0) {
                double uncappedRem = firstHour + (remHours - 1) * extraHour;
                remFee = Math.min(uncappedRem, dailyCap);
            }
            baseFee = (fullDays * dailyCap) + remFee;
        }

        double surge = 0.0;
        if (peakOn && multiplier > 1.0) {
            surge = baseFee * (multiplier - 1.0);
        }

        double overnightFee = isOvernight ? overnightRate : 0.0;
        double penalty = isLostTicket ? lostTicketPenalty : 0.0;
        double total = (baseFee + surge + overnightFee) + penalty;

        return new FeePreviewResult(firstHour, extraHour, dailyCap, overnightRate, baseFee, surge, overnightFee, penalty, total);
    }

    // --- Reset to Defaults by Category ---

    public synchronized void resetTariffsToDefaults() {
        updateTariff(new TariffRate(VehicleType.MOTORCYCLE, 10.0, 5.0, 40.0, 30.0, 300.0));
        updateTariff(new TariffRate(VehicleType.EV_TWO_WHEELER, 10.0, 5.0, 40.0, 30.0, 300.0));
        updateTariff(new TariffRate(VehicleType.AUTO_RICKSHAW, 20.0, 10.0, 80.0, 100.0, 600.0));
        updateTariff(new TariffRate(VehicleType.CAR, 40.0, 20.0, 200.0, 100.0, 1500.0));
        updateTariff(new TariffRate(VehicleType.EV_CAR, 40.0, 20.0, 200.0, 100.0, 1500.0));
        updateTariff(new TariffRate(VehicleType.LCV, 80.0, 40.0, 400.0, 200.0, 3000.0));
        updateTariff(new TariffRate(VehicleType.HCV, 100.0, 50.0, 500.0, 200.0, 4500.0));
    }

    public synchronized void resetBillingToDefaults() {
        updateSetting("grace_period_minutes", "15", "Free parking grace duration in minutes");
        updateSetting("lost_ticket_penalty", "250.0", "Standard penalty for lost entry ticket");
        updateSetting("peak_multiplier", "1.0", "Admin toggle peak multiplier");
        updateSetting("peak_enabled", "false", "Whether dynamic peak multiplier is currently active");
        updateSetting("peak_schedule", "WEEKENDS,FESTIVALS", "Configured peak multiplier schedule");
        updateSetting("peak_festivals", "2026-11-01:2026-11-05:Diwali,2026-03-24:2026-03-26:Holi", "Festival date ranges");
    }

    public synchronized void resetTaxAndBusinessToDefaults() {
        // Note: verify applicable GST circular and registered business details
        updateSetting("gst_rate_percent", "18.0", "Applicable GST tax percentage");
        updateSetting("gst_percentage", "18.0", "Applicable GST tax percentage");
        updateSetting("gstin", "09AAAAA0000A1Z5", "Business GSTIN (Uttar Pradesh 09)");
        updateSetting("business_name", "Grand Tech Park Parking Ltd", "Registered business name");
        updateSetting("business_address", "Plot 42, Knowledge Park III, Greater Noida, UP 201306", "Registered business address");
        updateSetting("upi_vpa", "parking@upi", "UPI Virtual Payment Address");
    }

    public synchronized void resetPassesToDefaults() {
        // Flat monthly passes per vehicle class
        tariffDao.findByVehicleType(VehicleType.MOTORCYCLE).ifPresent(t -> updateTariff(new TariffRate(t.getVehicleType(), t.getFirstHourRate(), t.getExtraHourRate(), t.getDailyCap(), t.getOvernightRate(), 300.0)));
        tariffDao.findByVehicleType(VehicleType.EV_TWO_WHEELER).ifPresent(t -> updateTariff(new TariffRate(t.getVehicleType(), t.getFirstHourRate(), t.getExtraHourRate(), t.getDailyCap(), t.getOvernightRate(), 300.0)));
        tariffDao.findByVehicleType(VehicleType.AUTO_RICKSHAW).ifPresent(t -> updateTariff(new TariffRate(t.getVehicleType(), t.getFirstHourRate(), t.getExtraHourRate(), t.getDailyCap(), t.getOvernightRate(), 600.0)));
        tariffDao.findByVehicleType(VehicleType.CAR).ifPresent(t -> updateTariff(new TariffRate(t.getVehicleType(), t.getFirstHourRate(), t.getExtraHourRate(), t.getDailyCap(), t.getOvernightRate(), 1500.0)));
        tariffDao.findByVehicleType(VehicleType.EV_CAR).ifPresent(t -> updateTariff(new TariffRate(t.getVehicleType(), t.getFirstHourRate(), t.getExtraHourRate(), t.getDailyCap(), t.getOvernightRate(), 1500.0)));
        tariffDao.findByVehicleType(VehicleType.LCV).ifPresent(t -> updateTariff(new TariffRate(t.getVehicleType(), t.getFirstHourRate(), t.getExtraHourRate(), t.getDailyCap(), t.getOvernightRate(), 3000.0)));
        tariffDao.findByVehicleType(VehicleType.HCV).ifPresent(t -> updateTariff(new TariffRate(t.getVehicleType(), t.getFirstHourRate(), t.getExtraHourRate(), t.getDailyCap(), t.getOvernightRate(), 4500.0)));
    }

    public synchronized void resetSecurityToDefaults() {
        updateSetting("auth.max_failed_attempts", "5", "Maximum consecutive failed login attempts before lockout");
        updateSetting("auth.lockout_duration_minutes", "15", "Account lockout duration in minutes");
        updateSetting("auth.session_timeout_minutes", "15", "Session inactivity timeout in minutes");
        updateSetting("retention.ticket_days", "365", "DPDP statutory retention period for parking ticket and vehicle data");
        updateSetting("retention.image_days", "30", "DPDP statutory retention period for ANPR image snapshots");
        updateSetting("backup.retention_count", "7", "Number of live rolling database backups retained");
    }

    public synchronized void resetEvChargingToDefaults() {
        updateSetting("ev_charging_rate_per_kwh", "15.0", "Electricity charging tariff per kWh");
        updateSetting("ev_charging_idle_fee_per_min", "2.0", "Idle fee per minute after charging completes in INR");
        updateSetting("ev_charging_grace_minutes", "15", "Grace minutes before idle fee applies");
        updateSetting("ev_charging_gst_rate_percent", "18.0", "GST rate for EV charging services (verify applicable GST circular)");
    }
}
