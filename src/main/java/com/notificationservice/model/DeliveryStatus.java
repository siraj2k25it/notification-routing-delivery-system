package com.notificationservice.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Record representing the delivery status of a notification.
 */
public record DeliveryStatus(
        String eventId,
        String requestId,
        NotificationChannel channel,
        NotificationStatus status,
        int retryCount,
        LocalDateTime createdAt,
        LocalDateTime lastAttemptAt,
        String failureReason,
        List<RetryAttempt> retryAttempts
) {

    public static DeliveryStatus fromRequest(NotificationRequest request, List<RetryAttempt> attempts) {
        return new DeliveryStatus(
                request.eventId(),
                request.requestId(),
                request.channel(),
                request.status(),
                request.retryCount(),
                request.createdAt(),
                request.lastRetryAt(),
                request.failureReason(),
                attempts != null ? attempts : List.of()
        );
    }

    public static DeliveryStatus forEvent(String eventId, NotificationStatus status) {
        return new DeliveryStatus(
                eventId, null, null, status, 0,
                LocalDateTime.now(), null, null, List.of()
        );
    }
}