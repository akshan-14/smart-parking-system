package com.smartparking.service;

import com.smartparking.support.TestDatabase;
import com.smartparking.model.enums.SlotType;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.slot.*;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.vehicle.*;
import com.smartparking.repository.DatabaseInitializer;
import com.smartparking.repository.ParkingSlotDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Slot Allocation Strict Suitability Tests")
class SlotAllocationTest {

    private static ParkingLot lot;
    private static ParkingService parkingService;

    @BeforeAll
    static void setUp() {
        TestDatabase.useFresh("test_slot_allocation");
        DatabaseInitializer.initializeDatabase();

        lot = new ParkingLot("LOT-TEST", "Test Lot", "Uttar Pradesh");
        ParkingSlotDao slotDao = new ParkingSlotDao();

        // Floor 1: Compact bays only
        ParkingFloor f1 = new ParkingFloor(1, "Floor 1 (Compact)");
        CompactSlot cmp1 = new CompactSlot("F1-CMP1", 1, "01");
        CompactSlot cmp2 = new CompactSlot("F1-CMP2", 1, "02");
        f1.addSlot(cmp1);
        f1.addSlot(cmp2);
        slotDao.save(cmp1);
        slotDao.save(cmp2);
        lot.addFloor(f1);

        // Floor 2: Regular & Electric bays
        ParkingFloor f2 = new ParkingFloor(2, "Floor 2 (Standard)");
        RegularSlot reg1 = new RegularSlot("F2-REG1", 2, "01");
        ElectricSlot ev1 = new ElectricSlot("F2-EV1", 2, "02");
        AccessibleSlot div1 = new AccessibleSlot("F2-DIV1", 2, "03");
        f2.addSlot(reg1);
        f2.addSlot(ev1);
        f2.addSlot(div1);
        slotDao.save(reg1);
        slotDao.save(ev1);
        slotDao.save(div1);
        lot.addFloor(f2);

        // Floor 3: Heavy Commercial bays
        ParkingFloor f3 = new ParkingFloor(3, "Floor 3 (Heavy)");
        LargeSlot hvy1 = new LargeSlot("F3-HVY1", 3, "01");
        f3.addSlot(hvy1);
        slotDao.save(hvy1);
        lot.addFloor(f3);

        parkingService = ParkingService.createDefault(lot);
    }

    @Test
    @DisplayName("Heavy Commercial Vehicle (HCV) must never land on compact or regular floor, only Large bays")
    void testHcvLandsOnHeavyFloorOnly() {
        Hcv truck = new Hcv("UP14TR9999");
        ParkingTicket ticket = parkingService.processVehicleEntry(truck);
        assertEquals("F3-HVY1", ticket.getSlotId(), "HCV must be allocated to Floor 3 Large bay");
        assertEquals(3, ticket.getFloorNumber());
    }

    @Test
    @DisplayName("Motorcycle must strictly land on Compact bay")
    void testMotorcycleLandsOnCompact() {
        Motorcycle bike = new Motorcycle("UP32BK8888");
        ParkingTicket ticket = parkingService.processVehicleEntry(bike);
        assertEquals("F1-CMP1", ticket.getSlotId(), "Motorcycle must land on Floor 1 Compact bay");
    }

    @Test
    @DisplayName("EV requesting charge must land on Electric bay")
    void testEvRequestingChargeLandsOnElectric() {
        ElectricCar ev = new ElectricCar("UP32EV7777", "Blue", "Tata", 60.0, 30.0, true);
        ParkingTicket ticket = parkingService.processVehicleEntry(ev);
        assertEquals("F2-EV1", ticket.getSlotId(), "EV requesting charging must land on Electric slot");
    }

    @Test
    @DisplayName("Divyang permit holder vehicle must land on Accessible bay")
    void testDivyangPermitLandsOnAccessible() {
        Car pwdCar = new Car("UP32PW6666", "White", "Maruti", true);
        ParkingTicket ticket = parkingService.processVehicleEntry(pwdCar);
        assertEquals("F2-DIV1", ticket.getSlotId(), "Divyang permit vehicle must land on Accessible slot");
    }

    @Test
    @DisplayName("When all suitable bays for vehicle type are full, unsuitable fallback must be blocked with exact message")
    void testUnsuitableFallbackBlocked() {
        // Try to park a second HCV when only 1 Large bay exists
        Hcv secondTruck = new Hcv("UP14TR0002");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                parkingService.processVehicleEntry(secondTruck)
        );
        assertEquals("No suitable slot available for this vehicle type", ex.getMessage(),
                "Must display exact user-specified message when no suitable slot is available");
    }
}
