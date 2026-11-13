package com.smartparking.security;

import com.smartparking.model.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Singleton managing the authenticated user session, activity timestamps,
 * and automated inactivity timeout detection.
 */
public class UserSession {

    private static volatile UserSession instance;

    private User currentUser;
    private LocalDateTime loginTime;
    private volatile long lastActivityTimestamp;
    private int timeoutMinutes = 15;
    private boolean warningEmitted = false;
    private final List<SessionListener> listeners = new ArrayList<>();

    public interface SessionListener {
        void onWarning(int minutesRemaining);
        void onTimeout();
    }

    private UserSession() {
        this.lastActivityTimestamp = System.currentTimeMillis();
    }

    public static UserSession getInstance() {
        if (instance == null) {
            synchronized (UserSession.class) {
                if (instance == null) {
                    instance = new UserSession();
                }
            }
        }
        return instance;
    }

    public synchronized void startSession(User user, int timeoutMinutes) {
        this.currentUser = user;
        this.loginTime = LocalDateTime.now();
        this.timeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : 15;
        this.lastActivityTimestamp = System.currentTimeMillis();
        this.warningEmitted = false;
    }

    public synchronized void recordActivity() {
        this.lastActivityTimestamp = System.currentTimeMillis();
        this.warningEmitted = false;
    }

    public synchronized Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public synchronized LocalDateTime getLoginTime() {
        return loginTime;
    }

    public synchronized boolean isAuthenticated() {
        return currentUser != null;
    }

    public synchronized boolean isExpired() {
        if (currentUser == null) return false;
        long elapsedMillis = System.currentTimeMillis() - lastActivityTimestamp;
        return elapsedMillis >= (long) timeoutMinutes * 60 * 1000;
    }

    public synchronized boolean isWarningWindow() {
        if (currentUser == null) return false;
        long elapsedMillis = System.currentTimeMillis() - lastActivityTimestamp;
        long warningThreshold = (long) (timeoutMinutes - 1) * 60 * 1000;
        return elapsedMillis >= warningThreshold && !isExpired();
    }

    public synchronized void checkInactivity() {
        if (currentUser == null) return;

        if (isExpired()) {
            User timedOutUser = currentUser;
            clearSession();
            for (SessionListener l : new ArrayList<>(listeners)) {
                l.onTimeout();
            }
        } else if (isWarningWindow() && !warningEmitted) {
            warningEmitted = true;
            for (SessionListener l : new ArrayList<>(listeners)) {
                l.onWarning(1);
            }
        }
    }

    public synchronized void clearSession() {
        this.currentUser = null;
        this.loginTime = null;
        this.warningEmitted = false;
    }

    public synchronized void addListener(SessionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }
}
