package com.smartparking.security;

import com.smartparking.support.TestDatabase;
import com.smartparking.repository.ConsentDao;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.service.AuthService;
import com.smartparking.service.ConsentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 6: DPDP Statutory Consent Management Tests")
public class ConsentServiceTest {

    /** Isolated throw-away database: never touch the real smart_parking.db or another class's file. */
    @BeforeAll
    static void isolateDatabase() {
        TestDatabase.useFresh("test_consent_service");
    }

    private AuthService authService;
    private ConsentService consentService;

    @BeforeEach
    void setUp() {
        DatabaseInitializer.initializeDatabase();
        this.authService = new AuthService();
        this.consentService = new ConsentService();
    }

    @Test
    @DisplayName("Registration is rejected if DPDP consent checkbox is false")
    void testRegistrationBlockedWithoutConsent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                authService.registerCustomer("No Consent User", "9876509999", "noconsent", "Pass@1234", false));

        assertTrue(ex.getMessage().contains("Consent"));
    }

    @Test
    @DisplayName("Registration with consent succeeds and withdrawal is recorded")
    void testConsentRecordingAndWithdrawal() {
        String username = "consent_user_" + System.currentTimeMillis();
        authService.registerCustomer("Consent User", "9876508888", username, "Pass@1234", true);

        assertTrue(consentService.hasValidConsent(username, "REGISTRATION"));

        // Withdraw consent
        consentService.withdrawConsent(username, "REGISTRATION");
        assertFalse(consentService.hasValidConsent(username, "REGISTRATION"), "Consent should be marked as withdrawn");
    }
}
