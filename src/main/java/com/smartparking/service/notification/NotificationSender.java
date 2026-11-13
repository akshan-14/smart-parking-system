package com.smartparking.service.notification;

import com.smartparking.model.notification.Notification;

/**
 * Pluggable Strategy Interface for Notification delivery.
 *
 * Viva & Architecture Note:
 * In a real production deployment, this interface would be implemented by real telco/gateway providers:
 * - TwilioNotificationSender / SinchNotificationSender (for real SMS / WhatsApp Business API)
 * - SendGridNotificationSender / AmazonSesSender (for real SMTP/REST transactional emails)
 * For college demonstration & offline lab testing, the SimulatedNotificationSender simulates
 * dispatch with zero external network dependencies, configurable demo failure rates, and retry backoffs.
 */
public interface NotificationSender {

    /**
     * Dispatches a notification to the destination.
     *
     * @param notification The notification record to send
     * @return true if successfully delivered; false if delivery failed
     */
    boolean send(Notification notification);
}
