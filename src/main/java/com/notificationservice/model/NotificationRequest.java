package com.notificationservice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * NotificationRequest record representing a specific notification delivery request.
 */
public record NotificationRequest(
        String requestId,
        String eventId,

        @NotNull
        NotificationChannel channel,

        @NotBlank
        String recipient,

        String subject,
        String message,
        Event.Priority priority,
        LocalDateTime createdAt,
        NotificationStatus status,
        int retryCount,
        LocalDateTime lastRetryAt,
        String failureReason
) {

    // Constructor with defaults
    public NotificationRequest {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = NotificationStatus.PENDING;
        }
        if (retryCount < 0) {
            retryCount = 0;
        }
    }

    // Builder-style methods for updates
    public NotificationRequest withStatus(NotificationStatus newStatus) {
        return new NotificationRequest(requestId, eventId, channel, recipient,
                subject, message, priority, createdAt, newStatus, retryCount,
                lastRetryAt, failureReason);
    }

    public NotificationRequest withRetry(int newRetryCount, String reason) {
        return new NotificationRequest(requestId, eventId, channel, recipient,
                subject, message, priority, createdAt, NotificationStatus.FAILED,
                newRetryCount, LocalDateTime.now(), reason);
    }

    public NotificationRequest withFailure(String reason) {
        return new NotificationRequest(requestId, eventId, channel, recipient,
                subject, message, priority, createdAt, NotificationStatus.FAILED,
                retryCount, LocalDateTime.now(), reason);
    }

    // Static factory methods
    public static NotificationRequest create(String eventId, NotificationChannel channel,
                                             String recipient, String subject, String message,
                                             Event.Priority priority) {
        return new NotificationRequest(null, eventId, channel, recipient,
                subject, message, priority, null, null, 0, null, null);
    }
}