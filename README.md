# Smart Parking Management System (OOM Project)

> **College Project Submission for Object Oriented Methodology (OOM)**  
> Built with **Java 17**, **Maven**, **SQLite JDBC**, **JUnit 5**, and **PlantUML**.

---

## 📌 Project Overview

The **Smart Parking Management System** is a robust, modular, and enterprise-grade software application designed to manage multi-floor parking facilities. The architecture is grounded in **Object-Oriented Methodology (OOM)** and strictly complies with the **SOLID** design principles.

---

## 🌟 Feature Checklist & Requirements Matrix

| Requirement | Implementation Details | Target Classes |
| :--- | :--- | :--- |
| **1. Multi-Floor Parking** | Floors containing slots categorized by type; real-time occupancy tracking. | `ParkingLot`, `ParkingFloor` |
| **2. Parking Slot Types** | Compact, Regular, Large, Electric (EV), and Handicapped slots with accessibility permit enforcement. | `CompactSlot`, `RegularSlot`, `LargeSlot`, `ElectricSlot`, `HandicappedSlot` |
| **3. Vehicle Types** | Bikes, Standard Cars, Heavy Trucks, and Electric Cars with accessibility permit flags. | `Bike`, `Car`, `Truck`, `ElectricCar` |
| **4. Automatic Slot Allocation** | Dynamic assignment strategy (Nearest-First, Best-Fit, or Random allocation matching vehicle type). | `SlotAllocationStrategy`, `NearestFirstAllocationStrategy`, `BestFitAllocationStrategy`, `RandomAllocationStrategy` |
| **5. Entry & Exit Workflow** | Synchronized, thread-safe gate arrival, ticket issuance, atomic checkout transaction, and departure. | `ParkingService` |
| **6. Ticket Generation** | Fluent Builder Pattern generating unique tokenized tickets with entry timestamp, slot details, and barcode tokens. | `TicketBuilder`, `ParkingTicket` |
| **7. Dynamic Pricing** | Hourly rate calculation with 15-minute grace period, weekend discount, and real-time occupancy surge multipliers. | `PricingStrategy`, `StandardHourlyPricingStrategy`, `PeakHourPricingStrategy`, `WeekendPricingStrategy` |
| **8. Multi-Channel Payments** | Simulated payments across Cash, Credit/Debit Card, UPI, and Digital Wallet. | `PaymentStrategy`, `CashPaymentStrategy`, `CardPaymentStrategy`, `UpiPaymentStrategy`, `WalletPaymentStrategy` |
| **9. Parking Reservations** | Advance booking of designated slots with check-in validation and automated no-show expiry purging. | `Reservation`, `ReservationService`, `ReservationDao` |
| **10. EV Charging Fees** | Integrated charging fee computation based on slot hourly charging rates and vehicle request. | `EVChargingService`, `ElectricSlot`, `ElectricCar` |
| **11. Monthly Memberships** | Tiered passes (Silver 10%, Gold 20%, Platinum 35%) with automated fee discount deduction. | `MembershipPass`, `MembershipService`, `MembershipDao` |
| **12. Penalty Enforcement** | Automated assessment for lost tickets ($250 fine) and extended overstays. | `Penalty`, `PenaltyService`, `PenaltyType` |
| **13. Real-Time Notifications** | Observer notifications dispatched to LED entrance display boards, SMS gateways, and emails. | `ParkingEventManager`, `DisplayBoardListener`, `EmailNotificationListener`, `SmsNotificationListener` |
| **14. Admin Dashboard & Reports** | Occupancy rates, floor utilization breakdowns, and total financial audit summaries. | `ReportService` |
| **15. Role-Based Access Control** | Dedicated permissions for Administrators, Attendants, and Customers with 16-byte cryptographically salted SHA-256 password hashing. | `User`, `Admin`, `Attendant`, `Customer`, `AuthService`, `PasswordUtils` |
| **16. Itemized Receipts** | Builder Pattern constructing detailed financial and operational receipts upon checkout. | `ReceiptBuilder` |
| **17. Vehicle Search** | Rapid license plate lookup locating active parked slot or ticket history. | `ParkingService.searchVehicle()` |
| **18. Atomic Transactions & SQLite Persistence** | Multi-table atomic checkout transactions coordinated on a single Connection with rollback safety and enforced foreign keys. | `DatabaseConnectionManager`, `DatabaseInitializer`, `TicketDao`, `PaymentDao`, `ParkingSlotDao` |

---

## 📐 Object-Oriented Methodology (OOM) Demonstrations

### 1. Encapsulation
- All domain entity fields (`licensePlate`, `hourlyRate`, `status`, `passwordHash`, `salt`) are `private`.
- State transitions are strictly controlled through domain methods and state machine transitions rather than arbitrary external field mutations.

### 2. Abstraction
- Abstract classes `Vehicle`, `ParkingSlot`, and `User` define contracts without exposing concrete mechanics.
- Interfaces `SlotAllocationStrategy`, `PricingStrategy`, `PaymentStrategy`, `ParkingEventListener`, and `SlotState` decouple business services from concrete algorithms.

