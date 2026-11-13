package com.smartparking.ui;

import com.smartparking.model.enums.*;
import com.smartparking.model.membership.MembershipPass;
import com.smartparking.model.parking.ParkingFloor;
import com.smartparking.model.parking.ParkingLot;
import com.smartparking.model.reservation.Reservation;
import com.smartparking.model.slot.ParkingSlot;
import com.smartparking.model.ticket.ParkingTicket;
import com.smartparking.model.user.User;
import com.smartparking.model.vehicle.*;
import com.smartparking.patterns.factory.VehicleFactory;
import com.smartparking.patterns.strategy.allocation.BestFitAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.NearestFirstAllocationStrategy;
import com.smartparking.patterns.strategy.allocation.RandomAllocationStrategy;
import com.smartparking.patterns.strategy.pricing.PeakHourPricingStrategy;
import com.smartparking.repository.*;
import com.smartparking.service.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Console Presentation Controller for the Smart Parking Management System.
 * Provides role-based workflows and an automated end-to-end demonstration.
 */
public class ConsoleApp {
    private ParkingLot parkingLot;
    private ParkingService parkingService;
    private AuthService authService;
    private ReservationService reservationService;
    private MembershipService membershipService;
    private ReportService reportService;
    private final Scanner scanner = new Scanner(System.in);

    public void start(String[] args) {
        System.out.println("==================================================================");
        System.out.println("       SMART PARKING MANAGEMENT SYSTEM (Java 17 / SQLite)         ");
        System.out.println("==================================================================");

        // 1. Initialize SQLite Database & Seeds
        System.out.println("[INIT] Initializing SQLite database schema and seed data...");
        DatabaseInitializer.initializeDatabase();

        // 2. Load ParkingLot Domain Graph from SQLite
        loadParkingLotFromDatabase();

        // 3. Initialize Services
        initializeServices();

        System.out.println("[INIT] System successfully initialized.");
        System.out.println("Total Floors: " + parkingLot.getFloors().size() + " | Total Slots: " + parkingLot.getTotalCapacity());

        if (args != null && args.length > 0 && "--demo".equalsIgnoreCase(args[0])) {
            runAutomatedDemonstration();
            return;
        }

        // Show main menu
        mainMenuLoop();
    }

    private void loadParkingLotFromDatabase() {
        this.parkingLot = new ParkingLot("LOT-001", "Grand Tech Park Garage", "100 Innovation Boulevard");
        ParkingSlotDao slotDao = new ParkingSlotDao();
        List<ParkingSlot> allSlots = slotDao.findAll();

        ParkingFloor f1 = new ParkingFloor(1, "Ground Floor - Compact & Accessible");
        ParkingFloor f2 = new ParkingFloor(2, "Level 2 - Standard & EV Station");
        ParkingFloor f3 = new ParkingFloor(3, "Level 3 - Heavy & General");

        for (ParkingSlot s : allSlots) {
            switch (s.getFloorNumber()) {
                case 1 -> f1.addSlot(s);
                case 2 -> f2.addSlot(s);
                case 3 -> f3.addSlot(s);
            }
        }

        parkingLot.addFloor(f1);
        parkingLot.addFloor(f2);
        parkingLot.addFloor(f3);
    }

    private void initializeServices() {
        this.parkingService = ParkingService.createDefault(parkingLot);
        this.authService = new AuthService();
        this.reservationService = new ReservationService();
        this.membershipService = new MembershipService();
        this.reportService = new ReportService(parkingLot);
    }

    private void mainMenuLoop() {
        while (true) {
            System.out.println("\n---------------- MAIN MENU ----------------");
            System.out.println("1. Run Complete Automated End-to-End Simulation (Recommended)");
            System.out.println("2. Vehicle Entry (Automatic Slot Allocation & Ticket Issuance)");
            System.out.println("3. Vehicle Exit & Payment (Dynamic Pricing & Checkout)");
            System.out.println("4. Search Vehicle by Registration Number");
            System.out.println("5. Advance Parking Slot Reservation");
            System.out.println("6. Purchase / Verify Monthly Membership Pass");
            System.out.println("7. Admin Dashboard & Facility Utilization Report");
            System.out.println("8. Role-Based Login (Admin / Attendant / Customer)");
            System.out.println("9. Exit Application");
            System.out.print("Please enter your choice (1-9): ");

            String input = scanner.hasNextLine() ? scanner.nextLine().trim() : "9";
            switch (input) {
                case "1" -> runAutomatedDemonstration();
                case "2" -> handleVehicleEntry();
                case "3" -> handleVehicleExit();
                case "4" -> handleVehicleSearch();
                case "5" -> handleReservation();
                case "6" -> handleMembership();
                case "7" -> showAdminReport();
                case "8" -> handleLogin();
                case "9" -> {
                    System.out.println("\nShutting down Smart Parking System. Have a great day!");
                    return;
                }
                default -> System.out.println("Invalid selection. Please choose an option from 1 to 9.");
            }
        }
    }

