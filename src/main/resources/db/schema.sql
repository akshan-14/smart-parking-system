-- Smart Parking Management System SQLite Schema
PRAGMA foreign_keys = ON;

-- 1. Users table (Admin, Attendant, Customer)
CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL DEFAULT '',
    full_name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    role TEXT NOT NULL CHECK (role IN ('ADMIN', 'ATTENDANT', 'CUSTOMER')),
    shift_or_plate TEXT,
    active INTEGER NOT NULL DEFAULT 1,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TEXT,
    last_login_at TEXT,
    force_password_change INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

-- 1b. Customer Registered Vehicles table (DPDP Act, 2023 & Customer isolation)
CREATE TABLE IF NOT EXISTS user_vehicles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    license_plate TEXT NOT NULL COLLATE NOCASE,
    created_at TEXT NOT NULL,
    UNIQUE(user_id, license_plate),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- 1c. DPDP Statutory Consent Records table
CREATE TABLE IF NOT EXISTS consents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id_or_plate TEXT NOT NULL,
    purpose TEXT NOT NULL,
    text_version TEXT NOT NULL,
    given_at TEXT NOT NULL,
    withdrawn_at TEXT
);

-- 1d. Customer Personal Data Erasure Requests (DPDP Act, 2023)
CREATE TABLE IF NOT EXISTS deletion_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    username TEXT NOT NULL,
    mobile TEXT,
    requested_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_at TEXT,
    reviewed_by TEXT,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 2. Parking Floors table
CREATE TABLE IF NOT EXISTS floors (
    floor_id INTEGER PRIMARY KEY AUTOINCREMENT,
    floor_number INTEGER NOT NULL UNIQUE,
    floor_name TEXT NOT NULL,
    capacity INTEGER DEFAULT 0
);

-- 3. Parking Slots table
CREATE TABLE IF NOT EXISTS slots (
    slot_id TEXT PRIMARY KEY,
    floor_number INTEGER NOT NULL,
    slot_number TEXT NOT NULL,
    slot_type TEXT NOT NULL CHECK (slot_type IN ('COMPACT', 'REGULAR', 'LARGE', 'ELECTRIC', 'HANDICAPPED', 'ACCESSIBLE')),
    hourly_rate REAL NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'RESERVED', 'MAINTENANCE', 'OUT_OF_SERVICE')),
    current_vehicle_plate TEXT,
    FOREIGN KEY (floor_number) REFERENCES floors(floor_number) ON DELETE CASCADE
);

-- 3b. System Tariff & Configuration Settings table
CREATE TABLE IF NOT EXISTS settings (
    setting_key TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL,
    description TEXT
);

-- 3c. Indian Tariff Rates Table (Seeded per Vehicle Class)
CREATE TABLE IF NOT EXISTS tariffs (
    vehicle_type TEXT PRIMARY KEY,
    first_hour REAL NOT NULL,
    extra_hour REAL NOT NULL,
    daily_cap REAL NOT NULL,
    overnight_rate REAL NOT NULL,
    monthly_pass_rate REAL NOT NULL
);

-- 4. Parking Tickets table
CREATE TABLE IF NOT EXISTS tickets (
    ticket_id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_number TEXT NOT NULL UNIQUE,
    license_plate TEXT NOT NULL COLLATE NOCASE,
    vehicle_type TEXT NOT NULL,
    slot_id TEXT NOT NULL,
    floor_number INTEGER NOT NULL,
    entry_time TEXT NOT NULL,
    exit_time TEXT,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'PAID', 'EXITED', 'LOST')),
    base_fee REAL DEFAULT 0.0,
    penalty_fee REAL DEFAULT 0.0,
    ev_charging_fee REAL DEFAULT 0.0,
    total_amount REAL DEFAULT 0.0,
    FOREIGN KEY (slot_id) REFERENCES slots(slot_id)
);

-- 5. Penalties table
CREATE TABLE IF NOT EXISTS penalties (
    penalty_id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL,
    penalty_type TEXT NOT NULL,
    amount REAL NOT NULL,
    reason TEXT,
    issued_at TEXT NOT NULL,
    FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id) ON DELETE CASCADE
);

