package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.PaymentMethodType;
import com.smartparking.model.enums.TicketStatus;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.slot.ElectricSlot;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.vehicle.Car;
import com.smartparking.model.vehicle.ElectricCar;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParkingService Integration Tests")
class ParkingServiceTest {

    private static ParkingService parkingService;
    private static ParkingLot parkingLot;

    @BeforeAll
    static void setUp() {
        // Use an isolated test database
        String testDb = "jdbc:sqlite:test_parking_system.db";
        new File("test_parking_system.db").delete();
        DatabaseConnectionManager.setDatabaseUrl(testDb);

        DatabaseInitializer.initializeDatabase();

        parkingLot = new ParkingLot("TEST-LOT", "Test Facility");
        ParkingSlotDao slotDao = new ParkingSlotDao();
        List<ParkingSlot> slots = slotDao.findAll();

        ParkingFloor f1 = new ParkingFloor(1);
        ParkingFloor f2 = new ParkingFloor(2);
        ParkingFloor f3 = new ParkingFloor(3);

        for (ParkingSlot s : slots) {
            switch (s.getFloorNumber()) {
                case 1 -> f1.addSlot(s);
                case 2 -> f2.addSlot(s);
                case 3 -> f3.addSlot(s);
            }
        }
        parkingLot.addFloor(f1);
        parkingLot.addFloor(f2);
        parkingLot.addFloor(f3);

        parkingService = ParkingService.createDefault(parkingLot);
    }

    @Test
    @DisplayName("Should successfully execute vehicle entry and exit workflow")
    void testVehicleEntryAndExit() {
        Car car = new Car("UP32TC0001");
        ParkingTicket ticket = parkingService.processVehicleEntry(car);

        assertNotNull(ticket);
        assertNotNull(ticket.getTicketNumber());
        assertEquals("UP32TC0001", ticket.getLicensePlate());
        assertEquals(TicketStatus.ACTIVE, ticket.getStatus());

        // Vehicle lookup by plate
        Optional<ParkingTicket> found = parkingService.searchVehicle("UP32TC0001");
        assertTrue(found.isPresent());
        assertEquals(ticket.getTicketNumber(), found.get().getTicketNumber());

        // Exit and payment
        ParkingTicket exitTicket = parkingService.processVehicleExit(ticket.getTicketNumber(), PaymentMethodType.UPI);
        assertEquals(TicketStatus.EXITED, exitTicket.getStatus());
        assertNotNull(exitTicket.getExitTime());

        // Slot must be available again
        Optional<ParkingSlot> slot = parkingService.findSlotInLot(ticket.getSlotId());
        assertTrue(slot.isPresent());
        assertTrue(slot.get().isAvailable());
    }

    @Test
    @DisplayName("Should apply EV charging fee when electric car requests charging")
    void testElectricCarCharging() {
        ElectricCar ev = new ElectricCar("UP16EV0002", "White", "Tesla", 75.0, 20.0, true);
        ParkingTicket ticket = parkingService.processVehicleEntry(ev);

        // Check if allocated an Electric slot
        Optional<ParkingSlot> slotOpt = parkingService.findSlotInLot(ticket.getSlotId());
        assertTrue(slotOpt.isPresent());
        assertInstanceOf(ElectricSlot.class, slotOpt.get());

        // Process exit
        ParkingTicket exitTicket = parkingService.processVehicleExit(ticket.getTicketNumber(), PaymentMethodType.WALLET);
        assertTrue(exitTicket.getEvChargingFee() > 0.0);
    }

    @Test
    @DisplayName("Should assess penalty on lost ticket exit")
    void testLostTicketExit() {
        Car visitor = new Car("UP32LT0003");
        parkingService.processVehicleEntry(visitor);

        ParkingTicket exitTicket = parkingService.processLostTicketExit("UP32LT0003", PaymentMethodType.CASH);
        assertEquals(TicketStatus.EXITED, exitTicket.getStatus());
        assertTrue(exitTicket.getPenaltyFee() >= 250.0);
        assertTrue(exitTicket.getTotalAmount() >= 250.0);
    }

    @Test
    @DisplayName("Should block duplicate entry when vehicle is already parked")
    void testBlockDuplicateEntry() {
        Car car = new Car("UP32DP9999");
        parkingService.processVehicleEntry(car);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            parkingService.processVehicleEntry(car);
        });
        assertEquals("This vehicle is already parked in the facility.", ex.getMessage());
    }
}
