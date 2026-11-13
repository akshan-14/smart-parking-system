package com.smartparking.service.notification;

import com.smartparking.model.notification.Notification;
import com.smartparking.repository.SystemSettingsDao;

import java.util.Random;

/**
 * Default Simulated implementation of NotificationSender.
 * Labels all outputs "[Simulated]".
 * Reads configurable demo failure rate from settings ('notification.simulated_failure_rate', default 0.0)
 * to demonstrate retry handling and FAILED state transitions during viva evaluation.
 */
public class SimulatedNotificationSender implements NotificationSender {

    private final SystemSettingsDao settingsDao;
    private final Random random = new Random();

    public SimulatedNotificationSender(SystemSettingsDao settingsDao) {
        this.settingsDao = settingsDao;
    }

    public SimulatedNotificationSender() {
        this(new SystemSettingsDao());
    }

    @Override
    public boolean send(Notification notification) {
        if (notification == null) return false;

        double failureRate = settingsDao.getDouble("notification.simulated_failure_rate", 0.0);
        if (failureRate > 0.0 && random.nextDouble() < failureRate) {
            // Simulated network timeout or telco provider reject
            return false;
        }

        // Successfully simulated transmission
        return true;
    }
}
