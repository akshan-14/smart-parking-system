package com.smartparking.service;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.repository.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Membership Service Flat Fee & Lifecycle Tests")
class MembershipServiceTest {

    private static MembershipService membershipService;

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_membership_service");
        DatabaseInitializer.initializeDatabase();

        membershipService = new MembershipService();
    }

    @Test
    @DisplayName("Should verify flat pricing calculation by vehicle class and duration")
    void testFlatPricingCalculation() {
        // Motorcycle: ₹300/mo
        assertEquals(300.0, membershipService.calculatePassPrice(VehicleType.MOTORCYCLE, 1), 0.01);
        assertEquals(900.0, membershipService.calculatePassPrice(VehicleType.MOTORCYCLE, 3), 0.01);

        // Auto Rickshaw: ₹600/mo
        assertEquals(600.0, membershipService.calculatePassPrice(VehicleType.AUTO_RICKSHAW, 1), 0.01);

        // Car: ₹1,500/mo
        assertEquals(1500.0, membershipService.calculatePassPrice(VehicleType.CAR, 1), 0.01);
        assertEquals(4500.0, membershipService.calculatePassPrice(VehicleType.CAR, 3), 0.01);

        // HCV: ₹4,500/mo
        assertEquals(4500.0, membershipService.calculatePassPrice(VehicleType.HCV, 1), 0.01);
    }

    @Test
    @DisplayName("Should issue pass and block duplicate active pass for same vehicle plate")
    void testIssuePassAndDuplicateRejection() {
        String plate = "UP32MB9999";
        MembershipPass pass = membershipService.issuePass(plate, VehicleType.CAR, 1);
        assertNotNull(pass);
        assertEquals("UP32MB9999", pass.getLicensePlate());
        assertEquals("ACTIVE", pass.getStatus());
        assertEquals(1500.0, pass.getTotalPrice(), 0.01);

        // Attempting to issue another active pass for same plate must fail with exact message
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                membershipService.issuePass(plate, VehicleType.CAR, 1)
        );
        assertEquals("An active pass already exists for this vehicle. Use Renew instead.", ex.getMessage());
    }

    @Test
    @DisplayName("Should renew existing pass extending expiration date")
    void testRenewPass() {
        String plate = "UP16RN1234";
        MembershipPass pass = membershipService.issuePass(plate, VehicleType.MOTORCYCLE, 1);
        LocalDate oldExpiry = pass.getExpiryDate();

        MembershipPass renewed = membershipService.renewMembership(pass.getMembershipId(), 2);
        assertEquals(oldExpiry.plusMonths(2), renewed.getExpiryDate());
        assertEquals(3, renewed.getDurationMonths());
    }

    @Test
    @DisplayName("Should cancel pass and transition status to CANCELLED")
    void testCancelPass() {
        String plate = "UP70CN5678";
        MembershipPass pass = membershipService.issuePass(plate, VehicleType.CAR, 1);

        membershipService.cancelMembership(pass.getMembershipId());
        Optional<MembershipPass> active = membershipService.getActiveMembership(plate);
        assertFalse(active.isPresent(), "Cancelled pass should not be returned as active");
    }
}
