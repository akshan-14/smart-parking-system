package com.smartparking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for GST Tax Calculation and Round-off Rules")
class GstCalculatorTest {

    private GstCalculator gstCalculator;

    @BeforeEach
    void setUp() {
        gstCalculator = new GstCalculator(18.0, "09AAAAA0000A1Z5", "Grand Tech Park Parking Ltd", "Greater Noida, UP");
    }

    @Test
    @DisplayName("Calculate GST for 18% on ₹60: CGST 5.40, SGST 5.40, round-off +0.20, grand total 71")
    void testSixtyRupeesGst() {
        GstCalculator.GstResult res = gstCalculator.calculate(60.0);

        assertEquals(60.0, res.taxableValue(), 0.001);
        assertEquals(9.0, res.cgstRatePercent(), 0.001);
        assertEquals(5.40, res.cgstAmount(), 0.001);
        assertEquals(9.0, res.sgstRatePercent(), 0.001);
        assertEquals(5.40, res.sgstAmount(), 0.001);
        assertEquals(0.20, res.roundOff(), 0.001);
        assertEquals(71.0, res.grandTotal(), 0.001);
    }

    @Test
    @DisplayName("Lost ticket penalty included in taxable value before GST")
    void testLostTicketPenaltyTaxableValue() {
        // Base fee = ₹40.0, Lost ticket penalty = ₹250.0 -> Taxable = ₹290.0
        double taxableWithLostPenalty = 40.0 + 250.0;
        GstCalculator.GstResult res = gstCalculator.calculate(taxableWithLostPenalty);

        assertEquals(290.0, res.taxableValue(), 0.001);
        assertEquals(26.10, res.cgstAmount(), 0.001);
        assertEquals(26.10, res.sgstAmount(), 0.001);
        assertEquals(-0.20, res.roundOff(), 0.001);
        assertEquals(342.0, res.grandTotal(), 0.001);
    }

    @Test
    @DisplayName("Calculate GST for standard taxable amount with half-up rounding")
    void testStandardGstCalculation() {
        // Taxable: ₹80.00
        // 9% CGST = ₹7.20
        // 9% SGST = ₹7.20
        // Total before round-off = 80 + 7.20 + 7.20 = 94.40
        // Nearest rupee = 94.00, round-off = -0.40
        GstCalculator.GstResult res = gstCalculator.calculate(80.0);

        assertEquals(80.0, res.taxableValue(), 0.001);
        assertEquals(9.0, res.cgstRatePercent(), 0.001);
        assertEquals(7.20, res.cgstAmount(), 0.001);
        assertEquals(9.0, res.sgstRatePercent(), 0.001);
        assertEquals(7.20, res.sgstAmount(), 0.001);
        assertEquals(-0.40, res.roundOff(), 0.001);
        assertEquals(94.0, res.grandTotal(), 0.001);
    }

    @Test
    @DisplayName("Calculate GST where fraction >= 0.50 rounds UP to nearest rupee")
    void testGstRoundingUp() {
        // Taxable: ₹123.00
        // 9% CGST = 11.07
        // 9% SGST = 11.07
        // Total before round-off = 123 + 22.14 = 145.14 -> 145.00
        // Taxable: ₹130.00
        // 9% CGST = 11.70
        // 9% SGST = 11.70
        // Total = 130 + 23.40 = 153.40 -> 153.00
        // Taxable: ₹140.00
        // 9% CGST = 12.60
        // 9% SGST = 12.60
        // Total = 140 + 25.20 = 165.20 -> 165.00
        // Taxable: ₹105.00
        // 9% CGST = 9.45
        // 9% SGST = 9.45
        // Total = 105 + 18.90 = 123.90 -> 124.00, round-off = +0.10
        GstCalculator.GstResult res = gstCalculator.calculate(105.0);

        assertEquals(105.0, res.taxableValue(), 0.001);
        assertEquals(9.45, res.cgstAmount(), 0.001);
        assertEquals(9.45, res.sgstAmount(), 0.001);
        assertEquals(0.10, res.roundOff(), 0.001);
        assertEquals(124.0, res.grandTotal(), 0.001);
    }

    @Test
    @DisplayName("Free tickets (grace period or active pass) yield ₹0 taxable and ₹0 GST")
    void testFreeTicketGst() {
        GstCalculator.GstResult res = gstCalculator.calculate(0.0);

        assertEquals(0.0, res.taxableValue(), 0.001);
        assertEquals(0.0, res.cgstAmount(), 0.001);
        assertEquals(0.0, res.sgstAmount(), 0.001);
        assertEquals(0.0, res.roundOff(), 0.001);
        assertEquals(0.0, res.grandTotal(), 0.001);
    }

    @Test
    @DisplayName("Negative amounts are protected and treated as ₹0")
    void testNegativeAmountGst() {
        GstCalculator.GstResult res = gstCalculator.calculate(-50.0);

        assertEquals(0.0, res.taxableValue(), 0.001);
        assertEquals(0.0, res.grandTotal(), 0.001);
    }
}
