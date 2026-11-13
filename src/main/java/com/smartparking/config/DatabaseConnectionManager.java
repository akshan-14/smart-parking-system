package com.smartparking.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Singleton Pattern implementation managing SQLite database connections.
 * Ensures:
 * - A single, centralized access point for database connectivity.
 * - Thread-safe initialization using double-checked locking or holder idiom.
 * - Enforced SQLite foreign key constraints (PRAGMA foreign_keys = ON).
 * - Automatic busy timeout configuration for concurrent write stability.
 */
public class DatabaseConnectionManager {
    private static volatile DatabaseConnectionManager instance;
    private static String databaseUrl = "jdbc:sqlite:smart_parking.db";

    private DatabaseConnectionManager() {
        try {
            // Explicitly load the SQLite JDBC driver class
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            // Fallback: DriverManager will automatically detect driver via ServiceLoader in modern JDBC
        }
    }

    /**
     * Retrieves the Singleton instance of DatabaseConnectionManager.
     */
    public static DatabaseConnectionManager getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnectionManager.class) {
                if (instance == null) {
                    instance = new DatabaseConnectionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Allows overriding the database URL (especially useful for JUnit in-memory testing: jdbc:sqlite::memory:).
     */
    public static synchronized void setDatabaseUrl(String newUrl) {
        databaseUrl = newUrl;
    }

    public static synchronized String getDatabaseUrl() {
        return databaseUrl;
    }

    /**
     * Opens and configures a new database connection with foreign keys, busy timeout, and WAL journal mode enabled.
     */
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(databaseUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            // Enable WAL (Write-Ahead Logging) for multi-gate concurrent transactions
            stmt.execute("PRAGMA journal_mode = WAL;");
        }
        return conn;
    }
}
