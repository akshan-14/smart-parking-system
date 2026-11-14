package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.model.enums.VehicleType;
import com.smartparking.service.GstCalculator;
import com.smartparking.service.InvoiceNumberGenerator;
import com.smartparking.util.NumberToWords;
import com.smartparking.util.PasswordUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Initializes the SQLite database schema and seeds realistic data for 150 slots
 * across 3 floors (50 per floor), UP vehicle plates, Indian tariffs (INR), and settings.
 */
public class DatabaseInitializer {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void initializeDatabase() {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            executeSchemaScript(conn);
            seedInitialData(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database: " + e.getMessage(), e);
        }
    }

    private static void executeSchemaScript(Connection conn) throws Exception {
        InputStream in = DatabaseInitializer.class.getClassLoader().getResourceAsStream("db/schema.sql");
        if (in == null) {
            throw new IllegalStateException("schema.sql not found on classpath!");
        }

        StringBuilder sql = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("--") || line.trim().isEmpty()) {
                    continue;
                }
                sql.append(line).append("\n");
            }
        }

        String[] statements = sql.toString().split(";");
        try (Statement stmt = conn.createStatement()) {
            for (String statement : statements) {
                if (!statement.trim().isEmpty()) {
                    stmt.execute(statement.trim());
                }
            }
        }
    }

    private static void seedInitialData(Connection conn) throws Exception {
        // 0. Ensure schema migrations exist for existing DB files
        applySchemaMigrations(conn);

        // 1. Seed System Settings & Tariffs (Single Source of Truth)
        seedSettings(conn);
        seedTariffs(conn);

        // 1b. Seed Official UP RTO Codes
        seedRtoCodes(conn);

        // 1c. Seed Simulated FASTag Demo Accounts
        seedFastagAccounts(conn);

        // 2. Seed Users
        seedUsers(conn);

        // 3. Seed 3 Floors & 150 Unified Slots with Neutral Bay Codes (A1..A50, B1..B50, C1..C50)
        seedFloorsAndSlots(conn);

        // 3a. Seed Phase 7 EV Chargers on dedicated EV bays
        seedEvChargers(conn);

        // 3b. Automatic Migration: Convert legacy bay codes (F1-S -> A, etc.) and non-UP plates
        migrateLegacyData(conn);

        // 4. Seed Live Active & Historical UP Vehicle Tickets
        seedTicketsAndPayments(conn);

        // 5. Seed Reservations (including 1 stale reservation to test auto-expiry)
        seedReservations(conn);

        // 6. Migrate Historical Completed Payments to compute GST and assign sequential Invoices
        migrateAuditPayments(conn);
    }

    private static void applySchemaMigrations(Connection conn) {
        String[] alterStatements = {
                "ALTER TABLE users ADD COLUMN salt TEXT NOT NULL DEFAULT '';",
                "ALTER TABLE users ADD COLUMN active INTEGER NOT NULL DEFAULT 1;",
                "ALTER TABLE users ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;",
                "ALTER TABLE users ADD COLUMN locked_until TEXT;",
                "ALTER TABLE users ADD COLUMN last_login_at TEXT;",
                "ALTER TABLE users ADD COLUMN force_password_change INTEGER NOT NULL DEFAULT 0;",
                "ALTER TABLE payments ADD COLUMN membership_id INTEGER REFERENCES memberships(membership_id);",
                "ALTER TABLE payments ADD COLUMN taxable_value REAL NOT NULL DEFAULT 0.0;",
                "ALTER TABLE payments ADD COLUMN cgst REAL NOT NULL DEFAULT 0.0;",
                "ALTER TABLE payments ADD COLUMN sgst REAL NOT NULL DEFAULT 0.0;",
                "ALTER TABLE payments ADD COLUMN round_off REAL NOT NULL DEFAULT 0.0;",
                "ALTER TABLE payments ADD COLUMN invoice_number TEXT;",
                "ALTER TABLE audit_log ADD COLUMN user_id INTEGER;",
                "ALTER TABLE audit_log ADD COLUMN username TEXT;",
                "ALTER TABLE audit_log ADD COLUMN role TEXT;",
                "ALTER TABLE audit_log ADD COLUMN entity_type TEXT;",
                "ALTER TABLE audit_log ADD COLUMN entity_id TEXT;",
                "ALTER TABLE audit_log ADD COLUMN ip_or_terminal TEXT;",
                "ALTER TABLE audit_log ADD COLUMN result TEXT;",
                "ALTER TABLE audit_log ADD COLUMN prev_hash TEXT;",
                "ALTER TABLE audit_log ADD COLUMN row_hash TEXT;",
                "ALTER TABLE audit_log ADD COLUMN performed_by TEXT;",
                "ALTER TABLE audit_log ADD COLUMN ticket_id TEXT;",
                "CREATE TABLE IF NOT EXISTS user_vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, license_plate TEXT NOT NULL COLLATE NOCASE, created_at TEXT NOT NULL, UNIQUE(user_id, license_plate), FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE);",
                "CREATE TABLE IF NOT EXISTS consents (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id_or_plate TEXT NOT NULL, purpose TEXT NOT NULL, text_version TEXT NOT NULL, given_at TEXT NOT NULL, withdrawn_at TEXT);",
                "CREATE TABLE IF NOT EXISTS deletion_requests (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, username TEXT NOT NULL, mobile TEXT, requested_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')), reviewed_at TEXT, reviewed_by TEXT, FOREIGN KEY (user_id) REFERENCES users(user_id));",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_active_ticket_plate ON tickets(license_plate) WHERE status IN ('ACTIVE', 'PAID');",
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_active_pass_plate ON memberships(license_plate) WHERE is_active = 1;",

                // Phase 7 migrations
                "CREATE TABLE IF NOT EXISTS settings_history (id INTEGER PRIMARY KEY AUTOINCREMENT, setting_key TEXT NOT NULL, old_value TEXT, new_value TEXT NOT NULL, changed_by TEXT NOT NULL, changed_at TEXT NOT NULL);",
                "CREATE TABLE IF NOT EXISTS ev_chargers (id INTEGER PRIMARY KEY AUTOINCREMENT, slot_id TEXT NOT NULL UNIQUE, connector_type TEXT NOT NULL, power_kw REAL NOT NULL DEFAULT 22.0, status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'CHARGING', 'FAULT', 'OFFLINE')), FOREIGN KEY (slot_id) REFERENCES slots(slot_id));",
                "CREATE TABLE IF NOT EXISTS charging_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, ticket_id INTEGER NOT NULL, charger_id INTEGER NOT NULL, start_time TEXT NOT NULL, end_time TEXT, kwh REAL NOT NULL DEFAULT 0.0, unit_price REAL NOT NULL, energy_charge REAL NOT NULL DEFAULT 0.0, idle_fee REAL NOT NULL DEFAULT 0.0, status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')), FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id), FOREIGN KEY (charger_id) REFERENCES ev_chargers(id));",
                "CREATE TABLE IF NOT EXISTS alerts (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, severity TEXT NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')), entity_type TEXT, entity_id TEXT, message TEXT NOT NULL, created_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')), acknowledged_by TEXT, acknowledged_at TEXT, resolved_by TEXT, resolved_at TEXT, resolution_note TEXT);",
                "CREATE TABLE IF NOT EXISTS notifications (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, mobile_masked TEXT, channel TEXT NOT NULL CHECK (channel IN ('SMS', 'WHATSAPP', 'EMAIL')), template TEXT NOT NULL, message TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'SENT' CHECK (status IN ('PENDING', 'SENT', 'FAILED')), created_at TEXT NOT NULL, sent_at TEXT, related_entity TEXT);",
                "CREATE TABLE IF NOT EXISTS notification_preferences (user_id INTEGER NOT NULL, channel TEXT NOT NULL CHECK (channel IN ('SMS', 'WHATSAPP', 'EMAIL')), enabled INTEGER NOT NULL DEFAULT 1, PRIMARY KEY (user_id, channel), FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE);",
                "ALTER TABLE tickets ADD COLUMN is_demo INTEGER NOT NULL DEFAULT 0;",
                "ALTER TABLE payments ADD COLUMN is_demo INTEGER NOT NULL DEFAULT 0;",
                "ALTER TABLE invoices ADD COLUMN is_demo INTEGER NOT NULL DEFAULT 0;",
                "ALTER TABLE invoices ADD COLUMN ev_kwh REAL NOT NULL DEFAULT 0.0;",
                "ALTER TABLE invoices ADD COLUMN ev_charging_amount REAL NOT NULL DEFAULT 0.0;",
                "ALTER TABLE invoices ADD COLUMN idle_fee REAL NOT NULL DEFAULT 0.0;"
        };
        for (String sql : alterStatements) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            } catch (Exception ignored) {
            }
        }
    }

    private static void seedSettings(Connection conn) throws Exception {
        String insertSetting = "INSERT OR IGNORE INTO settings (setting_key, setting_value, description) VALUES (?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertSetting)) {
            addSetting(ps, "currency_symbol", "₹", "Primary currency symbol (Indian Rupee)");
            addSetting(ps, "currency_code", "INR", "ISO currency code");
            addSetting(ps, "timezone", "Asia/Kolkata", "Standard application timezone");
            // Verify the current GST rate applicable to parking services before final submission.
            addSetting(ps, "gst_rate_percent", "18.0", "Applicable GST tax percentage");
            addSetting(ps, "gst_percentage", "18.0", "Applicable GST tax percentage");
            addSetting(ps, "gstin", "09AAAAA0000A1Z5", "Business GSTIN (Uttar Pradesh 09)");
            addSetting(ps, "business_name", "Grand Tech Park Parking Ltd", "Registered business name");
            addSetting(ps, "business_address", "Plot 42, Knowledge Park III, Greater Noida, UP 201306", "Registered business address");
            addSetting(ps, "upi_vpa", "parking@upi", "UPI Virtual Payment Address");
            addSetting(ps, "grace_period_minutes", "15", "Free parking grace duration in minutes");
            addSetting(ps, "reservation_grace_minutes", "15", "Reservation no-show cancellation grace in minutes");
            addSetting(ps, "lost_ticket_penalty", "250.0", "Standard penalty for lost entry ticket");
            addSetting(ps, "overstay_penalty_per_hour", "50.0", "Hourly penalty for overstaying permitted time");
            addSetting(ps, "peak_multiplier_70", "1.25", "Surge multiplier above 70% occupancy");
            addSetting(ps, "peak_multiplier_80", "1.50", "Surge multiplier above 80% occupancy");
            addSetting(ps, "peak_multiplier_90", "2.00", "Surge multiplier above 90% occupancy");
            addSetting(ps, "rate_compact", "20.0", "Base hourly rate for compact/bike bays in INR");
            addSetting(ps, "rate_regular", "40.0", "Base hourly rate for standard car bays in INR");
            addSetting(ps, "rate_large", "80.0", "Base hourly rate for large/truck bays in INR");
            addSetting(ps, "rate_electric", "60.0", "Base hourly rate for EV charging bays in INR");
            addSetting(ps, "rate_handicapped", "30.0", "Base hourly rate for accessible/VIP bays in INR");
            addSetting(ps, "ev_charging_rate_per_kwh", "15.0", "Electricity charging tariff per kWh");
            addSetting(ps, "peak_multiplier", "1.0", "Admin toggle peak multiplier (e.g. 1.25 for weekend/festival)");
            addSetting(ps, "peak_enabled", "false", "Whether dynamic peak multiplier is currently active");
            // Phase 6: Security, Lockout, and DPDP Retention Settings
            addSetting(ps, "auth.max_failed_attempts", "5", "Maximum consecutive failed login attempts before lockout");
            addSetting(ps, "auth.lockout_duration_minutes", "15", "Account lockout duration in minutes");
            addSetting(ps, "auth.session_timeout_minutes", "15", "Session inactivity timeout in minutes");
            addSetting(ps, "retention.ticket_days", "365", "DPDP statutory retention period for parking ticket and vehicle data");
            addSetting(ps, "retention.image_days", "30", "DPDP statutory retention period for ANPR image snapshots");

            // Phase 7: Settings, EV Charging, Alerts, and Notifications
            addSetting(ps, "ev_charging_idle_fee_per_min", "2.0", "Idle fee per minute after charging completes in INR");
            addSetting(ps, "ev_charging_grace_minutes", "15", "Grace minutes before idle fee applies");
            addSetting(ps, "ev_charging_gst_rate_percent", "18.0", "GST rate for EV charging services (verify applicable GST circular)");
            addSetting(ps, "backup.retention_count", "7", "Number of live rolling database backups retained");
            addSetting(ps, "peak_schedule", "WEEKENDS,FESTIVALS", "Configured peak multiplier schedule");
            addSetting(ps, "peak_festivals", "2026-11-01:2026-11-05:Diwali,2026-03-24:2026-03-26:Holi", "Festival date ranges with surge pricing");
            addSetting(ps, "alert.overstay_hours", "24", "Overstay threshold in hours to trigger operational alert");
            addSetting(ps, "alert.occupancy_high_percent", "90.0", "High occupancy percentage threshold");
            addSetting(ps, "notification.simulated_failure_rate", "0.0", "Simulated notification failure rate (0.0 to 1.0)");
            addSetting(ps, "facility.power_source", "GRID", "Simulated power source: GRID or GENERATOR");
            addSetting(ps, "facility.diesel_percent", "85", "Simulated backup diesel fuel percentage");
        }
    }

    private static void seedTariffs(Connection conn) throws Exception {
        String insertTariff = "INSERT OR REPLACE INTO tariffs (vehicle_type, first_hour, extra_hour, daily_cap, overnight_rate, monthly_pass_rate) VALUES (?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertTariff)) {
            addTariff(ps, "MOTORCYCLE", 10.0, 5.0, 40.0, 30.0, 300.0);
            addTariff(ps, "EV_TWO_WHEELER", 10.0, 5.0, 40.0, 30.0, 300.0);
            addTariff(ps, "AUTO_RICKSHAW", 20.0, 10.0, 80.0, 100.0, 600.0);
            addTariff(ps, "CAR", 40.0, 20.0, 200.0, 100.0, 1500.0);
            addTariff(ps, "EV_CAR", 40.0, 20.0, 200.0, 100.0, 1500.0);
            addTariff(ps, "LCV", 80.0, 40.0, 400.0, 200.0, 3000.0);
            addTariff(ps, "HCV", 100.0, 50.0, 500.0, 200.0, 4500.0);
        }
    }

    private static void addTariff(PreparedStatement ps, String vType, double firstHour, double extraHour, double dailyCap, double overnight, double passRate) throws Exception {
        ps.setString(1, vType);
        ps.setDouble(2, firstHour);
        ps.setDouble(3, extraHour);
        ps.setDouble(4, dailyCap);
        ps.setDouble(5, overnight);
        ps.setDouble(6, passRate);
        ps.executeUpdate();
    }

    private static void addSetting(PreparedStatement ps, String key, String val, String desc) throws Exception {
        ps.setString(1, key);
        ps.setString(2, val);
        ps.setString(3, desc);
        ps.executeUpdate();
    }

    private static void seedRtoCodes(Connection conn) throws Exception {
        // Verify against the official UP Transport Department list before final submission.
        String insertRtoSql = "INSERT OR IGNORE INTO rto_codes (code, district) VALUES (?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertRtoSql)) {
            String[][] rtos = {
                    {"UP32", "Lucknow"},
                    {"UP14", "Ghaziabad"},
                    {"UP16", "Noida (Gautam Buddh Nagar)"},
                    {"UP78", "Kanpur"},
                    {"UP80", "Agra"},
                    {"UP65", "Varanasi"},
                    {"UP70", "Prayagraj"},
                    {"UP53", "Gorakhpur"},
                    {"UP15", "Meerut"}
            };
            for (String[] r : rtos) {
                ps.setString(1, r[0]);
                ps.setString(2, r[1]);
                ps.executeUpdate();
            }
        }
    }

    private static void seedUsers(Connection conn) throws Exception {
        // 1. Read, then close the cursor before writing. Keeping a SELECT open while writing holds a stale WAL
        //    read snapshot; if any other connection commits meanwhile, SQLite rejects the write with
        //    SQLITE_BUSY_SNAPSHOT, which busy_timeout cannot retry.
        int existingUsers;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users;")) {
            existingUsers = rs.next() ? rs.getInt(1) : 0;
        }

        // 2. Do the slow BCrypt hashing (cost 12) before touching the database, not inside the transaction.
        String adminHash = PasswordUtils.hashPassword("Admin@123");
        String attendantHash = PasswordUtils.hashPassword("Attendant@123");
        String customerHash = PasswordUtils.hashPassword("Customer@123");

        // 3. Apply all user writes in one short transaction.
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            if (existingUsers == 0) {
                String insertUserSql = "INSERT INTO users (username, password_hash, salt, full_name, email, phone, role, shift_or_plate, active, failed_attempts, force_password_change, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0, ?, datetime('now'));";
                try (PreparedStatement pstmt = conn.prepareStatement(insertUserSql)) {
                    // Admin (Phase 6 demo credentials: admin / Admin@123, force change password on first login)
                    pstmt.setString(1, "admin");
                    pstmt.setString(2, adminHash);
                    pstmt.setString(3, PasswordUtils.generateSalt());
                    pstmt.setString(4, "System Administrator");
                    pstmt.setString(5, "admin@smartparking.up.gov.in");
                    pstmt.setString(6, "9876501001");
                    pstmt.setString(7, "ADMIN");
                    pstmt.setString(8, null);
                    pstmt.setInt(9, 1); // force password change
                    pstmt.executeUpdate();

                    // Attendant (attendant1 / Attendant@123)
                    pstmt.setString(1, "attendant1");
                    pstmt.setString(2, attendantHash);
                    pstmt.setString(3, PasswordUtils.generateSalt());
                    pstmt.setString(4, "Ramesh Kumar (Attendant)");
                    pstmt.setString(5, "ramesh@smartparking.up.gov.in");
                    pstmt.setString(6, "9876501002");
                    pstmt.setString(7, "ATTENDANT");
                    pstmt.setString(8, "Morning Shift (Lucknow Control)");
                    pstmt.setInt(9, 0);
                    pstmt.executeUpdate();

                    // Customer (customer1 / Customer@123)
                    pstmt.setString(1, "customer1");
                    pstmt.setString(2, customerHash);
                    pstmt.setString(3, PasswordUtils.generateSalt());
                    pstmt.setString(4, "Priya Sharma");
                    pstmt.setString(5, "priya.sharma@example.com");
                    pstmt.setString(6, "9876501003");
                    pstmt.setString(7, "CUSTOMER");
                    pstmt.setString(8, "UP32AB1234");
                    pstmt.setInt(9, 0);
                    pstmt.executeUpdate();
                }
            } else {
                // Ensure existing demo accounts are upgraded with standard Phase 6 BCrypt credentials
                String updateSql = "UPDATE users SET password_hash = ?, salt = CASE WHEN salt IS NULL OR salt = '' THEN ? ELSE salt END, phone = ?, force_password_change = ? WHERE LOWER(username) = LOWER(?);";
                try (PreparedStatement up = conn.prepareStatement(updateSql)) {
                    up.setString(1, adminHash);
                    up.setString(2, PasswordUtils.generateSalt());
                    up.setString(3, "9876501001");
                    up.setInt(4, 1);
                    up.setString(5, "admin");
                    up.executeUpdate();

                    up.setString(1, attendantHash);
                    up.setString(2, PasswordUtils.generateSalt());
                    up.setString(3, "9876501002");
                    up.setInt(4, 0);
                    up.setString(5, "attendant1");
                    up.executeUpdate();

                    up.setString(1, customerHash);
                    up.setString(2, PasswordUtils.generateSalt());
                    up.setString(3, "9876501003");
                    up.setInt(4, 0);
                    up.setString(5, "customer1");
                    up.executeUpdate();
                }
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }

        // Seed demo customer registered vehicles in user_vehicles table (read the id, close, then write)
        Integer customerId = null;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT user_id FROM users WHERE LOWER(username) = 'customer1';")) {
            if (rs.next()) customerId = rs.getInt(1);
        }
        if (customerId != null) {
            String insertVehSql = "INSERT OR IGNORE INTO user_vehicles (user_id, license_plate, created_at) VALUES (?, ?, datetime('now'));";
            try (PreparedStatement psVeh = conn.prepareStatement(insertVehSql)) {
                psVeh.setInt(1, customerId);
                psVeh.setString(2, "UP32AB1234");
                psVeh.executeUpdate();

                psVeh.setInt(1, customerId);
                psVeh.setString(2, "UP14XY5678");
                psVeh.executeUpdate();
            }
        }

        // Seed DPDP statutory consent for customer1
        try (PreparedStatement pc = conn.prepareStatement("INSERT OR IGNORE INTO consents (user_id_or_plate, purpose, text_version, given_at) VALUES (?, ?, ?, datetime('now'));")) {
            pc.setString(1, "customer1");
            pc.setString(2, "REGISTRATION");
            pc.setString(3, "v1.0 (DPDP 2023)");
            pc.executeUpdate();
        }
    }

    private static void seedFloorsAndSlots(Connection conn) throws Exception {
        boolean needSeed = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM slots;")) {
            if (rs.next() && rs.getInt(1) < 150) {
                needSeed = true;
            }
        }

        if (!needSeed) return;

        // Clear existing slots & floors to enforce uniform 150 layout
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM tickets;");
            stmt.execute("DELETE FROM reservations;");
            stmt.execute("DELETE FROM payments;");
            stmt.execute("DELETE FROM slots;");
            stmt.execute("DELETE FROM floors;");
        }

        // Insert 3 Floors (each capacity 50)
        try (PreparedStatement pf = conn.prepareStatement("INSERT INTO floors (floor_number, floor_name, capacity) VALUES (?, ?, ?);")) {
            pf.setInt(1, 1);
            pf.setString(2, "Ground Floor - Standard & EV Station");
            pf.setInt(3, 50);
            pf.executeUpdate();

            pf.setInt(1, 2);
            pf.setString(2, "Level 2 - EV Fast Hub");
            pf.setInt(3, 50);
            pf.executeUpdate();

            pf.setInt(1, 3);
            pf.setString(2, "Level 3 - Fleet & VIP Roof");
            pf.setInt(3, 50);
            pf.executeUpdate();
        }

        // Insert exactly 150 Slots (50 per floor) using neutral bay codes: A1..A50, B1..B50, C1..C50
        String insertSlotSql = "INSERT INTO slots (slot_id, floor_number, slot_number, slot_type, hourly_rate, status, current_vehicle_plate) VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertSlotSql)) {
            // Floor 1: A1 to A50
            for (int i = 1; i <= 50; i++) {
                String slotId = "A" + i;
                String slotNum = "A" + i;
                String type = "REGULAR";
                double rate = 40.0;
                String status = "AVAILABLE";
                String plate = null;

                if (i <= 10) {
                    type = "COMPACT";
                    rate = 20.0;
                } else if (i <= 38) {
                    type = "REGULAR";
                    rate = 40.0;
                } else if (i <= 42) {
                    type = "LARGE";
                    rate = 80.0;
                } else if (i <= 46) {
                    type = "ELECTRIC";
                    rate = 60.0;
                } else {
                    type = "ACCESSIBLE";
                    rate = 30.0;
                }

                // Maintenance slots
                if (i == 9 || i == 10) {
                    status = "MAINTENANCE";
                }

                insertSlotWithPlate(ps, slotId, 1, slotNum, type, rate, status, plate);
            }

            // Floor 2: B1 to B50
            for (int i = 1; i <= 50; i++) {
                String slotId = "B" + i;
                String slotNum = "B" + i;
                String type;
                double rate;
                String status = "AVAILABLE";
                String plate = null;

                if (i <= 8) {
                    type = "COMPACT";
                    rate = 20.0;
                } else if (i <= 28) {
                    type = "REGULAR";
                    rate = 40.0;
                } else if (i <= 44) {
                    type = "ELECTRIC";
                    rate = 60.0;
                } else if (i <= 48) {
                    type = "LARGE";
                    rate = 80.0;
                } else {
                    type = "ACCESSIBLE";
                    rate = 30.0;
                }

                if (i == 15) {
                    status = "MAINTENANCE";
                }

                insertSlotWithPlate(ps, slotId, 2, slotNum, type, rate, status, plate);
            }

            // Floor 3: C1 to C50
            for (int i = 1; i <= 50; i++) {
                String slotId = "C" + i;
                String slotNum = "C" + i;
                String type;
                double rate;
                String status = "AVAILABLE";
                String plate = null;

                if (i <= 10) {
                    type = "COMPACT";
                    rate = 20.0;
                } else if (i <= 30) {
                    type = "REGULAR";
                    rate = 40.0;
                } else if (i <= 44) {
                    type = "LARGE";
                    rate = 80.0;
                } else if (i <= 48) {
                    type = "ELECTRIC";
                    rate = 60.0;
                } else {
                    type = "ACCESSIBLE";
                    rate = 30.0;
                }

                if (i == 20) {
                    status = "MAINTENANCE";
                }

                insertSlotWithPlate(ps, slotId, 3, slotNum, type, rate, status, plate);
            }
        }
    }

    private static void seedEvChargers(Connection conn) throws Exception {
        String insertChargerSql = "INSERT OR IGNORE INTO ev_chargers (slot_id, connector_type, power_kw, status) VALUES (?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertChargerSql)) {
            // Floor 1 EV bays: A43, A44, A45, A46
            addCharger(ps, "A43", "BHARAT_DC001", 30.0, "AVAILABLE");
            addCharger(ps, "A44", "CCS2", 60.0, "AVAILABLE");
            addCharger(ps, "A45", "TYPE2", 22.0, "AVAILABLE");
            addCharger(ps, "A46", "TWO_WHEELER_AC", 3.3, "AVAILABLE");

            // Floor 2 EV bays: B43, B44, B45, B46
            addCharger(ps, "B43", "BHARAT_AC001", 15.0, "AVAILABLE");
            addCharger(ps, "B44", "CCS2", 50.0, "AVAILABLE");
            addCharger(ps, "B45", "TYPE2", 22.0, "AVAILABLE");
            addCharger(ps, "B46", "TWO_WHEELER_AC", 3.3, "AVAILABLE");

            // Floor 3 EV bays: C43, C44, C45, C46
            addCharger(ps, "C43", "BHARAT_DC001", 30.0, "AVAILABLE");
            addCharger(ps, "C44", "CCS2", 60.0, "AVAILABLE");
            addCharger(ps, "C45", "TYPE2", 22.0, "AVAILABLE");
            addCharger(ps, "C46", "TWO_WHEELER_AC", 3.3, "AVAILABLE");
        }
    }

    private static void addCharger(PreparedStatement ps, String slotId, String connector, double kw, String status) throws Exception {
        ps.setString(1, slotId);
        ps.setString(2, connector);
        ps.setDouble(3, kw);
        ps.setString(4, status);
        ps.executeUpdate();
    }

    private static void migrateLegacyData(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // 1. Migrate legacy bay codes: F1-S01..F1-S50 -> A1..A50, F2 -> B, F3 -> C
            for (int i = 1; i <= 50; i++) {
                String old1 = String.format("F1-S%02d", i);
                String new1 = "A" + i;
                stmt.executeUpdate("UPDATE slots SET slot_id = '" + new1 + "', slot_number = '" + new1 + "' WHERE slot_id = '" + old1 + "';");
                stmt.executeUpdate("UPDATE tickets SET slot_id = '" + new1 + "' WHERE slot_id = '" + old1 + "';");
                stmt.executeUpdate("UPDATE reservations SET slot_id = '" + new1 + "' WHERE slot_id = '" + old1 + "';");

                String old2 = String.format("F2-S%02d", i);
                String new2 = "B" + i;
                stmt.executeUpdate("UPDATE slots SET slot_id = '" + new2 + "', slot_number = '" + new2 + "' WHERE slot_id = '" + old2 + "';");
                stmt.executeUpdate("UPDATE tickets SET slot_id = '" + new2 + "' WHERE slot_id = '" + old2 + "';");
                stmt.executeUpdate("UPDATE reservations SET slot_id = '" + new2 + "' WHERE slot_id = '" + old2 + "';");

                String old3 = String.format("F3-S%02d", i);
                String new3 = "C" + i;
                stmt.executeUpdate("UPDATE slots SET slot_id = '" + new3 + "', slot_number = '" + new3 + "' WHERE slot_id = '" + old3 + "';");
                stmt.executeUpdate("UPDATE tickets SET slot_id = '" + new3 + "' WHERE slot_id = '" + old3 + "';");
                stmt.executeUpdate("UPDATE reservations SET slot_id = '" + new3 + "' WHERE slot_id = '" + old3 + "';");
            }

            // 2. Normalize and migrate non-UP plates in tickets
            // Read all rows and close the cursor before updating (no open read snapshot during writes)
            java.util.List<Object[]> tList = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT ticket_id, license_plate FROM tickets;")) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String plate = rs.getString(2);
                    String norm = com.smartparking.util.PlateUtil.mapToUpPlate(plate);
                    tList.add(new Object[]{id, norm});
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE tickets SET license_plate = ? WHERE ticket_id = ?;")) {
                for (Object[] item : tList) {
                    ps.setString(1, (String) item[1]);
                    ps.setInt(2, (Integer) item[0]);
                    ps.executeUpdate();
                }
            }

            // 3. Normalize and migrate non-UP plates in reservations
            // Read all rows and close the cursor before updating (no open read snapshot during writes)
            java.util.List<Object[]> rList = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT reservation_id, license_plate FROM reservations;")) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String plate = rs.getString(2);
                    String norm = com.smartparking.util.PlateUtil.mapToUpPlate(plate);
                    rList.add(new Object[]{id, norm});
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE reservations SET license_plate = ? WHERE reservation_id = ?;")) {
                for (Object[] item : rList) {
                    ps.setString(1, (String) item[1]);
                    ps.setInt(2, (Integer) item[0]);
                    ps.executeUpdate();
                }
            }

            // 4. Normalize and migrate non-UP plates in memberships
            // Read all rows and close the cursor before updating (no open read snapshot during writes)
            java.util.List<Object[]> mList = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT membership_id, license_plate FROM memberships;")) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    String plate = rs.getString(2);
                    String norm = com.smartparking.util.PlateUtil.mapToUpPlate(plate);
                    mList.add(new Object[]{id, norm});
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE memberships SET license_plate = ? WHERE membership_id = ?;")) {
                for (Object[] item : mList) {
                    ps.setString(1, (String) item[1]);
                    ps.setInt(2, (Integer) item[0]);
                    ps.executeUpdate();
                }
            }

            // 5. Normalize and migrate non-UP plates in slots
            // Read all rows and close the cursor before updating (no open read snapshot during writes)
            java.util.List<Object[]> sList = new java.util.ArrayList<>();
            try (ResultSet rs = stmt.executeQuery("SELECT slot_id, current_vehicle_plate FROM slots WHERE current_vehicle_plate IS NOT NULL;")) {
                while (rs.next()) {
                    String slotId = rs.getString(1);
                    String plate = rs.getString(2);
                    String norm = com.smartparking.util.PlateUtil.mapToUpPlate(plate);
                    sList.add(new Object[]{slotId, norm});
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE slots SET current_vehicle_plate = ? WHERE slot_id = ?;")) {
                for (Object[] item : sList) {
                    ps.setString(1, (String) item[1]);
                    ps.setString(2, (String) item[0]);
                    ps.executeUpdate();
                }
            }

            // 6. Migrate vehicle types in tickets
            stmt.executeUpdate("UPDATE tickets SET vehicle_type = 'MOTORCYCLE' WHERE vehicle_type = 'BIKE';");
            stmt.executeUpdate("UPDATE tickets SET vehicle_type = 'HCV' WHERE vehicle_type = 'TRUCK';");
            stmt.executeUpdate("UPDATE tickets SET vehicle_type = 'EV_CAR' WHERE vehicle_type IN ('ELECTRIC', 'ELECTRIC_CAR');");

            // 7. Migrate vehicle types in reservations
            stmt.executeUpdate("UPDATE reservations SET vehicle_type = 'MOTORCYCLE' WHERE vehicle_type = 'BIKE';");
            stmt.executeUpdate("UPDATE reservations SET vehicle_type = 'HCV' WHERE vehicle_type = 'TRUCK';");
            stmt.executeUpdate("UPDATE reservations SET vehicle_type = 'EV_CAR' WHERE vehicle_type IN ('ELECTRIC', 'ELECTRIC_CAR');");

            // 8. Migrate slot types to ACCESSIBLE
            stmt.executeUpdate("UPDATE slots SET slot_type = 'ACCESSIBLE' WHERE slot_type = 'HANDICAPPED';");

            // 9. Ensure memberships table has Phase 3 columns
            try { stmt.executeUpdate("ALTER TABLE memberships ADD COLUMN vehicle_type TEXT NOT NULL DEFAULT 'CAR';"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("ALTER TABLE memberships ADD COLUMN duration_months INTEGER NOT NULL DEFAULT 1;"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("ALTER TABLE memberships ADD COLUMN monthly_fee REAL NOT NULL DEFAULT 1500.0;"); } catch (Exception ignored) {}
            try { stmt.executeUpdate("ALTER TABLE memberships ADD COLUMN total_price REAL NOT NULL DEFAULT 1500.0;"); } catch (Exception ignored) {}
        } catch (Exception ignored) {
        }
    }

    private static void insertSlotWithPlate(PreparedStatement ps, String slotId, int floorNumber, String slotNumber,
                                            String type, double rate, String status, String plate) throws Exception {
        ps.setString(1, slotId);
        ps.setInt(2, floorNumber);
        ps.setString(3, slotNumber);
        ps.setString(4, type);
        ps.setDouble(5, rate);
        ps.setString(6, status);
        ps.setString(7, plate);
        ps.executeUpdate();
    }

    private static void seedTicketsAndPayments(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM tickets;")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // already seeded
            }
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        // Define 35 active occupied vehicles with normalized UP plates and neutral bays
        // Cars: 21, Bikes: 8, Trucks: 3, EVs: 3 = 35 total
        Object[][] activeVehicles = {
                // Cars (21)
                {"UP32AB1234", "CAR", "A11", 1, now.minusHours(2).minusMinutes(15)},
                {"UP16CD2345", "CAR", "A12", 1, now.minusHours(3).minusMinutes(40)},
                {"UP14EF3456", "CAR", "A13", 1, now.minusHours(1).minusMinutes(10)},
                {"UP70GH4567", "CAR", "A14", 1, now.minusHours(4).minusMinutes(25)},
                {"UP65JK5678", "CAR", "A15", 1, now.minusMinutes(45)},
                {"UP80LM6789", "CAR", "A16", 1, now.minusHours(1).minusMinutes(50)},
                {"UP78NP7890", "CAR", "A17", 1, now.minusHours(5).minusMinutes(10)},
                {"UP32QR8901", "CAR", "A18", 1, now.minusMinutes(30)},
                {"UP16ST9012", "CAR", "A19", 1, now.minusHours(2).minusMinutes(5)},
                {"UP14UV0123", "CAR", "A20", 1, now.minusHours(3).minusMinutes(15)},
                {"UP70WX1235", "CAR", "B9", 2, now.minusHours(1).minusMinutes(20)},
                {"UP65YZ2346", "CAR", "B10", 2, now.minusHours(4).minusMinutes(45)},
                {"UP80AA3457", "CAR", "B11", 2, now.minusMinutes(55)},
                {"UP78BB4568", "CAR", "B12", 2, now.minusHours(2).minusMinutes(30)},
                {"UP32CC5679", "CAR", "B13", 2, now.minusHours(6).minusMinutes(12)},
                {"UP16DD6780", "CAR", "B14", 2, now.minusMinutes(20)},
                {"UP14EE7891", "CAR", "C11", 3, now.minusHours(1).minusMinutes(5)},
                {"UP70FF8902", "CAR", "C12", 3, now.minusHours(3).minusMinutes(5)},
                {"UP65GG9013", "CAR", "C13", 3, now.minusHours(2).minusMinutes(40)},
                {"UP80HH0124", "CAR", "C14", 3, now.minusHours(4).minusMinutes(15)},
                {"UP78JJ1236", "CAR", "C15", 3, now.minusMinutes(35)},

                // Motorcycles (8)
                {"UP32BK1101", "MOTORCYCLE", "A1", 1, now.minusHours(1).minusMinutes(15)},
                {"UP16BK1102", "MOTORCYCLE", "A2", 1, now.minusHours(2).minusMinutes(30)},
                {"UP14BK1103", "MOTORCYCLE", "A3", 1, now.minusHours(50)},
                {"UP70BK1104", "MOTORCYCLE", "A4", 1, now.minusHours(3).minusMinutes(10)},
                {"UP65BK1105", "MOTORCYCLE", "B1", 2, now.minusMinutes(25)},
                {"UP80BK1106", "MOTORCYCLE", "B2", 2, now.minusHours(1).minusMinutes(45)},
                {"UP78BK1107", "MOTORCYCLE", "C3", 3, now.minusHours(2).minusMinutes(10)},
                {"UP32BK1108", "MOTORCYCLE", "C2", 3, now.minusMinutes(40)},

                // Heavy Commercial Vehicles (HCV) (3)
                {"UP32TR9901", "HCV", "A39", 1, now.minusHours(5).minusMinutes(20)},
                {"UP16TR9902", "HCV", "C31", 3, now.minusHours(3).minusMinutes(40)},
                {"UP14TR9903", "HCV", "C32", 3, now.minusHours(2).minusMinutes(15)},

                // EVs (3)
                {"UP32EV7001", "EV_CAR", "A43", 1, now.minusHours(1).minusMinutes(30)},
                {"UP16EV7002", "EV_CAR", "B29", 2, now.minusHours(2).minusMinutes(10)},
                {"UP14EV7003", "EV_CAR", "B30", 2, now.minusHours(45)}
        };

        String insertTicketSql = "INSERT INTO tickets (ticket_number, license_plate, vehicle_type, slot_id, floor_number, " +
                "entry_time, exit_time, status, base_fee, penalty_fee, ev_charging_fee, total_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        String updateSlotSql = "UPDATE slots SET status = 'OCCUPIED', current_vehicle_plate = ? WHERE slot_id = ?;";

        try (PreparedStatement pt = conn.prepareStatement(insertTicketSql);
             PreparedStatement ps = conn.prepareStatement(updateSlotSql)) {

            int seq = 1001;
            for (Object[] v : activeVehicles) {
                String plate = (String) v[0];
                String vType = (String) v[1];
                String slotId = (String) v[2];
                int floorNum = (Integer) v[3];
                LocalDateTime inTime = (LocalDateTime) v[4];

                String tktNum = "TKT-UP-" + (seq++);
                pt.setString(1, tktNum);
                pt.setString(2, plate);
                pt.setString(3, vType);
                pt.setString(4, slotId);
                pt.setInt(5, floorNum);
                pt.setString(6, inTime.format(DT_FMT));
                pt.setNull(7, java.sql.Types.VARCHAR);
                pt.setString(8, "ACTIVE");
                pt.setDouble(9, 40.0);
                pt.setDouble(10, 0.0);
                pt.setDouble(11, vType.contains("ELECTRIC") ? 30.0 : 0.0);
                pt.setDouble(12, 0.0);
                pt.executeUpdate();

                // Update slot status in slots table
                ps.setString(1, plate);
                ps.setString(2, slotId);
                ps.executeUpdate();
            }
        }

        // Seed Settled/Historical Payments & Tickets for Real Today's Revenue and Turnover Trends
        seedHistoricalTransactions(conn, now);
    }

    private static void seedHistoricalTransactions(Connection conn, LocalDateTime now) throws Exception {
        String insertTicketSql = "INSERT INTO tickets (ticket_number, license_plate, vehicle_type, slot_id, floor_number, " +
                "entry_time, exit_time, status, base_fee, penalty_fee, ev_charging_fee, total_amount) VALUES (?, ?, ?, ?, ?, ?, ?, 'EXITED', ?, ?, ?, ?);";
        String insertPaymentSql = "INSERT INTO payments (ticket_id, reservation_id, amount, payment_method, transaction_ref, status, payment_time) " +
                "VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?);";

        try (PreparedStatement pt = conn.prepareStatement(insertTicketSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement pp = conn.prepareStatement(insertPaymentSql)) {

            // 1. Transactions completed TODAY (IST) - Totaling ₹1,580
            double[] todayAmounts = {120.0, 80.0, 240.0, 160.0, 320.0, 140.0, 200.0, 180.0, 140.0};
            String[] methods = {"UPI", "CARD", "FASTAG", "UPI", "CARD", "CASH", "UPI", "WALLET", "UPI"};
            String[] todayPlates = {
                    "UP32KM1011", "UP16PQ2022", "UP14RS3033",
                    "UP70TV4044", "UP65XY5055", "UP80ZA6066",
                    "UP78BC7077", "UP32DE8088", "UP16FG9099"
            };

            for (int i = 0; i < todayAmounts.length; i++) {
                LocalDateTime inT = now.minusHours(4 + i).minusMinutes(10);
                LocalDateTime outT = now.minusMinutes(15 + i * 20);

                pt.setString(1, "TKT-HIST-TODAY-" + (i + 1));
                pt.setString(2, todayPlates[i]);
                pt.setString(3, "CAR");
                pt.setString(4, "A" + (21 + i));
                pt.setInt(5, 1);
                pt.setString(6, inT.format(DT_FMT));
                pt.setString(7, outT.format(DT_FMT));
                pt.setDouble(8, todayAmounts[i]);
                pt.setDouble(9, 0.0);
                pt.setDouble(10, 0.0);
                pt.setDouble(11, todayAmounts[i]);
                pt.executeUpdate();

                int tId = 0;
                try (ResultSet rs = pt.getGeneratedKeys()) {
                    if (rs.next()) tId = rs.getInt(1);
                }

                String pMethod = methods[i].equals("FASTAG") ? "CARD" : methods[i];
                pp.setInt(1, tId);
                pp.setNull(2, java.sql.Types.INTEGER);
                pp.setDouble(3, todayAmounts[i]);
                pp.setString(4, pMethod);
                pp.setString(5, "TXN-UP-" + System.currentTimeMillis() + "-" + (i + 1));
                pp.setString(6, outT.format(DT_FMT));
                pp.executeUpdate();
            }

            // 2. Multi-day history for Turnover Chart (Last 7 to 30 days)
            LocalDate today = now.toLocalDate();
            for (int d = 1; d <= 30; d++) {
                LocalDate date = today.minusDays(d);
                int dailyCount = 15 + (d * 7) % 20; // 15 to 34 tickets/day
                for (int k = 1; k <= dailyCount; k++) {
                    LocalDateTime entry = date.atTime(8 + (k % 10), (k * 7) % 60);
                    LocalDateTime exit = entry.plusHours(2 + (k % 4));

                    pt.setString(1, "TKT-PAST-" + d + "-" + k);
                    pt.setString(2, "UP32PAST" + d + "" + k);
                    pt.setString(3, (k % 3 == 0) ? "BIKE" : ((k % 5 == 0) ? "TRUCK" : "CAR"));
                    pt.setString(4, "B" + ((k % 25) + 1));
                    pt.setInt(5, 2);
                    pt.setString(6, entry.format(DT_FMT));
                    pt.setString(7, exit.format(DT_FMT));
                    pt.setDouble(8, 80.0);
                    pt.setDouble(9, 0.0);
                    pt.setDouble(10, 0.0);
                    pt.setDouble(11, 80.0);
                    pt.executeUpdate();
                }
            }
        }
    }

    private static void seedReservations(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM reservations;")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // already seeded
            }
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        // 6 active reservations on real slots: A47..A49, B49, C49, A5
        String[] resSlots = {"A47", "A48", "A49", "B49", "C49", "A5"};
        String[] resPlates = {
                "UP32RES101", "UP16RES102", "UP14RES103",
                "UP70RES104", "UP65RES105", "UP80RES106"
        };

        String insertRes = "INSERT INTO reservations (reservation_code, user_id, slot_id, license_plate, vehicle_type, " +
                "start_time, end_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
        String updateSlotSql = "UPDATE slots SET status = 'RESERVED', current_vehicle_plate = ? WHERE slot_id = ?;";

        try (PreparedStatement pr = conn.prepareStatement(insertRes);
             PreparedStatement ps = conn.prepareStatement(updateSlotSql)) {

            for (int i = 0; i < resSlots.length; i++) {
                String code = "RES-UP-2026-" + (i + 1);
                pr.setString(1, code);
                pr.setInt(2, 3); // customer1
                pr.setString(3, resSlots[i]);
                pr.setString(4, resPlates[i]);
                pr.setString(5, "CAR");
                pr.setString(6, now.plusMinutes(10 + i * 15).format(DT_FMT));
                pr.setString(7, now.plusHours(2 + i).format(DT_FMT));
                pr.setString(8, "CONFIRMED");
                pr.executeUpdate();

                // Update slot status to RESERVED
                ps.setString(1, resPlates[i]);
                ps.setString(2, resSlots[i]);
                ps.executeUpdate();
            }

            // Also seed 1 STALE reservation from yesterday to test automatic startup purge
            pr.setString(1, "RES-UP-STALE-999");
            pr.setInt(2, 3);
            pr.setString(3, "A6");
            pr.setString(4, "UP32OLD9999");
            pr.setString(5, "CAR");
            pr.setString(6, now.minusDays(1).format(DT_FMT));
            pr.setString(7, now.minusDays(1).plusHours(2).format(DT_FMT));
            pr.setString(8, "CONFIRMED");
            pr.executeUpdate();

            // Slot initially reserved
            ps.setString(1, "UP32OLD9999");
            ps.setString(2, "A6");
            ps.executeUpdate();
        }
    }

    private static void seedFastagAccounts(Connection conn) throws Exception {
        String countSql = "SELECT count(*) FROM fastag_accounts;";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return;
            }
        }

        String insertSql = "INSERT OR IGNORE INTO fastag_accounts (tag_id, plate, balance, status) VALUES (?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            Object[][] accounts = {
                    {"TAG-UP32-001", "UP32BK1101", 500.0, "ACTIVE"},
                    {"TAG-UP14-002", "UP14EV7003", 300.0, "ACTIVE"},
                    {"TAG-UP16-003", "UP16CD2345", 850.0, "ACTIVE"},
                    {"TAG-UP80-004", "UP80LM6789", 20.0, "ACTIVE"}
            };
            for (Object[] acc : accounts) {
                ps.setString(1, (String) acc[0]);
                ps.setString(2, (String) acc[1]);
                ps.setDouble(3, (Double) acc[2]);
                ps.setString(4, (String) acc[3]);
                ps.executeUpdate();
            }
        }
    }

    private static void migrateAuditPayments(Connection conn) throws Exception {
        String selectSql = "SELECT p.payment_id, p.ticket_id, p.reservation_id, p.membership_id, p.amount, " +
                "p.taxable_value, p.payment_method, p.transaction_ref, p.invoice_number, p.payment_time, " +
                "t.license_plate, t.vehicle_type, t.entry_time, t.exit_time " +
                "FROM payments p " +
                "LEFT JOIN tickets t ON p.ticket_id = t.ticket_id " +
                "WHERE p.status = 'COMPLETED' AND (p.invoice_number IS NULL OR p.taxable_value = 0.0 OR p.amount <= 0.0);";

        String updatePaymentSql = "UPDATE payments SET amount = ?, taxable_value = ?, cgst = ?, sgst = ?, " +
                "round_off = ?, invoice_number = ? WHERE payment_id = ?;";

        String insertInvoiceSql = "INSERT OR IGNORE INTO invoices (invoice_number, financial_year, invoice_date, " +
                "ticket_id, membership_id, license_plate, vehicle_type, taxable_value, cgst_rate, cgst_amount, " +
                "sgst_rate, sgst_amount, round_off, total_amount, amount_in_words, payment_method, " +
                "transaction_ref, qr_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        InvoiceNumberGenerator numGen = new InvoiceNumberGenerator();
        GstCalculator gstCalc = new GstCalculator();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(selectSql);
             PreparedStatement up = conn.prepareStatement(updatePaymentSql);
             PreparedStatement in = conn.prepareStatement(insertInvoiceSql)) {

            List<Object[]> pending = new ArrayList<>();
            while (rs.next()) {
                pending.add(new Object[]{
                        rs.getInt("payment_id"),
                        rs.getObject("ticket_id"),
                        rs.getObject("reservation_id"),
                        rs.getObject("membership_id"),
                        rs.getDouble("amount"),
                        rs.getString("payment_method"),
                        rs.getString("transaction_ref"),
                        rs.getString("payment_time"),
                        rs.getString("license_plate"),
                        rs.getString("vehicle_type"),
                        rs.getString("invoice_number")
                });
            }

            for (Object[] row : pending) {
                int paymentId = (Integer) row[0];
                Integer ticketId = (Integer) row[1];
                Integer membershipId = (Integer) row[3];
                double currentAmt = (Double) row[4];
                String method = (String) row[5];
                String txnRef = (String) row[6];
                String payTime = (String) row[7];
                String plate = (String) row[8];
                String vType = (String) row[9];
                String existingInv = (String) row[10];

                if (plate == null || plate.isBlank()) plate = "UP32AB1234";
                if (vType == null || vType.isBlank()) vType = "CAR";

                double taxableValue = currentAmt > 0 ? currentAmt : 80.0;
                GstCalculator.GstResult gst = gstCalc.calculate(taxableValue);

                String invNum = (existingInv != null && !existingInv.isBlank()) ? existingInv : numGen.nextInvoiceNumber(conn);
                String fy = InvoiceNumberGenerator.getFinancialYear();

                up.setDouble(1, gst.grandTotal());
                up.setDouble(2, gst.taxableValue());
                up.setDouble(3, gst.cgstAmount());
                up.setDouble(4, gst.sgstAmount());
                up.setDouble(5, gst.roundOff());
                up.setString(6, invNum);
                up.setInt(7, paymentId);
                up.executeUpdate();

                in.setString(1, invNum);
                in.setString(2, fy);
                in.setString(3, payTime != null ? payTime : LocalDateTime.now().format(DT_FMT));
                if (ticketId != null) in.setInt(4, ticketId); else in.setNull(4, java.sql.Types.INTEGER);
                if (membershipId != null) in.setInt(5, membershipId); else in.setNull(5, java.sql.Types.INTEGER);
                in.setString(6, plate);
                in.setString(7, vType);
                in.setDouble(8, gst.taxableValue());
                in.setDouble(9, gst.cgstRatePercent());
                in.setDouble(10, gst.cgstAmount());
                in.setDouble(11, gst.sgstRatePercent());
                in.setDouble(12, gst.sgstAmount());
                in.setDouble(13, gst.roundOff());
                in.setDouble(14, gst.grandTotal());
                in.setString(15, NumberToWords.convert(gst.grandTotal()));
                in.setString(16, method != null ? method : "CASH");
                in.setString(17, txnRef != null ? txnRef : ("TXN-MIG-" + paymentId));
                in.setString(18, "GSTIN:" + gstCalc.getGstin() + "|INV:" + invNum + "|AMT:" + gst.grandTotal());
                in.executeUpdate();
            }
        }
    }
}
