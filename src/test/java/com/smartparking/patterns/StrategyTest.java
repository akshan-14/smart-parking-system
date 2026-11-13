package com.smartparking.patterns;

import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.slot.*;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.vehicle.Bike;
import com.smartparking.model.vehicle.Car;
import com.smartparking.model.vehicle.Vehicle;
import com.smartparking.patterns.strategy.allocation.BestFitAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.NearestFirstAllocationStrategy;
import com.smartparking.patterns.strategy.payment.*;
import com.smartparking.patterns.strategy.pricing.PeakHourPricingStrategy;
import com.smartparking.patterns.strategy.pricing.StandardHourlyPricingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Strategy Pattern Tests (Allocation, Pricing, Payment)")
class StrategyTest {

    @Test
    @DisplayName("Should test NearestFirst and BestFit Allocation Strategies")
    void testAllocationStrategies() {
        ParkingFloor f1 = new ParkingFloor(1);
        f1.addSlot(new RegularSlot("F1-REG1", 1, "01"));
        f1.addSlot(new RegularSlot("F1-REG2", 1, "02"));
        f1.addSlot(new CompactSlot("F1-CMP", 1, "03"));

        List<ParkingFloor> floors = List.of(f1);
        Vehicle car = new Car("UP32AB1234");
        Vehicle bike = new Bike("UP32BK1101");

        // NearestFirst picks the first compatible regular slot for car in iteration order (F1-REG1)
        NearestFirstAllocationStrategy nearestStrategy = new NearestFirstAllocationStrategy();
        Optional<ParkingSlot> nearestSlot = nearestStrategy.allocateSlot(floors, car);
        assertTrue(nearestSlot.isPresent());
        assertEquals("F1-REG1", nearestSlot.get().getSlotId());

        // BestFit specifically prioritizes compact slot for bike
        BestFitAllocationStrategy bestFitStrategy = new BestFitAllocationStrategy();
        Optional<ParkingSlot> bestFitSlot = bestFitStrategy.allocateSlot(floors, bike);
        assertTrue(bestFitSlot.isPresent());
        assertEquals("F1-CMP", bestFitSlot.get().getSlotId());
    }

    @Test
    @DisplayName("Should test Dynamic Pricing Strategy and grace period")
    void testPricingStrategy() {
        StandardHourlyPricingStrategy standardStrategy = new StandardHourlyPricingStrategy();

        // Ticket within 15-min grace period -> ₹0.00
        ParkingTicket graceTicket = new ParkingTicket("UP32AB1234", VehicleType.CAR, "A1", 1,
                LocalDateTime.now().minusMinutes(10));
        assertEquals(0.0, standardStrategy.calculatePrice(graceTicket, 40.0, 50.0));

        // Ticket 2.5 hours stay (rounded up to 3 hours) -> 3 * 40.0 = 120.0
        ParkingTicket multiHourTicket = new ParkingTicket("UP32AB1234", VehicleType.CAR, "A1", 1,
                LocalDateTime.now().minusMinutes(150));
        assertEquals(120.0, standardStrategy.calculatePrice(multiHourTicket, 40.0, 50.0));

        // Peak surge pricing at 85% occupancy (1.5x surge) -> 120 * 1.5 = 180.0
        PeakHourPricingStrategy peakStrategy = new PeakHourPricingStrategy();
        assertEquals(180.0, peakStrategy.calculatePrice(multiHourTicket, 40.0, 85.0));

        // Peak surge pricing at 95% occupancy (2.0x surge) -> 120 * 2.0 = 240.0
        assertEquals(240.0, peakStrategy.calculatePrice(multiHourTicket, 40.0, 95.0));
    }

    @Test
    @DisplayName("Should verify all simulated payment strategies")
    void testPaymentStrategies() {
        // Cash payment
        CashPaymentStrategy cash = new CashPaymentStrategy(100.0);
        assertTrue(cash.processPayment(80.0, "REF-1"));
        assertEquals(20.0, cash.getChange(80.0));
        assertFalse(cash.processPayment(120.0, "REF-2")); // Insufficient tender

        // Card payment
        CardPaymentStrategy card = new CardPaymentStrategy("4111222233334444", "John Doe", "12/28");
        assertTrue(card.processPayment(50.0, "REF-3"));
        assertEquals("****-****-****-4444", card.getMaskedCardNumber());

        // UPI payment
        UpiPaymentStrategy upi = new UpiPaymentStrategy("user@okhdfcbank");
        assertTrue(upi.processPayment(50.0, "REF-4"));
        UpiPaymentStrategy invalidUpi = new UpiPaymentStrategy("invalid-vpa");
        assertFalse(invalidUpi.processPayment(50.0, "REF-5"));

        // Wallet payment
        WalletPaymentStrategy wallet = new WalletPaymentStrategy("Paytm", 200.0);
        assertTrue(wallet.processPayment(150.0, "REF-6"));
        assertEquals(50.0, wallet.getWalletBalance());
        assertFalse(wallet.processPayment(100.0, "REF-7")); // Exceeds balance
    }
}
