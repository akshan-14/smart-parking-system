package com.smartparking.service;

import com.smartparking.model.notification.Notification;
import com.smartparking.model.notification.NotificationChannel;
import com.smartparking.model.notification.NotificationPreference;
import com.smartparking.repository.AlertDao;
import com.smartparking.repository.NotificationDao;
import com.smartparking.repository.NotificationPreferenceDao;
import com.smartparking.repository.SystemSettingsDao;
import com.smartparking.service.notification.NotificationSender;
import com.smartparking.service.notification.SimulatedNotificationSender;
import com.smartparking.util.DateFormat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service orchestrating Simulated Multi-Channel Notifications (SMS, WhatsApp, Email).
 * Validates 10-digit Indian mobile numbers, renders dual-language templates (EN & Hindi),
 * masks sensitive PII (DPDP Act 2023 compliance), respects customer channel preferences,
 * and executes simulated retry logic with alert escalation.
 *
 * NOTE: All notifications are [Simulated] for presentation & viva evaluation.
 */
public class NotificationService {

    public static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");
    private static final Pattern INDIAN_MOBILE_REGEX = Pattern.compile("^[6-9][0-9]{9}$");

    private final NotificationDao notificationDao;
    private final NotificationPreferenceDao preferenceDao;
    private final AlertDao alertDao;
    private final SystemSettingsDao settingsDao;
    private final ConsentService consentService;
    private final AuditLogService auditLogService;
    private final NotificationSender notificationSender;

    public NotificationService(NotificationDao notificationDao, NotificationPreferenceDao preferenceDao,
                               AlertDao alertDao, SystemSettingsDao settingsDao,
                               ConsentService consentService, AuditLogService auditLogService,
                               NotificationSender notificationSender) {
        this.notificationDao = notificationDao;
        this.preferenceDao = preferenceDao;
        this.alertDao = alertDao;
        this.settingsDao = settingsDao;
        this.consentService = consentService;
        this.auditLogService = auditLogService;
        this.notificationSender = notificationSender;
    }

    public NotificationService() {
        this(new NotificationDao(), new NotificationPreferenceDao(), new AlertDao(),
                new SystemSettingsDao(), new ConsentService(), AuditLogService.getInstance(),
                new SimulatedNotificationSender());
    }

    /**
     * Validates that mobile number is a standard 10-digit Indian phone starting with 6-9.
     */
    public void validateMobile(String mobile) {
        if (mobile == null || !INDIAN_MOBILE_REGEX.matcher(mobile.trim()).matches()) {
            throw new IllegalArgumentException("Invalid mobile number '" + mobile + "'. Must be a 10-digit Indian mobile number starting with 6-9.");
        }
    }

    /**
     * Masks a 10-digit phone number as 98XXXXX210 for DPDP privacy in UI & logs.
     */
    public String maskMobile(String mobile) {
        if (mobile == null || mobile.trim().length() < 10) return "XX-XXXX-XXXX";
        String clean = mobile.trim();
        return clean.substring(0, 2) + "XXXXX" + clean.substring(clean.length() - 3);
    }