-- 6. Reservations table
CREATE TABLE IF NOT EXISTS reservations (
    reservation_id INTEGER PRIMARY KEY AUTOINCREMENT,
    reservation_code TEXT NOT NULL UNIQUE,
    user_id INTEGER NOT NULL,
    slot_id TEXT NOT NULL,
    license_plate TEXT NOT NULL COLLATE NOCASE,
    vehicle_type TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'EXPIRED')),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    FOREIGN KEY (slot_id) REFERENCES slots(slot_id)
);

-- 7. Payment Transactions table
CREATE TABLE IF NOT EXISTS payments (
    payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER,
    reservation_id INTEGER,
    membership_id INTEGER,
    amount REAL NOT NULL,
    taxable_value REAL NOT NULL DEFAULT 0.0,
    cgst REAL NOT NULL DEFAULT 0.0,
    sgst REAL NOT NULL DEFAULT 0.0,
    round_off REAL NOT NULL DEFAULT 0.0,
    payment_method TEXT NOT NULL CHECK (payment_method IN ('CASH', 'CARD', 'UPI', 'WALLET', 'FASTAG')),
    transaction_ref TEXT NOT NULL UNIQUE,
    invoice_number TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    payment_time TEXT NOT NULL,
    FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id),
    FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    FOREIGN KEY (membership_id) REFERENCES memberships(membership_id)
);

-- 7b. Simulated FASTag Accounts table
CREATE TABLE IF NOT EXISTS fastag_accounts (
    tag_id TEXT PRIMARY KEY,
    plate TEXT NOT NULL UNIQUE COLLATE NOCASE,
    balance REAL NOT NULL DEFAULT 0.0 CHECK (balance >= 0.0),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED', 'SUSPENDED')),
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);

-- 7c. Sequential Invoice Number Series table
CREATE TABLE IF NOT EXISTS invoice_sequences (
    financial_year TEXT PRIMARY KEY,
    current_seq INTEGER NOT NULL DEFAULT 0
);

-- 7d. GST Tax Invoices table
CREATE TABLE IF NOT EXISTS invoices (
    invoice_id INTEGER PRIMARY KEY AUTOINCREMENT,
    invoice_number TEXT NOT NULL UNIQUE,
    financial_year TEXT NOT NULL,
    invoice_date TEXT NOT NULL,
    ticket_id INTEGER,
    membership_id INTEGER,
    license_plate TEXT NOT NULL,
    vehicle_type TEXT NOT NULL,
    taxable_value REAL NOT NULL,
    cgst_rate REAL NOT NULL,
    cgst_amount REAL NOT NULL,
    sgst_rate REAL NOT NULL,
    sgst_amount REAL NOT NULL,
    round_off REAL NOT NULL,
    total_amount REAL NOT NULL,
    amount_in_words TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    transaction_ref TEXT NOT NULL,
    qr_data TEXT NOT NULL,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id),
    FOREIGN KEY (membership_id) REFERENCES memberships(membership_id)
);