    /**
     * Complete, fully-automated simulation exercising all 18 requirements and 5 design patterns.
     */
    public void runAutomatedDemonstration() {
        System.out.println("\n==================================================================");
        System.out.println("     EXECUTING AUTOMATED OBJECT-ORIENTED FEATURE DEMONSTRATION    ");
        System.out.println("==================================================================");

        // 1. Vehicles Creation (Factory Pattern & Polymorphism)
        System.out.println("\n>>> [1] CREATING MULTIPLE VEHICLE TYPES (Factory Pattern & Inheritance) <<<");
        String runId = String.format("%04d", (int)(Math.random() * 9000 + 1000));
        Vehicle bike = VehicleFactory.createVehicle(VehicleType.MOTORCYCLE, "UP32BK" + runId, "Black", "Yamaha");
        Vehicle sedan = VehicleFactory.createVehicle(VehicleType.CAR, "UP16CD" + runId, "White", "Honda");
        ElectricCar ev = new ElectricCar("UP32EV" + runId, "Blue", "Tata Nexon EV", 75.0, 30.0, true);
        Vehicle truck = VehicleFactory.createVehicle(VehicleType.HCV, "UP14TR" + runId, "Yellow", "Volvo");

        System.out.println("Created: " + bike);
        System.out.println("Created: " + sedan);
        System.out.println("Created: " + ev);
        System.out.println("Created: " + truck);

        // 2. Dynamic Slot Allocation (Strategy Pattern: NearestFirst & BestFit)
        System.out.println("\n>>> [2] VEHICLE ENTRY & AUTOMATIC SLOT ALLOCATION (Strategy Pattern) <<<");
        parkingService.setAllocationStrategy(new BestFitAllocationStrategy());
        System.out.println("Active Allocation Strategy: BestFitAllocationStrategy");

        ParkingTicket tktBike = parkingService.processVehicleEntry(bike);
        System.out.println("Issued Bike Ticket: " + tktBike);

        ParkingTicket tktSedan = parkingService.processVehicleEntry(sedan);
        System.out.println("Issued Car Ticket: " + tktSedan);

        ParkingTicket tktEv = parkingService.processVehicleEntry(ev);
        System.out.println("Issued EV Ticket: " + tktEv);

        ParkingTicket tktTruck = parkingService.processVehicleEntry(truck);
        System.out.println("Issued Truck Ticket: " + tktTruck);

        // 3. Vehicle Search
        System.out.println("\n>>> [3] SEARCH VEHICLE BY REGISTRATION NUMBER <<<");
        Optional<ParkingTicket> found = parkingService.searchVehicle("UP32EV" + runId);
        found.ifPresent(ticket -> System.out.println("Found Vehicle: Plate " + ticket.getLicensePlate() +
                " located in Slot " + ticket.getSlotId() + " (Floor " + ticket.getFloorNumber() + ")"));

        // 4. Monthly Membership Pass & Discount
        System.out.println("\n>>> [4] MONTHLY MEMBERSHIP PASS PURCHASE & DISCOUNT <<<");
        MembershipPass pass = membershipService.purchaseMembership(3, "UP16CD" + runId, MembershipType.GOLD);
        System.out.println("Purchased: " + pass);

        // 5. Dynamic Pricing & Surge Multiplier
        System.out.println("\n>>> [5] DYNAMIC PRICING (Strategy Pattern: Peak-Hour Surge) <<<");
        parkingService.getPricingService().setActiveStrategy(new PeakHourPricingStrategy());
        System.out.println("Active Pricing Strategy: PeakHourPricingStrategy");

        // 6. Payments with Different Channels & Builder Pattern Receipt Generation
        System.out.println("\n>>> [6] PAYMENT & VEHICLE EXIT (Strategy & Builder Patterns) <<<");
        System.out.println("\n-- Exiting Bike (Paying via UPI) --");
        ParkingTicket exitBike = parkingService.processVehicleExit(tktBike.getTicketNumber(), PaymentMethodType.UPI);
        System.out.println("Exit Confirmed: " + exitBike);

        System.out.println("\n-- Exiting Car with Membership Discount (Paying via CARD) --");
        ParkingTicket exitSedan = parkingService.processVehicleExit(tktSedan.getTicketNumber(), PaymentMethodType.CARD);
        System.out.println(parkingService.generateReceipt(exitSedan));

        System.out.println("\n-- Exiting EV with Charging Fee (Paying via WALLET) --");
        ParkingTicket exitEv = parkingService.processVehicleExit(tktEv.getTicketNumber(), PaymentMethodType.WALLET);
        System.out.println(parkingService.generateReceipt(exitEv));

        System.out.println("\n-- Exiting Heavy Truck (Paying via CASH) --");
        ParkingTicket exitTruck = parkingService.processVehicleExit(tktTruck.getTicketNumber(), PaymentMethodType.CASH);
        System.out.println(parkingService.generateReceipt(exitTruck));

        // 7. Allocation Strategy Switch & Lost Ticket Penalty Simulation
        System.out.println("\n>>> [7] ALLOCATION STRATEGY SWITCH & LOST TICKET PENALTY <<<");
        parkingService.setAllocationStrategy(new RandomAllocationStrategy());
        System.out.println("Switched Strategy to: RandomAllocationStrategy");

        Vehicle visitorCar = VehicleFactory.createVehicle(VehicleType.CAR, "UP70LS" + runId);
        ParkingTicket visitorTkt = parkingService.processVehicleEntry(visitorCar);
        System.out.println("Allocated slot for visitor car: " + visitorTkt.getSlotId());
        System.out.println("Customer lost ticket! Processing lost ticket exit...");
        ParkingTicket lostTktExit = parkingService.processLostTicketExit("UP70LS" + runId, PaymentMethodType.CASH);
        System.out.println(parkingService.generateReceipt(lostTktExit));

        // 8. Parking Advance Reservation & Auto-Purge
        System.out.println("\n>>> [8] ADVANCE PARKING SLOT RESERVATION & EXPIRY PURGE <<<");
        ParkingSlot availSlot = parkingLot.getFloors().stream()
                .flatMap(f -> f.getAvailableSlots().stream())
                .findFirst()
                .orElse(parkingLot.getFloors().get(0).getAllSlots().get(0));
        String targetSlotId = availSlot.getSlotId();
        if (!availSlot.isAvailable()) {
            availSlot.vacate();
            new ParkingSlotDao().updateStatus(targetSlotId, "AVAILABLE", null);
        }

        Reservation res = reservationService.makeReservation(3, targetSlotId, "UP78RS" + runId, VehicleType.CAR,
                LocalDateTime.now().minusMinutes(45), LocalDateTime.now().plusHours(2));
        System.out.println("Created Overdue Reservation: " + res);
        int purged = reservationService.purgeExpiredReservations(15);
        System.out.println("Reservation No-Show Purge Check Completed. Purged: " + purged);

        // Test active advance reservation
        Reservation activeRes = reservationService.makeReservation(3, targetSlotId, "UP80RS" + runId, VehicleType.CAR,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(4));
        System.out.println("Created Active Reservation: " + activeRes);
        reservationService.cancelReservation(activeRes.getReservationCode());
        System.out.println("Cleaned up Active Reservation: " + activeRes.getReservationCode() + " (Slot released)");

        // 9. Admin Dashboard & Reports
        System.out.println("\n>>> [9] ADMIN MANAGEMENT DASHBOARD & FACILITY AUDIT REPORT <<<");
        System.out.println(reportService.generateFullReport());

        System.out.println(">>> AUTOMATED DEMONSTRATION COMPLETE SUCCESSFUL <<<");
    }

