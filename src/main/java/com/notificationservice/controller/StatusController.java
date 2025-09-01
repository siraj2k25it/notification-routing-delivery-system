package com.notificationservice.controller;

import com.notificationservice.model.DeliveryStatus;
import com.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for monitoring notification delivery status.
 * Provides endpoints for checking delivery status and failed notifications.
 */
@RestController
@RequestMapping("/api/v1/status")
@Tag(name = "Status Monitoring", description = "APIs for monitoring notification delivery status")
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    private final NotificationService notificationService;

    public StatusController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/delivery/{eventId}")
    @Operation(summary = "Get delivery status", description = "Get the delivery status for a specific event")
    @ApiResponse(responseCode = "200", description = "Delivery status found")
    @ApiResponse(responseCode = "404", description = "Event not found")
    public ResponseEntity<DeliveryStatus> getDeliveryStatus(@PathVariable String eventId) {
        log.debug("🔍 Getting delivery status for event: {}", eventId);

        DeliveryStatus status = notificationService.getDeliveryStatus(eventId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/failed")
    @Operation(summary = "Get failed deliveries", description = "Get all failed notification deliveries")
    public ResponseEntity<List<DeliveryStatus>> getFailedDeliveries() {
        log.debug("🔍 Getting failed deliveries");

        List<DeliveryStatus> failedDeliveries = notificationService.getFailedDeliveries();
        return ResponseEntity.ok(failedDeliveries);
    }

    @GetMapping("/health")
    @Operation(summary = "System health check", description = "Get system health and statistics")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> healthInfo = notificationService.getHealthInfo();
        return ResponseEntity.ok(healthInfo);
    }

    @GetMapping("/metrics")
    @Operation(summary = "Get system metrics", description = "Get detailed system metrics and statistics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> stats = notificationService.getServiceStats();
        return ResponseEntity.ok(stats);
    }
}