### 3. Inheritance
- `Vehicle` $\rightarrow$ `Bike`, `Car`, `Truck`, `ElectricCar`
- `ParkingSlot` $\rightarrow$ `CompactSlot`, `RegularSlot`, `LargeSlot`, `ElectricSlot`, `HandicappedSlot`
- `User` $\rightarrow$ `Admin`, `Attendant`, `Customer`

### 4. Polymorphism
- **Runtime Polymorphism**: `slot.canAccommodate(vehicle)`, `pricingStrategy.calculatePrice(...)`, `paymentStrategy.processPayment(...)`.
- **Compile-time Polymorphism**: Method overloading in `ParkingService.processVehicleEntry(...)`, `UserDao.save(...)`, `PaymentService.processTicketPayment(...)`.

### 5. Constructors & Constructor Chaining
- **Hierarchy Chaining via `super(...)`**: Subclasses pass common invariants up to base class constructors.
- **Overloaded Chaining via `this(...)`**: Alternative constructors cascade to canonical constructors with default rates, salts, or timestamps.

### 6. Object Relationships
- **Association**: `Attendant` processes `ParkingTicket`; `Customer` makes `Reservation`.
- **Aggregation**: `ParkingLot` aggregates `Attendant` instances (staff exist independently of the facility).
- **Composition**: `ParkingLot` owns `ParkingFloor`s; `ParkingFloor` owns `ParkingSlot`s; `ParkingTicket` owns `Penalty` records.

---

## 🎨 GoF Design Patterns Implemented

1. **Singleton Pattern**: `DatabaseConnectionManager` ensures exactly one thread-safe SQLite connection instance across the JVM with enforced foreign keys (`PRAGMA foreign_keys = ON;`).
2. **Strategy Pattern**:
   - Slot Allocation: `NearestFirstAllocationStrategy`, `BestFitAllocationStrategy`, `RandomAllocationStrategy`.
   - Dynamic Pricing: `StandardHourlyPricingStrategy`, `PeakHourPricingStrategy`, `WeekendPricingStrategy`.
   - Payment Methods: `CashPaymentStrategy`, `CardPaymentStrategy`, `UpiPaymentStrategy`, `WalletPaymentStrategy`.
3. **Factory Pattern**:
   - `VehicleFactory`: Instantiates concrete vehicles based on `VehicleType`.
   - `SlotFactory`: Instantiates concrete parking slots based on `SlotType`.
   - `PaymentStrategyFactory`: Resolves strategy handlers based on `PaymentMethodType`.
4. **Observer Pattern**:
   - Subject: `ParkingEventManager`.
   - Observers: `DisplayBoardListener` (updates real-time LED entry display), `EmailNotificationListener`, `SmsNotificationListener`.
5. **State Pattern**:
   - `ParkingSlot` delegates its state behavior to `SlotState`: `AvailableState`, `OccupiedState`, `ReservedState`, and `OutOfServiceState`.
6. **Builder Pattern**:
   - `TicketBuilder`: Fluent multi-attribute construction of `ParkingTicket` instances.
   - `ReceiptBuilder`: Fluent itemized assembly of customer exit receipts.

---

## 🚀 How to Run

### Requirements
- **Java 17+**
- **Maven 3.8+** on your `PATH` (`mvn -version` should work). `mvnw.cmd` is only a thin Windows shim that calls `mvn`.

### Running the Desktop GUI
```bash
mvn compile exec:java
```
Opens the login window. Demo accounts are listed below and are one click away on the login screen.

### Running Automated Demonstration (console)
Executes a complete simulated workflow demonstrating vehicle entry, allocation, EV charging, membership discount, surge pricing, penalties, checkout, and admin reports:
```bash
mvn compile exec:java -Dexec.args="--demo"
```

### Running Interactive Console Application
Launches the interactive text menu (also used automatically on headless machines):
```bash
mvn compile exec:java -Dexec.args="--cli"
```

### Running JUnit 5 Test Suite
```bash
mvn test
```

### Packaging Runnable JAR
```bash
mvn package
java -jar target/smart-parking-system-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

The app creates `smart_parking.db` (SQLite) in the working directory on first launch and seeds demo data.

---

## 📡 Simulated Features (College Viva Note)

All real-world external hardware integrations and banking gateways are clearly designated as **(Simulated)** in both the user interface and the codebase:

1. **Simulated UPI Payments**:
   - Generates dynamic, compliant `upi://pay?pa=parking@upi&pn=Grand+Tech+Park...` payment intent URLs and high-resolution ZXing QR codes.
   - Payment confirmation is simulated via user checkout confirmation.
2. **Simulated Card Payments**:
   - Simulates POS card reader terminals.
   - In strict compliance with **PCI-DSS guidelines**, the application never captures, stores, or logs 16-digit Primary Account Numbers (PAN) or 3-digit CVV codes. Only the last 4 digits and cardholder name are recorded.
3. **Simulated Digital Wallets**:
   - Supports popular Indian mobile wallets (Paytm, PhonePe, Amazon Pay).
   - Validates linked 10-digit mobile numbers and simulates instantaneous wallet debit.
