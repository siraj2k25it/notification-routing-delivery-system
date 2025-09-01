package com.notificationservice.model;

import java.time.LocalDateTime;

/**
 * Record representing a retry attempt for a failed notification.
 */
public record RetryAttempt(
        LocalDateTime timestamp,
        String reason,
        boolean successful
) {

    public static RetryAttempt failed(String reason) {
        return new RetryAttempt(LocalDateTime.now(), reason, false);
    }

    public static RetryAttempt succeeded() {
        return new RetryAttempt(LocalDateTime.now(), "Successfully delivered", true);
    }

    public static RetryAttempt of(String reason, boolean wasSuccessful) {
        return new RetryAttempt(LocalDateTime.now(), reason, wasSuccessful);
    }
}