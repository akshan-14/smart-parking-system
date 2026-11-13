package com.smartparking.service;

import com.smartparking.config.DatabaseConnectionManager;
import com.smartparking.security.AccessControl;
import com.smartparking.security.Permission;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service managing automated live database backups and rotation.
 * Employs SQLite's native VACUUM INTO command for point-in-time consistent snapshots
 * without disrupting active WAL-mode concurrent connections.
 */
public class DatabaseBackupService {

    private static final int MAX_BACKUPS_TO_KEEP = 7;
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static volatile DatabaseBackupService instance;

    private final AuditLogService auditLogService;
    private final ScheduledExecutorService scheduler;

    public DatabaseBackupService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BackupSchedulerThread");
            t.setDaemon(true);
            return t;
        });
        startDailyBackupSchedule();
    }

    public DatabaseBackupService() {
        this(AuditLogService.getInstance());
    }

    public static DatabaseBackupService getInstance() {
        if (instance == null) {
            synchronized (DatabaseBackupService.class) {
                if (instance == null) {
                    instance = new DatabaseBackupService();
                }
            }
        }
        return instance;
    }

    /**
     * Creates a timestamped database backup immediately.
     */
    public synchronized File backupDatabaseNow() throws SQLException, IOException {
        AccessControl.checkPermission(Permission.BACKUP_DATABASE);

        File backupDir = new File("backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(FILE_FMT);
        File targetFile = new File(backupDir, "parking_backup_" + timestamp + ".db");
        String targetPath = targetFile.getAbsolutePath().replace("\\", "/");

        boolean vacuumSuccess = false;
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM INTO '" + targetPath + "';");
            vacuumSuccess = true;
        } catch (SQLException e) {
            // Fallback to safe file copy if VACUUM INTO is unsupported
            String dbUrl = DatabaseConnectionManager.getDatabaseUrl();
            if (dbUrl.startsWith("jdbc:sqlite:") && !dbUrl.contains(":memory:")) {
                String sourcePath = dbUrl.substring("jdbc:sqlite:".length());
                File sourceFile = new File(sourcePath);
                if (sourceFile.exists()) {
                    Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    vacuumSuccess = true;
                }
            }
        }

        if (!vacuumSuccess || !targetFile.exists()) {
            throw new IOException("Failed to create database snapshot at: " + targetFile.getAbsolutePath());
        }

        // Clean up older backups keeping only the latest 7
        rotateBackups(backupDir);

        auditLogService.log("DATABASE_BACKUP_CREATED", "DATABASE", targetFile.getName(),
                null, targetPath, "Admin triggered full live database backup", "SUCCESS");

        return targetFile;
    }

    private void rotateBackups(File backupDir) {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("parking_backup_") && name.endsWith(".db"));
        if (backups != null && backups.length > MAX_BACKUPS_TO_KEEP) {
            // Sort by last modified descending
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
            for (int i = MAX_BACKUPS_TO_KEEP; i < backups.length; i++) {
                backups[i].delete();
            }
        }
    }

    private void startDailyBackupSchedule() {
        // Runs daily every 24 hours
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Background system backup
                File backupDir = new File("backups");
                if (!backupDir.exists()) backupDir.mkdirs();
                String timestamp = LocalDateTime.now().format(FILE_FMT);
                File targetFile = new File(backupDir, "parking_backup_daily_" + timestamp + ".db");
                String targetPath = targetFile.getAbsolutePath().replace("\\", "/");

                try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("VACUUM INTO '" + targetPath + "';");
                }
                rotateBackups(backupDir);
                auditLogService.logWithActor(null, "SYSTEM_SCHEDULER", "SYSTEM",
                        "DAILY_BACKUP_CREATED", "DATABASE", targetFile.getName(),
                        null, targetPath, "Automated scheduled daily backup", "SUCCESS");
            } catch (Exception e) {
                System.err.println("Scheduled backup error: " + e.getMessage());
            }
        }, 24, 24, TimeUnit.HOURS);
    }
}
