package com.notificationservice.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Event record representing an incoming notification event.
 * Uses Java 21 records for immutable data structure.
 */
public record Event(
        String eventId,

        @NotBlank(message = "Event type is required")
        String eventType,

        Map<String, Object> payload,

        @NotBlank(message = "Recipient is required")
        String recipient,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime timestamp,

        @NotNull
        Priority priority
) {

    // Constructor with defaults
    public Event {
        if (eventId == null) {
            eventId = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (priority == null) {
            priority = Priority.MEDIUM;
        }
    }

    // Convenience constructor
    public Event(String eventType, String recipient, Map<String, Object> payload) {
        this(null, eventType, payload, recipient, null, Priority.MEDIUM);
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}