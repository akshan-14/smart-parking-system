package com.smartparking.util;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.repository.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test Suite for PlateUtil.
 * Validates Phase 2 requirements for Uttar Pradesh and Bharat series number plates:
 * 1. Normalization (stripping spaces, hyphens, uppercasing, null safety).
 * 2. Regex validation for standard UP plates (^UP[0-9]{2}[A-Z]{1,3}[0-9]{4}$).
 * 3. Regex validation for Bharat series plates (^[0-9]{2}BH[0-9]{4}[A-Z]{2}$).
 * 4. Display formatting (e.g. 'UP32 AB 1234').
 * 5. District lookup from rto_codes table and fallbacks.
 * 6. Random UP plate generator validity.
 */
@DisplayName("PlateUtil - Uttar Pradesh & Bharat Plate Utility Tests")
class PlateUtilTest {

    @BeforeAll
    static void setUp() {
        String testDb = "jdbc:sqlite:test_plate_util.db";
        new File("test_plate_util.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);
        DatabaseInitializer.initializeDatabase();
    }

    @Test
    @DisplayName("Should properly normalize plate strings")
    void testNormalize() {
        assertEquals("UP32AB1234", PlateUtil.normalize("up32 ab-1234"));
        assertEquals("UP16CD5678", PlateUtil.normalize("  Up 16 - cd - 5678  "));
        assertEquals("22BH1234AA", PlateUtil.normalize("22 bh 1234 aa"));
        assertEquals("", PlateUtil.normalize(null));
        assertEquals("", PlateUtil.normalize("   "));
    }

    @Test
    @DisplayName("Should validate Uttar Pradesh (UP) registration plates")
    void testIsValidUp() {
        // Valid 2-letter series
        assertTrue(PlateUtil.isValidUp("UP32AB1234"));
        assertTrue(PlateUtil.isValidUp("up 32 - ab - 1234"));
        // Valid 1-letter series
        assertTrue(PlateUtil.isValidUp("UP14A1234"));
        // Valid 3-letter series
        assertTrue(PlateUtil.isValidUp("UP80ABC1234"));
        // Different UP RTO codes
        assertTrue(PlateUtil.isValidUp("UP16CD2345"));
        assertTrue(PlateUtil.isValidUp("UP78EF3456"));
        assertTrue(PlateUtil.isValidUp("UP70GH5678"));
        assertTrue(PlateUtil.isValidUp("UP65IJ6789"));
        assertTrue(PlateUtil.isValidUp("UP53KL7890"));
        assertTrue(PlateUtil.isValidUp("UP15MN8901"));

        // Invalid: Other states
        assertFalse(PlateUtil.isValidUp("DL01AB1234"));
        assertFalse(PlateUtil.isValidUp("KA05MJ1234"));
        assertFalse(PlateUtil.isValidUp("MH12DE1433"));

        // Invalid formats
        assertFalse(PlateUtil.isValidUp("UP321AB1234")); // 3-digit RTO
        assertFalse(PlateUtil.isValidUp("UP32ABCD1234")); // 4 letters in series
        assertFalse(PlateUtil.isValidUp("UP32AB123")); // 3 digits only
        assertFalse(PlateUtil.isValidUp(null));
        assertFalse(PlateUtil.isValidUp(""));
    }

    @Test
    @DisplayName("Should validate Bharat (BH) series registration plates")
    void testIsValidBharat() {
        assertTrue(PlateUtil.isValidBharat("22BH1234AA"));
        assertTrue(PlateUtil.isValidBharat("22 bh 1234 aa"));
        assertTrue(PlateUtil.isValidBharat("24BH9999ZZ"));

        // Invalid: missing BH or wrong length
        assertFalse(PlateUtil.isValidBharat("UP32AB1234"));
        assertFalse(PlateUtil.isValidBharat("22AB1234AA"));
        assertFalse(PlateUtil.isValidBharat("2BH1234AA"));
        assertFalse(PlateUtil.isValidBharat(null));
    }

    @Test
    @DisplayName("Should validate either UP or Bharat plates using isValid()")
    void testIsValid() {
        assertTrue(PlateUtil.isValid("UP32AB1234"));
        assertTrue(PlateUtil.isValid("22BH1234AA"));
        assertFalse(PlateUtil.isValid("DL01AB1234"));
        assertFalse(PlateUtil.isValid("KA05MJ1234"));
        assertFalse(PlateUtil.isValid(null));
    }

    @Test
    @DisplayName("Should format plate numbers for clean display")
    void testFormat() {
        assertEquals("UP32 AB 1234", PlateUtil.format("UP32AB1234"));
        assertEquals("UP32 A 1234", PlateUtil.format("UP32A1234"));
        assertEquals("UP80 ABC 1234", PlateUtil.format("UP80ABC1234"));
        assertEquals("22 BH 1234 AA", PlateUtil.format("22BH1234AA"));

        // Invalid returns original unchanged
        assertEquals("INVALID-PLATE", PlateUtil.format("INVALID-PLATE"));
        assertEquals("", PlateUtil.format(null));
    }

    @Test
    @DisplayName("Should identify district correctly from RTO code")
    void testDistrictLookup() {
        assertEquals("Lucknow", PlateUtil.district("UP32AB1234"));
        assertEquals("Ghaziabad", PlateUtil.district("UP14AB1234"));
        assertEquals("Noida (Gautam Buddh Nagar)", PlateUtil.district("UP16CD2345"));
        assertEquals("Kanpur", PlateUtil.district("UP78EF3456"));
        assertEquals("Agra", PlateUtil.district("UP80GH5678"));
        assertEquals("Varanasi", PlateUtil.district("UP65IJ6789"));
        assertEquals("Prayagraj", PlateUtil.district("UP70KL7890"));
        assertEquals("Gorakhpur", PlateUtil.district("UP53MN8901"));
        assertEquals("Meerut", PlateUtil.district("UP15OP9012"));

        // Bharat series
        assertEquals("Bharat Series", PlateUtil.district("22BH1234AA"));

        // Unknown UP code falls back to 'Uttar Pradesh'
        assertEquals("Uttar Pradesh", PlateUtil.district("UP99AB1234"));
    }

    @Test
    @DisplayName("Should generate random valid UP plates")
    void testGenerateRandomUp() {
        for (int i = 0; i < 20; i++) {
            String plate = PlateUtil.generateRandomUp();
            assertNotNull(plate);
            assertTrue(PlateUtil.isValidUp(plate), "Generated plate must be valid UP plate: " + plate);
        }
    }

    @Test
    @DisplayName("Should deterministically migrate legacy plate to UP plate")
    void testMapToUpPlate() {
        String upPlate = PlateUtil.mapToUpPlate("KA-01-AB-1234");
        assertTrue(PlateUtil.isValidUp(upPlate));

        // Same input yields same output
        assertEquals(upPlate, PlateUtil.mapToUpPlate("KA-01-AB-1234"));

        // Already valid UP plate returned normalized
        assertEquals("UP32AB1234", PlateUtil.mapToUpPlate("UP32 AB-1234"));
    }
}
