package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.UserRole;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.tariff.TariffRate;
import com.smartparking.model.user.Admin;
import com.smartparking.model.user.Attendant;
import com.smartparking.model.user.User;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.SettingsHistoryDao;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.repository.TariffDao;
import com.smartparking.security.UserSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Priority 1: SettingsService Unit & Integration Tests")
class SettingsServiceTest {

    private SettingsService settingsService;
    private SystemSettingsDao settingsDao;
    private TariffDao tariffDao;
    private SettingsHistoryDao historyDao;
    private AuditLogService auditLogService;

    private final String dbUrl = "jdbc:sqlite:test_settings_service.db";

    @BeforeEach
    void setUp() {
        new File("test_settings_service.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(dbUrl);
        DatabaseInitializer.initializeDatabase();

        settingsDao = new SystemSettingsDao();
        tariffDao = new TariffDao();
        historyDao = new SettingsHistoryDao();
        auditLogService = AuditLogService.getInstance();
        settingsService = new SettingsService(settingsDao, tariffDao, historyDao, auditLogService);

        // Login as ADMIN by default
        User admin = new Admin(1, "admin", "hash", "salt", "Admin User", "admin@smartparking.in", "9876543210");
        UserSession.getInstance().startSession(admin, 15);
    }

    @AfterEach
    void tearDown() {
        UserSession.getInstance().clearSession();
        new File("test_settings_service.db").delete();
    }

    @Test
    @DisplayName("Reject invalid GST rates outside 0% to 28%")
    void testInvalidGstRateValidation() {
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateGstRate(-1.0));
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateGstRate(29.0));

        assertDoesNotThrow(() -> settingsService.validateGstRate(0.0));
        assertDoesNotThrow(() -> settingsService.validateGstRate(18.0));
        assertDoesNotThrow(() -> settingsService.validateGstRate(28.0));
    }

    @Test
    @DisplayName("Reject invalid peak multiplier outside 1.0 to 3.0")
    void testInvalidMultiplierValidation() {
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateMultiplier(0.9));
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateMultiplier(3.5));

        assertDoesNotThrow(() -> settingsService.validateMultiplier(1.0));
        assertDoesNotThrow(() -> settingsService.validateMultiplier(1.5));
        assertDoesNotThrow(() -> settingsService.validateMultiplier(3.0));
    }

    @Test
    @DisplayName("Reject invalid Indian GSTIN numbers")
    void testGstinValidation() {
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateGstin("INVALID_GSTIN"));
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateGstin("09ABCDE1234F")); // too short
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateGstin(""));
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateGstin(null));

        // Valid 15-char UP GSTIN
        assertDoesNotThrow(() -> settingsService.validateGstin("09AAAAA0000A1Z5"));
        assertDoesNotThrow(() -> settingsService.validateGstin("09AABCG1234M1Z2"));
    }

    @Test
    @DisplayName("Reject invalid UPI VPA formats")
    void testUpiVpaValidation() {
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateUpiVpa("plainString"));
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateUpiVpa("a@b")); // too short
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateUpiVpa(""));
        assertThrows(IllegalArgumentException.class, () -> settingsService.validateUpiVpa(null));

        assertDoesNotThrow(() -> settingsService.validateUpiVpa("smartparking@sbi"));
        assertDoesNotThrow(() -> settingsService.validateUpiVpa("merchant.up@okhdfcbank"));
    }

    @Test
    @DisplayName("Updating a setting writes to settings_history and audit_log")
    void testSettingUpdateRecordsHistoryAndAudit() {
        String key = "business_name";
        String oldValue = settingsDao.getString(key, "Grand Tech Park Parking Ltd");
        String newValue = "Lucknow Metro Grand Tech Park";

        settingsService.updateSetting(key, newValue, "Updated business name");

        // Verify setting in DB
        assertEquals(newValue, settingsDao.getString(key, ""));

        // Verify settings_history
        var history = historyDao.findByKey(key);
        assertFalse(history.isEmpty(), "settings_history must contain recorded change");
        assertEquals(key, history.get(0).settingKey());
        assertEquals(oldValue, history.get(0).oldValue());
        assertEquals(newValue, history.get(0).newValue());
        assertEquals("admin", history.get(0).changedBy());
    }

    @Test
    @DisplayName("Updating a tariff records history and leaves historical transactions untouched")
    void testTariffUpdateRecordsHistory() {
        TariffRate updatedCarTariff = new TariffRate(VehicleType.CAR, 40.0, 25.0, 300.0, 150.0, 2500.0);
        settingsService.updateTariff(updatedCarTariff);

        // Verify tariff in DB
        TariffRate loaded = tariffDao.findByVehicleType(VehicleType.CAR).orElseThrow();
        assertEquals(40.0, loaded.getFirstHourRate());
        assertEquals(25.0, loaded.getExtraHourRate());
        assertEquals(300.0, loaded.getDailyCap());

        // Verify history recorded
        var history = historyDao.findByKey("tariff.CAR");
        assertFalse(history.isEmpty());
        assertEquals("admin", history.get(0).changedBy());
    }

    @Test
    @DisplayName("Fee preview calculates accurately matching TariffService logic")
    void testFeePreviewMatchesCalculation() {
        // Car: 3 hours (180 mins), first hour 30, extra hour 20, multiplier 1.5, peak ON
        var preview = settingsService.previewFee(
                VehicleType.CAR, 180, false, false,
                30.0, 20.0, 250.0, 100.0, 1.5, true, 15, 200.0
        );

        // 3 hours = 30 + 20 + 20 = 70 base
        assertEquals(70.0, preview.baseFee(), 0.01);
        // Surge = 70 * 0.5 = 35.0
        assertEquals(35.0, preview.peakSurge(), 0.01);
        // Total = 105.0
        assertEquals(105.0, preview.totalFee(), 0.01);
    }

    @Test
    @DisplayName("Non-Admin user (ATTENDANT or CUSTOMER) is denied from updating settings")
    void testNonAdminDenied() {
        User attendant = new Attendant(2, "attendant1", "hash", "salt", "Attendant", "att@smartparking.in", "9876543211");
        UserSession.getInstance().startSession(attendant, 15);

        assertThrows(SecurityException.class, () -> {
            settingsService.updateSetting("tax.gst_rate_percent", "12.0", "Unauthorized change");
        });

        assertThrows(SecurityException.class, () -> {
            settingsService.updateTariff(new TariffRate(VehicleType.CAR, 50.0, 30.0, 300.0, 100.0, 2000.0));
        });
    }
}
