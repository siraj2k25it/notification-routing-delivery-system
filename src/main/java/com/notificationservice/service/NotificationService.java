package com.notificationservice.service;

import com.notificationservice.channel.NotificationChannelHandler;
import com.notificationservice.model.*;
import com.notificationservice.routing.RoutingEngine;
import com.notificationservice.storage.NotificationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Core notification service that orchestrates event processing and delivery.
 * Handles async processing, routing, and delegation to appropriate channels.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final RoutingEngine routingEngine;
    private final NotificationStorage storage;
    private final Map<NotificationChannel, NotificationChannelHandler> channelHandlers;

    public NotificationService(RoutingEngine routingEngine,
                               NotificationStorage storage,
                               List<NotificationChannelHandler> handlers) {
        this.routingEngine = routingEngine;
        this.storage = storage;
        this.channelHandlers = handlers.stream()
                .collect(Collectors.toMap(
                        NotificationChannelHandler::getChannelType,
                        Function.identity()
                ));

        log.info("🚀 NotificationService initialized with {} channel handlers: {}",
                channelHandlers.size(), channelHandlers.keySet());
    }

    /**
     * Process an incoming event asynchronously.
     * Routes the event and creates notification requests for appropriate channels.
     */
    @Async
    public CompletableFuture<String> processEvent(Event event) {
        log.info("📨 Processing event: {} of type: {} for recipient: {}",
                event.eventId(), event.eventType(), event.recipient());

        try {
            // Store the event
            storage.saveEvent(event);
            log.debug("💾 Event stored: {}", event.eventId());

            // Route the event to determine notification channels
            List<NotificationRequest> requests = routingEngine.routeEvent(event);

            if (requests.isEmpty()) {
                log.warn("⚠️ No notification requests generated for event: {} of type: {}",
                        event.eventId(), event.eventType());
                return CompletableFuture.completedFuture("No matching routing rules");
            }

            // Process each notification request
            for (NotificationRequest request : requests) {
                storage.saveNotificationRequest(request);
                deliverNotification(request);
            }

            log.info("✅ Successfully processed {} notification requests for event: {}",
                    requests.size(), event.eventId());

            return CompletableFuture.completedFuture("Event processed successfully");

        } catch (Exception e) {
            log.error("❌ Error processing event: {}", event.eventId(), e);
            return CompletableFuture.completedFuture("Error: " + e.getMessage());
        }
    }

    /**
     * Deliver a notification through the appropriate channel handler.
     */
    @Async
    public void deliverNotification(NotificationRequest request) {
        log.debug("🚚 Attempting to deliver notification: {} via {}",
                request.requestId(), request.channel());

        NotificationChannelHandler handler = channelHandlers.get(request.channel());
        if (handler == null) {
            log.error("❌ No handler found for channel: {}", request.channel());
            var failedRequest = request.withFailure("No handler available for channel: " + request.channel());
            storage.updateNotificationRequest(failedRequest);
            return;
        }

        try {
            boolean success = handler.send(request);
            NotificationRequest updatedRequest;

            if (success) {
                updatedRequest = request.withStatus(NotificationStatus.SENT);
                log.info("✅ Successfully delivered notification: {} via {}",
                        request.requestId(), request.channel());
            } else {
                updatedRequest = request.withFailure("Handler returned false");
                log.warn("⚠️ Handler returned false for notification: {}", request.requestId());
            }

            storage.updateNotificationRequest(updatedRequest);

        } catch (Exception e) {
            log.warn("❌ Failed to deliver notification: {} via {} - {}",
                    request.requestId(), request.channel(), e.getMessage());

            var failedRequest = request.withFailure(e.getMessage());
            storage.updateNotificationRequest(failedRequest);

            // TODO: Schedule retry (will implement retry service next)
            log.debug("🔄 Retry scheduling not yet implemented for: {}", request.requestId());
        }
    }

    /**
     * Get delivery status for a specific event.
     */
    public DeliveryStatus getDeliveryStatus(String eventId) {
        Event event = storage.getEvent(eventId);
        if (event == null) {
            return null;
        }

        List<NotificationRequest> requests = storage.getNotificationRequestsByEventId(eventId);
        if (requests.isEmpty()) {
            return DeliveryStatus.forEvent(eventId, NotificationStatus.PENDING);
        }

        // Return status for the first request (could be enhanced to return all)
        NotificationRequest request = requests.get(0);
        return DeliveryStatus.fromRequest(request, List.of());
    }

    /**
     * Get all delivery statuses for a specific event (all channels).
     */
    public List<DeliveryStatus> getAllDeliveryStatuses(String eventId) {
        Event event = storage.getEvent(eventId);
        if (event == null) {
            return List.of();
        }

        List<NotificationRequest> requests = storage.getNotificationRequestsByEventId(eventId);
        return requests.stream()
                .map(request -> DeliveryStatus.fromRequest(request, List.of()))
                .toList();
    }

    /**
     * Get all failed deliveries.
     */
    public List<DeliveryStatus> getFailedDeliveries() {
        return storage.getFailedNotificationRequests().stream()
                .map(request -> DeliveryStatus.fromRequest(request, List.of()))
                .toList();
    }

    /**
     * Get service statistics.
     */
    public Map<String, Object> getServiceStats() {
        return Map.of(
                "eventsProcessed", storage.getEventCount(),
                "notificationsSent", storage.getSuccessfulDeliveryCount(),
                "failedDeliveries", storage.getFailedDeliveryCount(),
                "deadLetterCount", storage.getDeadLetterCount(),
                "availableChannels", channelHandlers.keySet(),
                "routingRules", routingEngine.getRuleCount()
        );
    }

    /**
     * Get detailed service health information.
     */
    public Map<String, Object> getHealthInfo() {
        Map<String, Object> health = Map.of(
                "status", "healthy",
                "channelsAvailable", channelHandlers.size(),
                "routingRulesActive", routingEngine.getRuleCount(),
                "storageType", storage.getClass().getSimpleName()
        );

        // Add channel status
        Map<String, String> channelStatus = channelHandlers.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        entry -> entry.getValue().getStatus()
                ));

        return Map.of(
                "service", health,
                "channels", channelStatus,
                "statistics", getServiceStats()
        );
    }
}