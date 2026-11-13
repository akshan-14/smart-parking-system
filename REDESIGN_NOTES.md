# Dark-mode UI redesign — notes

This document describes the UI redesign of the Smart Parking Management System (Swing desktop client).
Business logic, services, DAOs, the SQLite schema, authentication and RBAC are unchanged.

## 1. Redesigned screens

| Screen | What changed |
| --- | --- |
| **Login** | Two-pane layout: brand + value proposition on a quiet dot-grid with a painted bay motif; form with labels above fields, password visibility toggle (vector icon), **inline validation and auth errors** (no pop-ups), Enter-to-submit, demo-account shortcuts. |
| **App shell** | Sidebar + top bar kept. Focus rings now appear only during keyboard navigation (the first nav item no longer shows a permanent outline). |
| **Overview (dashboard)** | KPI tiles (available, occupied, reserved, occupancy %, revenue today, today's entries/exits), vehicle-turnover chart (7D/30D/90D/1Y), occupancy by level, revenue last 7 days, vehicle-mix donut, operational alerts that deep-link to the bay. All values come from SQLite. |
| **Floor map** | Rebuilt as a responsive bay grid: tiles reflow to the window width, aisles separated by drive lanes. Distinct states **Available / Occupied / Reserved / Maintenance (hatched) / EV charging** (from active charging sessions), EV and accessible bay markers, plate on occupied bays, tooltips, hover and selection states. Per-level counters double as the legend, plus a stacked occupancy bar. Filters (All / Standard / EV / Accessible) dim non-matching bays. Tile-size zoom. Bay inspector shows the **real** ticket, entry time, duration and accrued fee. Keyboard: arrow keys move between bays, Enter opens checkout. Double-click an occupied bay to check it out. |
| **Vehicle entry** | Sectioned form (vehicle type tiles, plate with ANPR-simulated random plate, options, allocation strategy). Inline plate validation. Ticket panel with empty state; after parking shows the allocated bay prominently, ticket details, QR and barcode on a white "paper" strip. Success/failure as toasts. |
| **Checkout & billing** | Three steps left to right: **1 Bill** (vehicle, stay, fee lines, GST, total, admin override) → **2 Payment** (radio-style method list with per-method detail: cash change calculator, dynamic UPI QR, card approve/decline simulation, wallet, FASTag balance check) → **3 Gate & receipt** (barrier status, GST invoice on paper, refund / print / PDF). Inline lookup errors; toasts instead of pop-ups; refund still asks for confirmation and a reason. |
| **Advance booking** | Vertical booking form (no more overflowing toolbar), inline validation, end-time preview, bookings table with status pills, count, empty state, Extend/Cancel enabled only when a row is selected; cancel confirms. |
| **Monthly passes** | Issue-a-pass card with price summary, check-a-pass card with structured result and status pill, pass-holder table with empty state. GST/payment dialog restyled. |
| **Find vehicle** | Standard page header; full-width search bar. |
| **Audit & reports** | Standard header, short tab names, period segmented control with a visible active state, 2×4 KPI grid, neutral revenue-by-method labels. |
| **Security audit trail** | Standard header and secondary actions; **user / action / result / search filters are now on screen and wired** to the existing `AuditLogDao.findFiltered` (they were created but never shown); status pills; monospace hash column; pagination with icons. |
| **Users & access** | Header actions (New user, Reset password, Activate/deactivate enabled on selection), status pills, **confirmation before deactivating** an account, toasts. |
| **Settings** | Tab labels and subtitle now show real text (see bugs fixed). |
| **Privacy notice** | Dark-theme HTML (was navy-on-charcoal), readable sizes, Hindi tab title renders. |
| **My data (customer)** | Standard header; download/erase actions in the design-system button styles. |

## 2. Design system

**Tokens** — `ui/theme/UITheme.java`

| Role | Value |
| --- | --- |
| App background / sidebar | `#090B10` |
| Canvas | `#0D1117` |
| Surface | `#10141D` |
| Card | `#141A24` |
| Elevated (inputs, headers) | `#1A2230` |
| Hover | `#212B3B` |
| Border / subtle / strong | `#263244` / `#1D2634` / `#33415A` |
| Text primary / body / secondary / tertiary | `#F1F5F9` / `#CBD5E1` / `#94A3B8` / `#64748B` |
| Accent (indigo) | `#6366F1`, hover `#7679F5`, pressed `#4F46E5`, text `#A5B4FC` |
| Success / Warning / Error / Info | `#22C55E` / `#F59E0B` / `#EF4444` / `#38BDF8` |
| Slot states | Available = success, Occupied = error, Reserved = warning, Maintenance = `#64748B`, EV = teal `#14B8A6`, Accessible = indigo-400 |

- **Spacing**: 4 px grid (`SP_1`…`SP_8`); pages use 24/28 px padding, cards 16–24 px.
- **Radii**: 6 / 8 / 10 px.
- **Typography**: Inter → Segoe UI Variable → Segoe UI → SF Pro → Helvetica Neue → Roboto → Dialog (first installed wins). Page title 20 bold, card title 14 bold, body 13, caption 11 bold caps. Monospace (JetBrains Mono → … → Menlo) for plates and IDs. `UITheme.fontFor(text, …)` falls back to the platform composite font for scripts the UI font can't render (e.g. Devanagari).

**Look-and-feel** — `ui/theme/DarkTheme.java` installs flat UI delegates (buttons, scrollbars, tabs, combo boxes, spinners, borders, dialogs) and:
- styles every `JTable` automatically: row height, zebra rows, hover row, header renderer;
- sizes columns to their content, and falls back to horizontal scrolling rather than eliding every cell;
- renders `Status` / `Result` columns as tinted pills;
- shows focus rings only while the user navigates with the keyboard (`DarkTheme.isFocusVisible()`).

**Components** — `ui/components/`
- `ModernButton`: Primary, Secondary, Ghost, Danger (soft), Success (soft), plus `.compact()`. Ad-hoc saturated colours passed by older code are mapped onto these variants automatically.
- `PageHeader`: title, one-line description, actions.
- `RoundedPanel` cards, `Badge` status pills (also a table renderer), `EmptyState`, `SegmentedControl`, `MetricCard`, `ScrollablePanel`.
- `Toast` (new): non-blocking success, error, warning and info notifications.

**Icons**: vector icons from `ui/util/VectorIconUtil` everywhere. No emoji or font symbols, which render as empty boxes on platforms without those glyphs.

## 3. Bugs fixed along the way (UI layer)

1. **Customer sign-in crashed.** The main window builds every panel up front, and the audit-log, user-management and settings panels loaded admin-only data in their constructors, which threw `SecurityException` for customers. Those eager loads are now skipped unless the role has the permission. RBAC is still enforced in `refreshData()` and the services.
2. **Floor map "Checkout & Bill" opened "Access Denied".** It navigated to a non-existent card `CHECKOUT`. It now deep-links to `EXIT:<ticket>`, opening Checkout with the ticket already looked up.
3. **Renewing a monthly pass billed the wrong vehicle class.** The code read the *District* column as the vehicle type; it now uses the underlying pass record.
4. **Settings showed raw i18n keys.** Added the `settings.tab.*` and `settings.subtitle` keys (English and Hindi).
5. **Audit log filters were never shown.** They are now displayed and applied.
6. **Floor map inspector showed fabricated values** ("10:30:00 (Today)", "45m 12s", invented plates) when no ticket was found. It now says so instead.
7. **Fake telemetry removed from the floor map**: static CO-level/ventilation/sprinkler readings, the "mesh signal 99.8%" footer and the simulated CCTV panel.

## 4. Pre-existing issues (not caused by the redesign)

- **Compile error in the original commit**: `NotificationService` called `ConsentService.hasConsent(int, String)`, which doesn't exist. Fixed with a one-line change to `hasValidConsent(String.valueOf(userId), "NOTIFICATIONS")`.
- `VisualVerificationTest` (2 tests) writes PNGs to a hard-coded Windows path (`C:/Users/ASUS/.gemini/...`) and fails on any other machine. The panels render successfully before the write.
- `ParkingServiceTest.testElectricCarCharging` expects a non-zero EV charging fee for a car that exits the instant it parks, so it fails without waiting.

## 5. Running

```bash
mvn compile exec:java                        # GUI (login window)
mvn compile exec:java -Dexec.args="--demo"   # scripted console demo
mvn compile exec:java -Dexec.args="--cli"    # console menu
mvn test                                     # JUnit 5 suite
mvn package                                  # runnable JAR in target/
```

Demo accounts: `admin / Admin@123`, `attendant1 / Attendant@123`, `customer1 / Customer@123` (also one click away on the login screen).

Keyboard: `Ctrl+1…9` jump between sidebar screens; on the floor map use the arrow keys to move between bays and Enter to check out.
