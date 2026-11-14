package com.smartparking.support;

import com.smartparking.config.DatabaseConnectionManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gives each test class its own throw-away SQLite database.
 *
 * <p>{@link DatabaseConnectionManager} keeps the JDBC URL in a static field whose default is the real
 * application database ({@code smart_parking.db} in the working directory). Test classes that never set a URL
 * therefore ran against the developer's real data (one of them deletes all tickets and payments) or against
 * whatever file the previous class left behind, which made results depend on execution order.
 *
 * <p>Databases live in a private temp directory created once per test JVM. Because the app runs SQLite in
 * WAL mode, {@link #useFresh(String)} also removes the {@code -wal} / {@code -shm} side files so a new database
 * can never replay a stale write-ahead log from an earlier run.
 */
public final class TestDatabase {

    private static final Path DIR = createDir();

    private TestDatabase() {
    }

    private static Path createDir() {
        try {
            Path dir = Files.createTempDirectory("smart-parking-tests-");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create temp directory for test databases", e);
        }
    }

    /**
     * Points the application at a brand-new, empty database file named {@code name}.db and returns its JDBC URL.
     * Call it before {@code DatabaseInitializer.initializeDatabase()}.
     */
    public static String useFresh(String name) {
        Path db = DIR.resolve(name + ".db");
        for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) {
            Path p = DIR.resolve(name + ".db" + suffix);
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not reset test database file " + p, e);
            }
            p.toFile().deleteOnExit();
        }
        String url = "jdbc:sqlite:" + db.toAbsolutePath();
        DatabaseConnectionManager.setDatabaseUrl(url);
        return url;
    }
}