    private void handleVehicleEntry() {
        System.out.println("\n--- Vehicle Entry ---");
        System.out.print("Enter License Plate Number: ");
        String plate = scanner.nextLine().trim();
        if (plate.isEmpty()) {
            System.out.println("License plate cannot be empty.");
            return;
        }

        System.out.println("Select Vehicle Type:");
        System.out.println("1. Bike");
        System.out.println("2. Car");
        System.out.println("3. Electric Car");
        System.out.println("4. Heavy Truck");
        System.out.print("Choice: ");
        String ch = scanner.nextLine().trim();

        Vehicle vehicle = switch (ch) {
            case "1" -> new Bike(plate);
            case "3" -> new ElectricCar(plate, 75.0);
            case "4" -> new Truck(plate);
            default -> new Car(plate);
        };

        try {
            ParkingTicket ticket = parkingService.processVehicleEntry(vehicle);
            System.out.println("\n[SUCCESS] Vehicle parked successfully!");
            System.out.println("Ticket Number : " + ticket.getTicketNumber());
            System.out.println("Allocated Slot: " + ticket.getSlotId() + " (Floor " + ticket.getFloorNumber() + ")");
            System.out.println("Entry Time    : " + ticket.getEntryTime());
        } catch (Exception e) {
            System.out.println("[FAILED] " + e.getMessage());
        }
    }

