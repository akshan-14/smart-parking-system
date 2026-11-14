package com.smartparking.repository;

import com.smartparking.config.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Runs work in one SQLite write transaction started with {@code BEGIN IMMEDIATE}.
 *
 * <p>IMMEDIATE takes the database write lock before the work starts (waiting up to the connection's busy_timeout).
 * Only one write transaction can hold it at a time, across all threads and processes using the file, so everything
 * read inside the work (such as the audit chain head) cannot change until the transaction commits. Either all of
 * the work is committed or, on any exception, all of it is rolled back.
 *
 * <p>Code running inside the work must use only the given connection. Opening a second connection and writing
 * through it would wait for this transaction's lock and fail after busy_timeout.
 */
public final class SqliteTransaction {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection conn) throws SQLException;
    }

    /** Thrown when the transaction could not be started, committed, or the work failed with an SQLException. */
    public static class TransactionException extends RuntimeException {
        public TransactionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private SqliteTransaction() {
    }

    /** Opens a connection, runs the work in an IMMEDIATE transaction and commits; rolls back on any exception. */
    public static <T> T immediate(Work<T> work) {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            return immediate(conn, work);
        } catch (SQLException e) {
            throw new TransactionException("Database transaction failed: " + e.getMessage(), e);
        }
    }

    /** Runs the work in an IMMEDIATE transaction on an existing auto-commit connection. */
    public static <T> T immediate(Connection conn, Work<T> work) {
        try (Statement st = conn.createStatement()) {
            st.execute("BEGIN IMMEDIATE;");
        } catch (SQLException e) {
            throw new TransactionException("Could not start database transaction: " + e.getMessage(), e);
        }
        try {
            T result = work.run(conn);
            try (Statement st = conn.createStatement()) {
                st.execute("COMMIT;");
            }
            return result;
        } catch (SQLException e) {
            rollback(conn, e);
            throw new TransactionException("Database transaction rolled back: " + e.getMessage(), e);
        } catch (RuntimeException | Error e) {
            rollback(conn, e);
            throw e;
        }
    }

    private static void rollback(Connection conn, Throwable cause) {
        try (Statement st = conn.createStatement()) {
            st.execute("ROLLBACK;");
        } catch (SQLException rollbackFailure) {
            // Closing the connection also rolls back an open transaction; keep the original failure primary.
            cause.addSuppressed(rollbackFailure);
        }
    }
}