    /**
     * Renders dual-language notification message templates in English or Hindi.
     */
    public String renderMessage(String templateKey, String lang, Map<String, String> params) {
        boolean isHi = "hi".equalsIgnoreCase(lang) || "hindi".equalsIgnoreCase(lang);

        String template = switch (templateKey) {
            case "BOOKING_CONFIRMED" -> isHi
                    ? "[सिम्युलेटेड] वाहन {plate} हेतु स्लॉट {slot} पर अग्रिम बुकिंग स्वीकृत। कोड: {code}। समय: {start} से {end}।"
                    : "[Simulated] Advance booking confirmed for {plate} at bay {slot}. Code: {code}. Valid: {start} to {end}.";

            case "BOOKING_EXPIRING_15M" -> isHi
                    ? "[सिम्युलेटेड] अनुस्मारक: आपकी बुकिंग {code} 15 मिनट में समाप्त हो रही है। कृपया स्लॉट {slot} पर पहुंचे।"
                    : "[Simulated] Reminder: Booking {code} for bay {slot} expires in 15 minutes. Please arrive promptly.";

            case "BOOKING_EXPIRED_NO_SHOW" -> isHi
                    ? "[सिम्युलेटेड] वाहन {plate} की बुकिंग {code} नो-शो के कारण निरस्त कर दी गई है। स्लॉट {slot} रिक्त है।"
                    : "[Simulated] Booking {code} for {plate} cancelled due to no-show. Slot {slot} has been released.";

            case "PARKING_RECEIPT" -> isHi
                    ? "[सिम्युलेटेड] पार्किंग रसीद: {plate}। इनवॉइस #{inv}, कुल देय: {amount}। ग्रैंड टेक पार्क में पधारने हेतु धन्यवाद।"
                    : "[Simulated] Parking Receipt: {plate}. Invoice #{inv}, Total: {amount}. Thank you for parking at Grand Tech Park.";

            case "OVERSTAY_WARNING" -> isHi
                    ? "[सिम्युलेटेड] चेतावनी: वाहन {plate} ने स्लॉट {slot} में अनुमत समय सीमा पार कर ली है। अतिरिक्त अधिभार लागू होगा।"
                    : "[Simulated] Warning: Vehicle {plate} has exceeded permitted stay in bay {slot}. Overstay penalty applies.";

            case "PASS_EXPIRING_7D" -> isHi
                    ? "[सिम्युलेटेड] वाहन {plate} का मासिक पार्किंग पास 7 दिनों में ({expiry}) समाप्त हो रहा है। कृपया नवीनीकरण करें।"
                    : "[Simulated] Your monthly parking pass for {plate} expires in 7 days on {expiry}. Please renew.";

            case "PASS_RENEWED" -> isHi
                    ? "[सिम्युलेटेड] वाहन {plate} का मासिक पास नवीनीकृत हो गया है। नई समाप्ति तिथि: {expiry}। राशि: {amount}।"
                    : "[Simulated] Monthly pass successfully renewed for {plate}. Valid until {expiry}. Amount: {amount}.";

            case "FASTAG_LOW_BALANCE" -> isHi
                    ? "[सिम्युलेटेड] फ़ास्टैग चेतावनी: वाहन {plate} का बैलेंस कम है ({balance})। निकासी से पूर्व रिचार्ज करें।"
                    : "[Simulated] FASTag Alert: Balance for {plate} is low ({balance}). Please top-up before exit.";

            case "EV_CHARGING_COMPLETE" -> isHi
                    ? "[सिम्युलेटेड] ईवी चार्जिंग पूर्ण: {plate}। {kwh} kWh ऊर्जा ({amount})। 15 मिनट में प्लग हटाएं अन्यथा आइडल शुल्क लगेगा।"
                    : "[Simulated] EV Charging complete for {plate}: {kwh} kWh ({amount}). 15-min grace period to unplug before idle fee.";

            case "PASSWORD_CHANGED" -> isHi
                    ? "[सिम्युलेटेड] सुरक्षा सूचना: आपके खाते '{username}' का पासवर्ड सफलतापूर्वक बदल दिया गया है।"
                    : "[Simulated] Security Alert: Password for your account '{username}' was recently changed.";

            case "DATA_DELETION_STATUS" -> isHi
                    ? "[सिम्युलेटेड] डीपीडीपी डेटा अनुरोध: आपके डेटा निष्कासन अनुरोध #{reqId} की वर्तमान स्थिति: {status}।"
                    : "[Simulated] DPDP Privacy: Your personal data deletion request #{reqId} status is: {status}.";

            default -> isHi
                    ? "[सिम्युलेटेड सूचना] {message}"
                    : "[Simulated Notification] {message}";
        };

        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                template = template.replace("{" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
            }
        }
        return template;
    }

    /**
     * Dispatches a notification across the specified simulated channel.
     * Enforces:
     * 1. Mobile number format validation
     * 2. DPDP consent & notification preference checks
     * 3. Retries up to 3 times before marking FAILED and raising an alert
     */
    public synchronized Notification sendNotification(int userId, String mobileRaw, NotificationChannel channel,
                                                      String templateKey, String lang, Map<String, String> params,
                                                      String relatedEntity) {
        // 1. Mobile validation
        validateMobile(mobileRaw);
        String masked = maskMobile(mobileRaw);
        String channelName = channel != null ? channel.name() : NotificationChannel.SMS.name();

        // 2. Preferences & DPDP Consent Check
        if (userId > 0) {
            boolean enabled = preferenceDao.isChannelEnabled(userId, channelName);
            if (!enabled) {
                // User opted out of this channel
                auditLogService.log("NOTIFICATION_SUPPRESSED", "USER", String.valueOf(userId),
                        channelName, "OPTED_OUT", "Customer opted out of " + channelName + " notifications", "SUCCESS");
                return null;
            }

            boolean hasConsent = consentService.hasConsent(userId, "NOTIFICATIONS");
            if (!hasConsent) {
                auditLogService.log("NOTIFICATION_SUPPRESSED", "USER", String.valueOf(userId),
                        channelName, "NO_CONSENT", "Customer has withdrawn DPDP notification consent", "SUCCESS");
                return null;
            }
        }

        // 3. Render message
        String message = renderMessage(templateKey, lang, params);

        // 4. Record pending notification
        Notification notif = notificationDao.createNotification(userId, masked, channelName, templateKey, message, relatedEntity);

        // 5. Attempt sending with up to 3 retries
        boolean delivered = false;
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            delivered = notificationSender.send(notif);
            if (delivered) {
                break;
            }
        }

        LocalDateTime now = LocalDateTime.now(ZONE_IST);
        if (delivered) {
            notificationDao.updateStatus(notif.getId(), "SENT", now);
            notif.setStatus("SENT");
            notif.setSentAt(now);
            auditLogService.log("NOTIFICATION_SENT", "NOTIFICATION", String.valueOf(notif.getId()),
                    "PENDING", "SENT", "Simulated " + channelName + " dispatched to " + masked, "SUCCESS");
        } else {
            notificationDao.updateStatus(notif.getId(), "FAILED", null);
            notif.setStatus("FAILED");
            // Raise operational alert for failed dispatch
            alertDao.createAlert("NOTIFICATION_FAILED", "WARNING", "NOTIFICATION", String.valueOf(notif.getId()),
                    "Failed delivering " + channelName + " notification to " + masked + " after 3 retries.");
            auditLogService.log("NOTIFICATION_FAILED", "NOTIFICATION", String.valueOf(notif.getId()),
                    "PENDING", "FAILED", "Failed simulated delivery to " + masked + " after 3 retries", "FAILURE");
        }

        return notif;
    }

    public List<Notification> getRecentNotifications(int limit) {
        return notificationDao.findRecent(limit);
    }

    public List<Notification> getUserNotifications(int userId, int limit) {
        return notificationDao.findByUserId(userId, limit);
    }

    public void updatePreference(int userId, NotificationChannel channel, boolean enabled) {
        preferenceDao.setPreference(userId, channel.name(), enabled);
        auditLogService.log("PREFERENCE_UPDATED", "USER", String.valueOf(userId),
                channel.name(), enabled ? "ENABLED" : "DISABLED",
                "Customer updated notification preference for " + channel.name(), "SUCCESS");
    }

    public boolean isChannelEnabled(int userId, NotificationChannel channel) {
        return preferenceDao.isChannelEnabled(userId, channel.name());
    }
}
