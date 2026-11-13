package com.smartparking.patterns.builder;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.PaymentStatus;
import com.smartparking.model.enums.PenaltyType;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.ticket.Penalty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Builder Pattern Verification Tests")
class BuilderTest {

    @Test
    @DisplayName("TicketBuilder successfully builds valid ParkingTicket with custom attributes")
    void testTicketBuilderSuccess() {
        LocalDateTime now = LocalDateTime.now();
        Penalty penalty = new Penalty(1, 10, PenaltyType.OVERSTAY, 25.0, "Overstayed by 2 hours", now);

        ParkingTicket ticket = TicketBuilder.newInstance()
                .setTicketId(10)
                .setTicketNumber("TKT-TEST-001")
                .setLicensePlate("UP32AB1234")
                .setVehicleType(VehicleType.CAR)
                .setSlotId("A1")
                .setFloorNumber(1)
                .setEntryTime(now.minusHours(3))
                .setExitTime(now)
                .setStatus(TicketStatus.PAID)
                .setBaseFee(30.0)
                .addPenalty(penalty)
                .setEvChargingFee(0.0)
                .setTotalAmount(55.0)
                .build();

        assertNotNull(ticket);
        assertEquals(10, ticket.getTicketId());
        assertEquals("TKT-TEST-001", ticket.getTicketNumber());
        assertEquals("UP32AB1234", ticket.getLicensePlate());
        assertEquals(VehicleType.CAR, ticket.getVehicleType());
        assertEquals("A1", ticket.getSlotId());
        assertEquals(1, ticket.getFloorNumber());
        assertEquals(TicketStatus.PAID, ticket.getStatus());
        assertEquals(30.0, ticket.getBaseFee());
        assertEquals(25.0, ticket.getPenaltyFee());
        assertEquals(55.0, ticket.getTotalAmount());
        assertEquals(1, ticket.getPenalties().size());
    }

    @Test
    @DisplayName("TicketBuilder throws IllegalStateException when mandatory fields are omitted")
    void testTicketBuilderMissingFields() {
        // Missing license plate
        assertThrows(IllegalStateException.class, () ->
                TicketBuilder.newInstance()
                        .setVehicleType(VehicleType.CAR)
                        .setSlotId("A1")
                        .build()
        );

        // Missing vehicle type
        assertThrows(IllegalStateException.class, () ->
                TicketBuilder.newInstance()
                        .setLicensePlate("UP32AB1234")
                        .setSlotId("A1")
                        .build()
        );

        // Missing slot id
        assertThrows(IllegalStateException.class, () ->
                TicketBuilder.newInstance()
                        .setLicensePlate("UP32AB1234")
                        .setVehicleType(VehicleType.CAR)
                        .build()
        );
    }

    @Test
    @DisplayName("ReceiptBuilder formats comprehensive itemized customer receipt")
    void testReceiptBuilderFormat() {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 6, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 6, 14, 0, 0);

        ParkingTicket ticket = TicketBuilder.newInstance()
                .setTicketNumber("TKT-RECEIPT-99")
                .setLicensePlate("UP32ZZ9999")
                .setVehicleType(VehicleType.ELECTRIC_CAR)
                .setSlotId("B2")
                .setFloorNumber(2)
                .setEntryTime(entry)
                .setExitTime(exit)
                .setBaseFee(40.0)
                .setEvChargingFee(15.0)
                .setTotalAmount(55.0)
                .build();

        PaymentTransaction txn = new PaymentTransaction(1, 99, null, 55.0, PaymentMethodType.UPI,
                "TXN-UPI-ABC12345", PaymentStatus.COMPLETED, exit);

        String receipt = ReceiptBuilder.newInstance()
                .setFacilityName("Metro Smart Parking")
                .setTicket(ticket)
                .setPaymentTransaction(txn)
                .setPricingPolicy("Peak-Hour Surge")
                .setMembershipDiscount(10.0, "GOLD")
                .build();

        assertNotNull(receipt);
        assertTrue(receipt.contains("Metro Smart Parking"));
        assertTrue(receipt.contains("TKT-RECEIPT-99"));
        assertTrue(receipt.contains("UP32 ZZ 9999") || receipt.contains("UP32ZZ9999"));
        assertTrue(receipt.contains("B2 (Floor 2)"));
        assertTrue(receipt.contains("Base Parking Fee    : \u20B940.00 (Peak-Hour Surge)"));
        assertTrue(receipt.contains("EV Charging Station : \u20B915.00"));
        assertTrue(receipt.contains("Membership Discount : -\u20B910.00 (GOLD)"));
        assertTrue(receipt.contains("TOTAL PAID (Incl. GST): \u20B955.00"));
        assertTrue(receipt.contains("Unified Payments Interface (UPI)"));
        assertTrue(receipt.contains("TXN-UPI-ABC12345"));
    }
}