-- 8. Memberships table (Flat Monthly Pass Model)
CREATE TABLE IF NOT EXISTS memberships (
    membership_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    license_plate TEXT NOT NULL UNIQUE COLLATE NOCASE,
    vehicle_type TEXT NOT NULL DEFAULT 'CAR',
    pass_type TEXT NOT NULL DEFAULT 'FLAT_MONTHLY',
    duration_months INTEGER NOT NULL DEFAULT 1,
    monthly_fee REAL NOT NULL DEFAULT 1500.0,
    total_price REAL NOT NULL DEFAULT 1500.0,
    discount_percentage REAL NOT NULL DEFAULT 1.0,
    start_date TEXT NOT NULL,
    expiry_date TEXT NOT NULL,
    is_active INTEGER DEFAULT 1,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 8b. Immutable System Audit Log with SHA-256 Hash Chain
CREATE TABLE IF NOT EXISTS audit_log (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    user_id INTEGER,
    username TEXT,
    role TEXT,
    action TEXT NOT NULL,
    entity_type TEXT,
    entity_id TEXT,
    old_value TEXT,
    new_value TEXT,
    reason TEXT,
    ip_or_terminal TEXT,
    result TEXT,
    prev_hash TEXT,
    row_hash TEXT,
    performed_by TEXT,
    ticket_id TEXT
);

-- 9. Regional Transport Office (RTO) Codes for Uttar Pradesh
-- Verify against the official UP Transport Department list before final submission.
CREATE TABLE IF NOT EXISTS rto_codes (
    code TEXT PRIMARY KEY,
    district TEXT NOT NULL
);

INSERT OR IGNORE INTO rto_codes (code, district) VALUES
    ('UP32', 'Lucknow'),
    ('UP14', 'Ghaziabad'),
    ('UP16', 'Noida (Gautam Buddh Nagar)'),
    ('UP78', 'Kanpur'),
    ('UP80', 'Agra'),
    ('UP65', 'Varanasi'),
    ('UP70', 'Prayagraj'),
    ('UP53', 'Gorakhpur'),
    ('UP15', 'Meerut');

-- Performance & Concurrency Integrity Indices
CREATE INDEX IF NOT EXISTS idx_tickets_plate ON tickets(license_plate);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_slots_status ON slots(status);
CREATE INDEX IF NOT EXISTS idx_reservations_plate ON reservations(license_plate);
CREATE INDEX IF NOT EXISTS idx_memberships_plate ON memberships(license_plate);
CREATE INDEX IF NOT EXISTS idx_rto_codes_code ON rto_codes(code);

-- Single active ticket and single active pass per vehicle plate
CREATE UNIQUE INDEX IF NOT EXISTS idx_active_ticket_plate ON tickets(license_plate) WHERE status IN ('ACTIVE', 'PAID');
CREATE UNIQUE INDEX IF NOT EXISTS idx_active_pass_plate ON memberships(license_plate) WHERE is_active = 1;

-- ========================================================
-- Phase 7: Settings History, EV Charging, Alerts, Notifications
-- ========================================================

-- 10. Settings Change History Table
CREATE TABLE IF NOT EXISTS settings_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    setting_key TEXT NOT NULL,
    old_value TEXT,
    new_value TEXT NOT NULL,
    changed_by TEXT NOT NULL,
    changed_at TEXT NOT NULL
);

-- 11. EV Chargers Configuration Table
CREATE TABLE IF NOT EXISTS ev_chargers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    slot_id TEXT NOT NULL UNIQUE,
    connector_type TEXT NOT NULL,
    power_kw REAL NOT NULL DEFAULT 22.0,
    status TEXT NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'CHARGING', 'FAULT', 'OFFLINE')),
    FOREIGN KEY (slot_id) REFERENCES slots(slot_id)
);

-- 12. EV Charging Sessions Table
CREATE TABLE IF NOT EXISTS charging_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL,
    charger_id INTEGER NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT,
    kwh REAL NOT NULL DEFAULT 0.0,
    unit_price REAL NOT NULL,
    energy_charge REAL NOT NULL DEFAULT 0.0,
    idle_fee REAL NOT NULL DEFAULT 0.0,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    FOREIGN KEY (ticket_id) REFERENCES tickets(ticket_id),
    FOREIGN KEY (charger_id) REFERENCES ev_chargers(id)
);

-- 13. Operational Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    entity_type TEXT,
    entity_id TEXT,
    message TEXT NOT NULL,
    created_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
    acknowledged_by TEXT,
    acknowledged_at TEXT,
    resolved_by TEXT,
    resolved_at TEXT,
    resolution_note TEXT
);

-- 14. Simulated Notifications Dispatch Log
CREATE TABLE IF NOT EXISTS notifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    mobile_masked TEXT,
    channel TEXT NOT NULL CHECK (channel IN ('SMS', 'WHATSAPP', 'EMAIL')),
    template TEXT NOT NULL,
    message TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'SENT' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at TEXT NOT NULL,
    sent_at TEXT,
    related_entity TEXT
);

-- 15. Customer Notification Channel Preferences
CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id INTEGER NOT NULL,
    channel TEXT NOT NULL CHECK (channel IN ('SMS', 'WHATSAPP', 'EMAIL')),
    enabled INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, channel),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_charging_ticket ON charging_sessions(ticket_id);
CREATE INDEX IF NOT EXISTS idx_charging_status ON charging_sessions(status);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_type ON alerts(type);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);

