package com.smartparking.service;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.tariff.FeeBreakdown;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.SystemSettingsDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test Suite for TariffService and SystemSettingsDao.
 * Verifies that all financial constants (rates, GST, penalties, grace periods)
 * are cleanly loaded from the SQLite settings table (Single Source of Truth)
 * rather than hardcoded in business logic, prepared for college project viva defense.
 */
@DisplayName("Tariff & Settings Configuration Service Tests")
class TariffServiceTest {

    private static TariffService tariffService;
    private static SystemSettingsDao settingsDao;

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_tariff_service");
        DatabaseInitializer.initializeDatabase();

        settingsDao = new SystemSettingsDao();
        tariffService = new TariffService(settingsDao);
    }

    @Test
    @DisplayName("Should load currency symbol as INR ₹ and timezone as Asia/Kolkata")
    void testCurrencyAndTimezone() {
        assertEquals("₹", tariffService.getCurrencySymbol(), "Currency symbol must be INR ₹");
        assertEquals("INR", tariffService.getCurrencyCode(), "Currency code must be INR");
        assertEquals("Asia/Kolkata", tariffService.getTimezone().getId(), "Timezone must be Indian Standard Time (Asia/Kolkata)");
    }

    @Test
    @DisplayName("Should retrieve correct GST percentage and grace duration from settings table")
    void testGstAndGracePeriod() {
        assertEquals(18.0, tariffService.getGstPercentage(), 0.001, "GST rate must be 18%");
        assertEquals(15, tariffService.getGracePeriodMinutes(), "Grace period must be 15 minutes");
        assertEquals(15, tariffService.getReservationGraceMinutes(), "Reservation grace period must be 15 minutes");
    }

    @Test
    @DisplayName("Should retrieve correct penalties and peak-hour multipliers from settings")
    void testPenaltiesAndMultipliers() {
        assertEquals(250.0, tariffService.getLostTicketPenalty(), 0.01, "Lost ticket penalty must be ₹250");
        assertEquals(50.0, tariffService.getOverstayPenaltyPerHour(), 0.01, "Overstay penalty must be ₹50/hr");

        // Surge multipliers
        assertEquals(1.25, tariffService.getPeakMultiplier(72.0), 0.01, "Surge at 72% occupancy should be 1.25x");
        assertEquals(1.50, tariffService.getPeakMultiplier(82.0), 0.01, "Surge at 82% occupancy should be 1.50x");
        assertEquals(2.00, tariffService.getPeakMultiplier(92.0), 0.01, "Surge at 92% occupancy should be 2.00x");
        assertEquals(1.00, tariffService.getPeakMultiplier(50.0), 0.01, "Normal load (<70%) should have 1.0x multiplier");
    }

    @Test
    @DisplayName("Should return configured hourly rates for all slot types in INR")
    void testHourlyRatesBySlotType() {
        assertEquals(20.0, tariffService.getBaseHourlyRate(SlotType.COMPACT), 0.01, "Compact slot rate ₹20/hr");
        assertEquals(40.0, tariffService.getBaseHourlyRate(SlotType.REGULAR), 0.01, "Regular slot rate ₹40/hr");
        assertEquals(80.0, tariffService.getBaseHourlyRate(SlotType.LARGE), 0.01, "Large slot rate ₹80/hr");
        assertEquals(60.0, tariffService.getBaseHourlyRate(SlotType.ELECTRIC), 0.01, "EV slot rate ₹60/hr");
        assertEquals(30.0, tariffService.getBaseHourlyRate(SlotType.HANDICAPPED), 0.01, "Handicapped slot rate ₹30/hr");
    }

    @Test
    @DisplayName("Should verify 15-minute grace period charges exactly ₹0.00")
    void testGracePeriodChargesZero() {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 7, 10, 0);
        LocalDateTime exit = entry.plusMinutes(14);

        FeeBreakdown fee = tariffService.calculateFee("TKT-1", "UP32AB1234", VehicleType.CAR, entry, exit, false, null);
        assertEquals(0.0, fee.getTotalAmount(), 0.01, "Stay under 15 min must be free");
        assertEquals(0.0, fee.getBaseFee(), 0.01);
    }

    @Test
    @DisplayName("Should verify started-hour billing for Car (40 1st hr, 20 extra hr)")
    void testCarHourlyTariff() {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 7, 10, 0);

        // 16 min -> 1 started hr (₹40)
        FeeBreakdown fee16m = tariffService.calculateFee("TKT-2", "UP32AB1234", VehicleType.CAR, entry, entry.plusMinutes(16), false, null);
        assertEquals(40.0, fee16m.getTotalAmount(), 0.01);

        // 65 min -> 2 started hrs (40 + 20 = ₹60)
        FeeBreakdown fee65m = tariffService.calculateFee("TKT-3", "UP32AB1234", VehicleType.CAR, entry, entry.plusMinutes(65), false, null);
        assertEquals(60.0, fee65m.getTotalAmount(), 0.01);

        // 3 hours -> 40 + 2*20 = ₹80
        FeeBreakdown fee3h = tariffService.calculateFee("TKT-4", "UP32AB1234", VehicleType.CAR, entry, entry.plusHours(3), false, null);
        assertEquals(80.0, fee3h.getTotalAmount(), 0.01);
    }

    @Test
    @DisplayName("Should verify Daily Cap applies (₹200 for Car within same day, plus overnight if crossing midnight)")
    void testDailyCap() {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 7, 8, 0);
        LocalDateTime sameDayExit = entry.plusHours(12); // 40 + 11*20 = 260 > 200 cap

        FeeBreakdown sameDayFee = tariffService.calculateFee("TKT-5A", "UP32AB1234", VehicleType.CAR, entry, sameDayExit, false, null);
        assertEquals(200.0, sameDayFee.getTotalAmount(), 0.01, "Daily cap for Car is ₹200.00");

        // Crossing midnight adds flat overnight fee (100)
        LocalDateTime overnightExit = entry.plusHours(20); // Nov 8th 4:00 AM
        FeeBreakdown overnightFee = tariffService.calculateFee("TKT-5B", "UP32AB1234", VehicleType.CAR, entry, overnightExit, false, null);
        assertEquals(300.0, overnightFee.getTotalAmount(), 0.01, "Daily cap ₹200 + overnight ₹100 = ₹300.00");
    }

    @Test
    @DisplayName("Should verify active monthly pass holders receive ₹0.00 hourly fee")
    void testActiveMonthlyPassWaiver() {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 7, 10, 0);
        LocalDateTime exit = entry.plusHours(5);

        MembershipPass pass = new MembershipPass(1, 1, "UP32AB1234", VehicleType.CAR,
                1, 1500.0, 1500.0, LocalDate.now().minusDays(5), LocalDate.now().plusDays(25), true);

        FeeBreakdown fee = tariffService.calculateFee("TKT-6", "UP32AB1234", VehicleType.CAR, entry, exit, false, pass);
        assertEquals(0.0, fee.getTotalAmount(), 0.01, "Active pass holder pays ₹0 hourly fee");
        assertTrue(fee.isPassActive());
    }

    @Test
    @DisplayName("Should verify lost ticket penalty of ₹250 is applied")
    void testLostTicketPenalty() {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 7, 10, 0);
        LocalDateTime exit = entry.plusMinutes(10); // inside grace, but lost ticket

        FeeBreakdown fee = tariffService.calculateFee("TKT-7", "UP32AB1234", VehicleType.CAR, entry, exit, true, null);
        assertEquals(250.0, fee.getLostTicketPenalty(), 0.01, "Lost ticket penalty must be ₹250.00");
        assertEquals(250.0, fee.getTotalAmount(), 0.01);
    }
}
