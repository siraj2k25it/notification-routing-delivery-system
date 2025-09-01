package com.notificationservice.controller;

import com.notificationservice.model.Event;
import com.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for handling notification events.
 * Provides endpoints for submitting events for processing.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Event Management", description = "APIs for publishing and managing notification events")
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final NotificationService notificationService;

    public EventController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/events")
    @Operation(summary = "Publish an event", description = "Submit an event for processing and notification routing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Event accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid event data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> publishEvent(@Valid @RequestBody Event event) {
        log.info("📥 Received event: {} of type: {} for recipient: {}",
                event.eventId(), event.eventType(), event.recipient());

        try {
            // Process event asynchronously using the notification service
            notificationService.processEvent(event);

            return ResponseEntity.accepted()
                    .body(Map.of(
                            "eventId", event.eventId(),
                            "status", "accepted",
                            "message", "Event accepted for processing",
                            "timestamp", event.timestamp()
                    ));

        } catch (Exception e) {
            log.error("❌ Error processing event: {}", event.eventId(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "eventId", event.eventId(),
                            "error", "Internal server error",
                            "message", e.getMessage()
                    ));
        }
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get event details", description = "Retrieve details of a specific event")
    public ResponseEntity<Map<String, Object>> getEvent(@PathVariable String eventId) {
        log.debug("🔍 Getting event details for: {}", eventId);

        var deliveryStatus = notificationService.getDeliveryStatus(eventId);
        if (deliveryStatus == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "eventId", eventId,
                "status", deliveryStatus.status(),
                "channel", deliveryStatus.channel() != null ? deliveryStatus.channel() : "N/A",
                "retryCount", deliveryStatus.retryCount(),
                "lastAttempt", deliveryStatus.lastAttemptAt() != null ? deliveryStatus.lastAttemptAt() : "N/A"
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "Simple health check", description = "Basic health check endpoint")
    public ResponseEntity<Map<String, Object>> simpleHealthCheck() {
        var healthInfo = notificationService.getHealthInfo();
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "notification-routing-delivery-system",
                "timestamp", System.currentTimeMillis(),
                "details", healthInfo
        ));
    }
}