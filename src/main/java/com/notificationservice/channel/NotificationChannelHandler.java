package com.notificationservice.channel;

import com.notificationservice.model.NotificationRequest;
import com.notificationservice.model.NotificationChannel;

/**
 * Interface for notification channel handlers.
 * Each channel (Email, SMS, Push, Webhook) implements this interface.
 */
public interface NotificationChannelHandler {

    /**
     * Get the channel type this handler supports
     */
    NotificationChannel getChannelType();

    /**
     * Send notification through this channel
     * @param request the notification request to send
     * @return true if successful, false otherwise
     * @throws Exception if delivery fails
     */
    boolean send(NotificationRequest request) throws Exception;

    /**
     * Check if this channel is enabled and ready
     */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Get channel-specific configuration or status
     */
    default String getStatus() {
        return "Ready";
    }
}