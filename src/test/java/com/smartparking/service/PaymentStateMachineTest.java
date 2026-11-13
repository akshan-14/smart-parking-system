package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.PaymentStatus;
import com.smartparking.model.payment.PaymentTransaction;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.PaymentDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests for Payment Transaction Lifecycle & Status Transitions")
class PaymentStateMachineTest {

    private static PaymentDao paymentDao;

    @BeforeAll
    static void setUp() {
        String testDb = "jdbc:sqlite:test_payment_state.db";
        new File("test_payment_state.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);
        DatabaseInitializer.initializeDatabase();
        paymentDao = new PaymentDao();
    }

    @Test
    @DisplayName("Save and retrieve pending, completed, failed, and refunded payment statuses")
    void testPaymentStatuses() throws Exception {
        int ticketId = 1;
        try (var conn = DatabaseConnectionManager.getInstance().getConnection();
             var rs = conn.createStatement().executeQuery("SELECT ticket_id FROM tickets LIMIT 1")) {
            if (rs.next()) {
                ticketId = rs.getInt(1);
            }
        }

        PaymentTransaction txn = new PaymentTransaction(0, ticketId, null, null, 150.0, 127.12, 11.44, 11.44, 0.0,
                PaymentMethodType.UPI, "TXN-TEST-STATUS-1", "PK/2026-27/000999",
                PaymentStatus.PENDING, LocalDateTime.now());

        int id = paymentDao.save(txn);
        assertTrue(id > 0);

        // Transition: PENDING -> FAILED (e.g. UPI timeout or card decline)
        paymentDao.updateStatus(id, PaymentStatus.FAILED);
        PaymentTransaction failedTxn = paymentDao.findByRef("TXN-TEST-STATUS-1").orElseThrow();
        assertEquals(PaymentStatus.FAILED, failedTxn.getStatus());

        // Retry payment: FAILED -> COMPLETED (customer re-attempts with valid payment)
        paymentDao.updateStatus(id, PaymentStatus.COMPLETED);
        PaymentTransaction updated = paymentDao.findByRef("TXN-TEST-STATUS-1").orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, updated.getStatus());

        // Transition: COMPLETED -> REFUNDED with audit reason
        boolean refunded = paymentDao.refund(id, "Customer double billed due to POS glitch", "manager");
        assertTrue(refunded);

        PaymentTransaction refTxn = paymentDao.findByRef("TXN-TEST-STATUS-1").orElseThrow();
        assertEquals(PaymentStatus.REFUNDED, refTxn.getStatus());

        // Disallow refunding an already refunded payment
        boolean doubleRefund = paymentDao.refund(id, "Attempt second refund", "manager");
        assertFalse(doubleRefund);
    }

    @Test
    @DisplayName("Prevent double checkout payment on an already settled ticket")
    void testDoublePaymentBlocked() {
        com.smartparking.model.parking.ParkingLot lot = new com.smartparking.model.parking.ParkingLot("LOT-001", "Test Lot", "UP");
        com.smartparking.model.parking.ParkingFloor f1 = new com.smartparking.model.parking.ParkingFloor(1, "L1");
        f1.addSlot(new com.smartparking.model.slot.RegularSlot("A25", 1, "A25", 40.0));
        lot.addFloor(f1);
        ParkingService service = ParkingService.createDefault(lot);

        com.smartparking.model.ticket.ParkingTicket ticket = service.processVehicleEntry(new com.smartparking.model.vehicle.Car("UP32DP0001"));
        assertNotNull(ticket);

        // First checkout payment succeeds
        ParkingService.CheckoutResult result = service.processVehicleCheckout(ticket.getTicketNumber(), PaymentMethodType.CASH);
        assertNotNull(result);
        assertEquals(com.smartparking.model.enums.TicketStatus.EXITED, result.ticket().getStatus());

        // Second checkout payment attempt on same ticket is blocked with IllegalStateException
        assertThrows(IllegalStateException.class, () ->
                service.processVehicleCheckout(ticket.getTicketNumber(), PaymentMethodType.CASH)
        );
    }

    @Test
    @DisplayName("Reject refund without a valid mandatory reason")
    void testMandatoryRefundReason() {
        assertThrows(IllegalArgumentException.class, () -> paymentDao.refund(1, "", "admin"));
        assertThrows(IllegalArgumentException.class, () -> paymentDao.refund(1, "bad", "admin"));
    }
}