    private void handleVehicleExit() {
        System.out.println("\n--- Vehicle Exit & Payment ---");
        System.out.print("Enter Ticket Number: ");
        String ticketNum = scanner.nextLine().trim();

        System.out.println("Select Payment Method:");
        System.out.println("1. Cash");
        System.out.println("2. Credit / Debit Card");
        System.out.println("3. UPI");
        System.out.println("4. Digital Wallet");
        System.out.print("Choice: ");
        String pCh = scanner.nextLine().trim();

        PaymentMethodType method = switch (pCh) {
            case "1" -> PaymentMethodType.CASH;
            case "2" -> PaymentMethodType.CARD;
            case "4" -> PaymentMethodType.WALLET;
            default -> PaymentMethodType.UPI;
        };

        try {
            ParkingTicket ticket = parkingService.processVehicleExit(ticketNum, method);
            System.out.println("\n[SUCCESS] Checkout completed!");
            System.out.println(parkingService.generateReceipt(ticket));
        } catch (Exception e) {
            System.out.println("[FAILED] " + e.getMessage());
        }
    }

    private void handleVehicleSearch() {
        System.out.print("\nEnter License Plate to search: ");
        String plate = scanner.nextLine().trim();
        Optional<ParkingTicket> tktOpt = parkingService.searchVehicle(plate);
        if (tktOpt.isPresent()) {
            ParkingTicket t = tktOpt.get();
            System.out.println("\n[FOUND ACTIVE RECORD]");
            System.out.println("Ticket Number : " + t.getTicketNumber());
            System.out.println("Plate         : " + t.getLicensePlate());
            System.out.println("Slot Location : " + t.getSlotId() + " (Floor " + t.getFloorNumber() + ")");
            System.out.println("Parked Since  : " + t.getEntryTime());
            System.out.println("Status        : " + t.getStatus());
        } else {
            System.out.println("[NOT FOUND] No active parked vehicle found for plate: " + plate);
        }
    }

    private void handleReservation() {
        System.out.println("\n--- Advance Slot Reservation ---");
        System.out.print("Enter Slot ID (e.g. F1-S01, F2-S01): ");
        String slotId = scanner.nextLine().trim();
        System.out.print("Enter License Plate: ");
        String plate = scanner.nextLine().trim();

        try {
            Reservation res = reservationService.makeReservation(3, slotId, plate, VehicleType.CAR,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(3));
            System.out.println("\n[SUCCESS] Reservation Confirmed!");
            System.out.println("Reservation Code: " + res.getReservationCode());
            System.out.println("Slot Reserved   : " + res.getSlotId());
        } catch (Exception e) {
            System.out.println("[FAILED] " + e.getMessage());
        }
    }

    private void handleMembership() {
        System.out.println("\n--- Monthly Membership Passes ---");
        System.out.println("1. Purchase New Pass");
        System.out.println("2. Verify Existing Pass by License Plate");
        System.out.print("Choice: ");
        String ch = scanner.nextLine().trim();

        if ("1".equals(ch)) {
            System.out.print("Enter License Plate: ");
            String plate = scanner.nextLine().trim();
            System.out.println("Select Tier: 1. Silver (10%), 2. Gold (20%), 3. Platinum (35%)");
            System.out.print("Choice: ");
            String tCh = scanner.nextLine().trim();
            MembershipType type = switch (tCh) {
                case "1" -> MembershipType.SILVER;
                case "3" -> MembershipType.PLATINUM;
                default -> MembershipType.GOLD;
            };
            MembershipPass pass = membershipService.purchaseMembership(3, plate, type);
            System.out.println("\n[SUCCESS] Pass issued successfully: " + pass);
        } else {
            System.out.print("Enter License Plate: ");
            String plate = scanner.nextLine().trim();
            Optional<MembershipPass> pass = membershipService.getActiveMembership(plate);
            if (pass.isPresent() && pass.get().isValid()) {
                System.out.println("[VALID] " + pass.get());
            } else {
                System.out.println("[NO ACTIVE MEMBERSHIP] Vehicle has no active pass.");
            }
        }
    }

    private void showAdminReport() {
        System.out.println("\n" + reportService.generateFullReport());
    }

    private void handleLogin() {
        System.out.println("\n--- Role-Based Login ---");
        System.out.print("Username (e.g., admin, attendant1, customer1): ");
        String username = scanner.nextLine().trim();
        System.out.print("Password (admin123, attendant123, customer123): ");
        String password = scanner.nextLine().trim();

        Optional<User> userOpt = authService.authenticate(username, password);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("\n[LOGIN SUCCESSFUL] Welcome, " + user.getFullName() + "!");
            System.out.println("Role: " + user.getRole().getRoleDescription());
        } else {
            System.out.println("\n[LOGIN FAILED] Invalid credentials.");
        }
    }
}