4. **Simulated FASTag Electronic Toll Collection**:
   - Backed by SQLite table `fastag_accounts(tag_id, plate, balance, status)`.
   - Entry gate cameras auto-detect active RFID FASTag stickers by vehicle license plate.
   - Checkout gate automatically debits toll fee from linked FASTag account balance in a single atomic transaction.
   - Provides administrator management (top-up, block/unblock, RFID tag linking) with audit logging.
5. **Simulated SMS & Email Gateways**:
   - Formats and logs outbound SMS dispatch (e.g. entry ticket links) and email invoice notifications to application event streams.

---

## 🔐 Phase 6: Security, Role-Based Access Control (RBAC), and DPDP Compliance

### 1. Pre-Configured Demo Accounts

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `Admin@123` | Full access; forced to change password upon first login; audit viewer & retention admin. |
| **Attendant** | `attendant1` | `Attendant@123` | Operational access: Overview, Floor Map, Entry Gate, Checkout & Bill, Advance Booking, Vehicle Search, Alerts. |
| **Customer** | `customer1` | `Customer@123` | Self-service portal: linked vehicles, personal tickets, passes, data download & DPDP erasure. |

> **Password Security**: Passwords are cryptographically hashed using **BCrypt** (cost factor 12) via `jBCrypt`. Full card numbers and CVV codes are never stored or logged in accordance with PCI-DSS guidelines.

### 2. Role-Based Access Control (RBAC) Matrix

| Navigation Screen / Capability | Admin | Attendant | Customer |
| :--- | :---: | :---: | :---: |
| **Overview (Dashboard)** | ✅ | ✅ | ❌ |
| **Floor Visual Map** | ✅ | ✅ | ❌ |
| **Vehicle Entry Gate** | ✅ | ✅ | ❌ |
| **Checkout & Bill** | ✅ | ✅ | ❌ |
| **Advance Booking** | ✅ | ✅ | ✅ (Own) |
| **Monthly Passes** | ✅ | ❌ | ✅ (Own) |
| **Find Vehicle** | ✅ | ✅ | ✅ (Own) |
| **Operational Alerts (acknowledge / resolve)** | ✅ | ✅ | ❌ |
| **Audit & Reports** | ✅ | ❌ | ❌ |
| **Audit Log Viewer (Hash Verification)** | ✅ | ❌ | ❌ |
| **User & Staff Management** | ✅ | ❌ | ❌ |
| **System & Tariff Settings** | ✅ | ❌ | ❌ |
| **Privacy Notice (EN / HI)** | ✅ | ✅ | ✅ |
| **Customer Data Portal (DPDP)** | ❌ | ❌ | ✅ |
| **Database Backup & Retention (Admin)** | ✅ | ❌ | ❌ |

### 3. Digital Personal Data Protection (DPDP) Act 2023 Compliance

- **Statutory Consent**: User registration captures explicit consent stored in `consents` table with versioning.
- **Dual-Language Privacy Notice**: Available in English and Hindi (`PrivacyNoticePanel`), citing Data Protection Grievance Officer details.
- **Automated Data Retention**:
  - Closed tickets older than 365 days are anonymized (`UP00XX0000`).
  - Simulated ANPR license plate snapshots older than 30 days are purged.
  - Tax invoices are preserved in compliance with Section 36 of the CGST Act 2017 (72-month statutory retention).
- **Customer "My Data" Portal**: Customers can view linked vehicles, download personal parking history in CSV/JSON, or file a DPDP Right-to-Erasure request.

### 4. Database Concurrency, Cryptographic Audit Trail & Live Backup

- **Concurrency & WAL Mode**: SQLite runs in Write-Ahead Logging (`PRAGMA journal_mode = WAL;`) with 5000ms busy timeout and conditional atomic updates (`UPDATE slots SET status='OCCUPIED'... WHERE slot_id=? AND status='AVAILABLE'`), eliminating race conditions across multiple gates.
- **Tamper-Evident SHA-256 Hash Chaining**: Every operational action is recorded in `audit_log` with `prev_hash` chained to `row_hash = SHA-256(prev_hash + timestamp + user + action + entity + values + reason)`. Any external manual SQL alteration breaks chain continuity and is flagged immediately by the UI's "Verify Log Integrity" tool.
- **Live Online Backup (`VACUUM INTO`)**: Administrative backups run live without taking the database offline, retaining a 7-file rolling backup history in `./backups/`.

---

## 📊 UML Diagrams (PlantUML)

All diagrams are located under `docs/diagrams/`:
- `class_diagram.puml`: Full structural class hierarchy and OOP relationships.
- `use_case_diagram.puml`: Comprehensive use case diagram covering all 18 requirements and 4 actors (Customer, Attendant, Admin, Sensor System).
- `activity_diagram.puml`: Activity diagram modeling vehicle arrival, allocation, dynamic fee calculation, atomic checkout transaction, and departure.
- `sequence_entry_exit.puml`: Sequence diagram for vehicle check-in and checkout message workflows.
- `state_slot.puml`: State pattern statechart for parking slot lifecycle transitions.
