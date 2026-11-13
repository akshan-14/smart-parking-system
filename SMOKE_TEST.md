# Smart Parking Management System — Comprehensive Viva & Manual Smoke Test Guide

**System Specifications**:
- **Platform**: Java 17 (OpenJDK), Swing Desktop GUI, SQLite Database.
- **Target Localization**: Republic of India, Uttar Pradesh (UP RTO Series & Bharat Series).
- **Currency**: Indian Rupee (`₹`), Timezone: Indian Standard Time (`Asia/Kolkata`, UTC+5:30).
- **Data Integrity**: Single Source of Truth computed directly from `slots` table. Hardcoded metrics removed.
- **Design Patterns**: State Pattern, Strategy Pattern, Builder Pattern, Singleton Pattern, DAO Pattern, Observer Pattern.

---

## 1. Overview Dashboard Screen

### Steps to Test
1. Launch the application via `com.smartparking.Main`.
2. Inspect the 6 top metric cards (Total Capacity, Available Slots, Occupied Slots, Occupancy Rate, Today's Revenue, Active Reservations).
3. Check the **Vehicle Turnover Trend** line/bar chart and toggle the time-range buttons (`7 Days`, `30 Days`, `3 Months`, `1 Year`).
4. Check the **Vehicle Type Distribution** donut chart.
5. Review **Recent Parking Activity** table and **Live Operational Alerts**.
6. Click each of the 4 Quick Action buttons: `New Entry`, `Checkout`, `Reserve Slot`, `View Reports`.

### Expected Visual Appearance
- **Vector Icons**: High-DPI Java 2D vector icons appear crisply on every metric card (no broken `▯` boxes).
- **Accessible Colors**:
  - `7 Days` button is high-contrast deep blue (`#1d4ed8`) with pure white bold text (contrast $\ge 7:1$).
  - `New Entry` button has rich emerald green background with white car icon and white text.
  - `Checkout` button has crimson background with white ticket icon and white text.
  - `Reserve Slot` button has royal blue background with white calendar icon and white text.
  - `View Reports` button has deep violet background with white reports icon and white text.
- **Turnover Trend Chart**:
  - Legend displays clean labels `■ Entered` and `● Exited` (no raw hex codes).
  - Y-axis shows `Vehicles` and X-axis shows formatted dates with day names (`Mon 09 Nov`, `Tue 10 Nov`).
- **Donut Chart**:
  - Center displays total occupied count (e.g. `35 Parked`).
  - Legend items (Cars, Bikes, Trucks, EVs) strictly sum to the center count.

### Expected Behavior on Actions
- Clicking time-range buttons toggles active state and repaints trend metrics dynamically.
- Quick action buttons instantly switch sidebar navigation to the target screen without delay.

### College Viva Talking Points
- **Single Source of Truth**: Metrics are never hardcoded. Occupancy %, counts, and revenue are dynamically aggregated from the SQLite database.
- **Color-Blind Accessibility**: Metric indicators pair distinct colors with geometric symbols and text labels.

---

## 2. Floor Visual Map & Architectural Blueprint

### Steps to Test
1. Click **Floor Visual Map** on sidebar or press `Alt + 4`.
2. Switch floors using top tabs: `L1 (Ground)`, `L2 (EV Hub)`, `L3 (Fleet & VIP)`.
3. Filter bays by type: `All`, `Standard`, `EV Bays`, `Accessible`.
4. Use the zoom controls: `[-]`, `100%`, `[+]`, `[Reset]`.
5. Click on an occupied bay (e.g. `A11` or `A1`).
6. Click `Send Directions to Phone (Simulated)`.
7. Click `Checkout & Bill →`.

### Expected Visual Appearance
- **Architectural Blueprint**: Scale 1:120 CAD grid layout with coordinate ticks (`1, 2, 3, 5, 6` and `A, B, C, D, E`), Inbound Roadway, Gate 1 In barrier, zebra crosswalk, and 11kV Substation deck.
- **Dual Status Indicators**:
  - *Vacant*: Mint green border + VACANT text.
  - *Active Occupied*: Crimson red border + vehicle silhouette icon + ACTIVE text.
  - *VIP / Accessible*: Iris purple border + wheelchair symbol.
  - *EV Hub*: Mint green border + lightning bolt icon.
  - *Maintenance*: Neutral slate background + caution diagonal hatch stripes + OFFLINE text.
- **Bay Stall Inspector**:
  - Embossed yellow Indian number plate with district callout (e.g., `UP32 AB 1234 [Lucknow]`).
  - Simulated optical CCTV camera stream with crosshairs and recording HUD.
  - Telemetry: Vehicle Model, Assigned Hourly Rate (`₹`), Inbound Timestamp (IST), Elapsed Dwell Time, and dynamically calculated accrued parking fee.

### Expected Behavior on Actions
- Zoom buttons scale the CAD blueprint smoothly while maintaining accurate mouse click detection.
- `Send Directions to Phone`: Prompts for 10-digit Indian mobile number (`^[6-9][0-9]{9}$`). Upon entering valid number, displays simulated SMS route dispatch confirmation.
- `Checkout & Bill`: Deep-links immediately to the checkout panel.

### College Viva Talking Points
- **State Pattern**: `ParkingSlot` delegates operations to concrete states (`AvailableState`, `OccupiedState`, `ReservedState`, `OutOfServiceState`), preventing invalid state transitions at compile/runtime.

---

## 3. Vehicle Entry Gate (Fast Check-in)

### Steps to Test
1. Click **Vehicle Entry Gate** on sidebar.
2. Enter or generate a plate number using the random generator (Dice icon).
3. Test validation: Enter an invalid plate (e.g. `ABC 123`) and click Generate Ticket.
4. Select vehicle type (Motorcycle, Car, Electric Car, LCV, HCV).
5. Check special accessibility flags: `Persons with Disabilities (Divyang)` and `EV Fast Charging Required`.
6. Click **Generate Ticket & Open Barrier**.

### Expected Visual Appearance
- Dice icon rendered as a clean Java 2D vector icon (no broken `??` glyphs).
- Checkboxes feature wheelchair and EV plug vector icons alongside labels.
- Live bay assignment preview displays designated bay ID and floor number.

### Expected Behavior on Actions
- Invalid plate prompts an error dialog explaining UP and Bharat series plate formats.
- Valid plate allocates the closest suitable bay, updates slot status to `OCCUPIED` in SQLite, generates a ticket with timestamp, and increments floor occupancy.

### College Viva Talking Points
- **Strategy Pattern**: Slot allocation implements `SlotAllocationStrategy` (`NearestSlotStrategy`, `EvChargingPriorityStrategy`, `AccessiblePriorityStrategy`), allowing runtime policy selection.
- **Validation**: Strict regex validation enforces Indian transport norms (`^UP[0-9]{2}[A-Z]{1,3}[0-9]{4}$` and `^[0-9]{2}BH[0-9]{4}[A-Z]{2}$`).

---

## 4. Checkout & Bill (Cash, UPI QR, FASTag)

### Steps to Test
1. Click **Checkout & Bill** on sidebar.
2. Enter a ticket number or vehicle plate (e.g. `UP32AB1234`).
3. Click **Fetch Bill**.
4. Review the GST Itemized Invoice:
   - Base Parking Fee
   - CGST (9.0%) and SGST (9.0%)
   - Overstay / Lost Ticket penalties (if applicable)
   - Total Amount in INR (`₹`) and in words.
5. Test Payment Methods:
   - **Cash**: Enter tendered cash amount; verify change calculation.
   - **UPI (Simulated)**: Dynamic UPI payment link `upi://pay?pa=...` with auto-generated ZXing QR code.
   - **FASTag (Simulated)**: One-click simulated toll RFID debit with balance deduction.
6. Click **Complete Payment & Print GST Invoice**.

### Expected Visual Appearance
- Live QR code rendered on screen for UPI payments.
- Invoice conforms to Indian GST standards: Registered Business Name, GSTIN (`09AAAAA0000A1Z5`), HSN/SAC code `9967`, CGST/SGST split.

### Expected Behavior on Actions
- Payment records transaction in `payments` table, updates ticket status to `PAID`, releases parking slot back to `AVAILABLE`, and generates PDF invoice using OpenPDF.

### College Viva Talking Points
- **Observer Pattern / Event Dispatch**: System notifies simulated hardware components (LED displays, barrier arms, SMS gateways, and audit logs) on successful departure.
- **GST Compliance**: Section 31 of CGST Act compliance with sequential fiscal invoice numbering (`PK/2026-27/000001`).

---

## 5. Advance Booking (Reservations)

### Steps to Test
1. Click **Advance Booking** on sidebar.
2. Select vehicle type from dropdown.
3. Observe the **Suitable Bay Picker** dropdown auto-filter to compatible bays.
4. Set Start Time (rounded to 15-minute intervals) and Duration (1 to 24 hours).
5. Enter a valid UP plate and click **Confirm Advance Booking**.
6. In the table below, select the new reservation and click **Extend Reservation (+1 hr)**.
7. Test overlap rejection: Try booking the same bay for an overlapping window.

### Expected Visual Appearance
- Mini bay picker replaces manual text entry.
- Table displays high-contrast status badge pills: `CONFIRMED` (green), `ACTIVE` (blue), `EXPIRED` (amber), `CANCELLED` (red).
- Booking Confirmation Dialog pops up with booking code and QR code for scanning at entry gate.

### Expected Behavior on Actions
- Overlapping bookings are rejected with a clear validation message.
- Past start times are prevented.
- Extending adds 1 hour to end time and updates DB.

### College Viva Talking Points
- **Timezone Awareness**: All schedule calculations strictly use `ZoneId.of("Asia/Kolkata")` to avoid server-client UTC drift.

---

## 6. Monthly Passes (Corporate & Resident Passes)

### Steps to Test
1. Click **Monthly Passes** on sidebar.
2. Enter vehicle plate and select pass type (Standard Monthly, VIP Fast-Track, EV Subscriber).
3. Set validity duration (1, 3, 6, 12 months).
4. Review flat-fee preview (GST included).
5. Click **Issue Membership Pass (Simulated)**.
6. Test pass lookup and renewal (+1 Month).

### Expected Visual Appearance
- Clean tabular view of active passes with plate, vehicle class, issue date, expiry date, and status.

### Expected Behavior on Actions
- Pass holders enjoy automatic exit billing waivers when checking out vehicles within the validity window.

---

## 7. Find Vehicle & Live Wayfinding

### Steps to Test
1. Click **Find Vehicle** on sidebar.
2. Type partial plate digits (e.g. `1234` or `UP16`).
3. Press Enter or click **Search**.
4. Inspect search result card:
   - Formatted UP Plate + District badge (`[Noida]`, `[Lucknow]`).
   - Bay ID, Floor, Entry Time, Elapsed Duration, Accrued Fee (`₹`).
5. Click **Show on Floor Map**.
6. Click **Send Route to Mobile (Simulated)**.

### Expected Behavior on Actions
- Deep links directly to Floor Visual Map with the vehicle's bay highlighted and selected in the inspector.
- Entering 10-digit mobile number dispatches simulated turn-by-turn SMS route.

---

## 8. Audit & Executive Reports

### Steps to Test
1. Click **Audit & Reports** on sidebar.
2. Review top 8 KPI cards:
   - Total Bay Capacity
   - Available Bays
   - Occupied Bays
   - Facility Occupancy %
   - Today's Revenue (IST)
   - Monthly Accrued Revenue
   - Active Advance Bookings
   - Active Monthly Passes
3. Check **Floor Utilization Progress Bars** (L1, L2, L3).
4. Check **Revenue Breakdown by Payment Method** (Cash, UPI, FASTag, Card, Pass).
5. Use the **Settled Payment Audit Trail** table:
   - Search by plate or invoice number.
   - Filter by payment method.
   - Paginate through records (`Next >`, `< Prev`).
6. Click **Export CSV** and **Export PDF Report**.

### Expected Visual Appearance
- Modern executive analytics dashboard (monospace text dump completely replaced).
- High-contrast progress bars with live % and bay counts.
- Search bar and pagination controls.

### Expected Behavior on Actions
- Export CSV outputs clean comma-separated audit log.
- Export PDF creates formatted PDF document with timestamp and revenue summaries.

---

## 9. Global Features & Navigation

### Steps to Test
1. Click the **EN | हिन्दी** button in top header bar.
2. Observe all navigation labels, headers, and buttons update instantly to Hindi without restart.
3. Click the toggle again to return to English.
4. Resize the window below 900px width: observe sidebar collapse into compact icon-only mode.
5. Expand window above 900px: observe sidebar expand with full labels.
6. Verify User Profile badge (`Admin | System Administrator`) and `Sign Out` button dimensions (`125x34`).

---

## 10. Summary Checklist for Viva Defense

| Module | Core Concepts & Design Patterns | Verification Status |
| :--- | :--- | :---: |
| **Data Integrity** | Single source of truth from SQLite `slots` table. No hardcoded capacity or count numbers. | PASS |
| **India Localization** | Currency in `₹` everywhere, `Asia/Kolkata` IST timezone, UP & BH vehicle plates, RTO districts. | PASS |
| **Taxation & Compliance** | 18.0% GST (9% CGST + 9% SGST), SAC code 9967, GSTIN, fiscal invoice sequence numbers. | PASS |
| **Visual Polish** | Pure Java 2D vector icons, WCAG AA contrast buttons, no broken glyph boxes (`▯`). | PASS |
| **Floor Blueprint** | Scale 1:120 CAD grid layout, dual status indicators, live bay filters, zoom controls. | PASS |
| **Advance Booking** | 15m rounded intervals, overlap rejection, suitability check, QR code ticket. | PASS |
| **Multi-Language (i18n)** | English & Hindi (Devanagari) dynamic locale switcher without application restart. | PASS |
| **Simulation Transparency**| FASTag, SMS, CCTV, and UPI labeled clearly as `(Simulated)`. | PASS |
| **Automated Tests** | 99 unit tests covering models, services, DAOs, UI consistency, and edge cases. | PASS (100%) |
