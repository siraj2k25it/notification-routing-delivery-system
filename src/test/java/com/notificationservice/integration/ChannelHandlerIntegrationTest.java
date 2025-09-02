package com.notificationservice.integration;

import com.notificationservice.channel.EmailChannelHandler;
import com.notificationservice.channel.NotificationChannelHandler;
import com.notificationservice.channel.SmsChannelHandler;
import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import com.notificationservice.model.NotificationStatus;
import com.notificationservice.service.NotificationService;
import com.notificationservice.storage.NotificationStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for channel handlers and their interaction with the notification service.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.com.notificationservice=DEBUG"
})
class ChannelHandlerIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationStorage storage;

    @Autowired
    private List<NotificationChannelHandler> channelHandlers;

    @Autowired
    private EmailChannelHandler emailChannelHandler;

    @Autowired
    private SmsChannelHandler smsChannelHandler;

    @Test
    void testAllChannelHandlersAreRegistered() {
        // Then
        assertNotNull(channelHandlers);
        assertFalse(channelHandlers.isEmpty());
        
        // Should have at least EMAIL and SMS handlers
        assertTrue(channelHandlers.size() >= 2);
        
        boolean hasEmail = channelHandlers.stream()
                .anyMatch(h -> h.getChannelType() == NotificationChannel.EMAIL);
        boolean hasSms = channelHandlers.stream()
                .anyMatch(h -> h.getChannelType() == NotificationChannel.SMS);
        
        assertTrue(hasEmail, "EMAIL channel handler not found");
        assertTrue(hasSms, "SMS channel handler not found");
    }

    @Test
    void testEmailChannelHandlerIntegration() throws Exception {
        // Given
        Event event = new Event("USER_REGISTERED", "test@example.com", 
                Map.of("name", "John Doe"));

        // When
        notificationService.processEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(event.eventId());
                    assertFalse(requests.isEmpty());

                    // Find email request
                    var emailRequest = requests.stream()
                            .filter(r -> r.channel() == NotificationChannel.EMAIL)
                            .findFirst();
                    
                    assertTrue(emailRequest.isPresent(), "Email request should exist");
                    
                    // Should be processed (either SENT or FAILED)
                    NotificationStatus status = emailRequest.get().status();
                    assertTrue(status == NotificationStatus.SENT || status == NotificationStatus.FAILED,
                            "Email request should be processed, was: " + status);
                });
    }

    @Test
    void testSmsChannelHandlerIntegration() throws Exception {
        // Given
        Event event = new Event("PAYMENT_COMPLETED", "+971-50-789-4567", 
                Map.of("amount", "875.50", "transactionId", "TXN456"));

        // When
        notificationService.processEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(event.eventId());
                    assertFalse(requests.isEmpty());

                    // Find SMS request
                    var smsRequest = requests.stream()
                            .filter(r -> r.channel() == NotificationChannel.SMS)
                            .findFirst();
                    
                    assertTrue(smsRequest.isPresent(), "SMS request should exist");
                    
                    // Should be processed
                    NotificationStatus status = smsRequest.get().status();
                    assertTrue(status == NotificationStatus.SENT || status == NotificationStatus.FAILED,
                            "SMS request should be processed, was: " + status);
                });
    }

    @Test
    void testMultiChannelDelivery() throws Exception {
        // Given - Event that routes to both EMAIL and SMS
        Event event = new Event("SECURITY_ALERT", "user@example.com", 
                Map.of("alertType", "Suspicious login", "timestamp", "2024-01-01T10:00:00"));

        // When
        notificationService.processEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(event.eventId());
                    assertFalse(requests.isEmpty());

                    // Should have both EMAIL and SMS requests
                    boolean hasEmail = requests.stream()
                            .anyMatch(r -> r.channel() == NotificationChannel.EMAIL);
                    boolean hasSms = requests.stream()
                            .anyMatch(r -> r.channel() == NotificationChannel.SMS);
                    
                    assertTrue(hasEmail, "Should have EMAIL notification");
                    assertTrue(hasSms, "Should have SMS notification");
                    
                    // All should be processed
                    long processedCount = requests.stream()
                            .filter(r -> r.status() != NotificationStatus.PENDING)
                            .count();
                    assertEquals(requests.size(), processedCount, 
                            "All notifications should be processed");
                });
    }

    @Test
    void testChannelHandlerStatus() {
        // When & Then
        assertNotNull(emailChannelHandler.getStatus());
        assertNotNull(smsChannelHandler.getStatus());
        
        assertTrue(emailChannelHandler.getStatus().contains("Email"));
        assertTrue(smsChannelHandler.getStatus().contains("SMS"));
    }

    @Test
    void testChannelHandlerDirectSend() throws Exception {
        // Given
        NotificationRequest emailRequest = NotificationRequest.create(
                "test-event", NotificationChannel.EMAIL, "test@example.com",
                "Test Subject", "Test message body", Event.Priority.MEDIUM);
        
        NotificationRequest smsRequest = NotificationRequest.create(
                "test-event", NotificationChannel.SMS, "+1234567890",
                "Test Subject", "Test SMS message", Event.Priority.MEDIUM);

        // When & Then - Test email handler
        try {
            boolean emailResult = emailChannelHandler.send(emailRequest);
            // Should return true or throw exception (due to random failure simulation)
            assertTrue(emailResult);
        } catch (Exception e) {
            // Expected due to simulated failures
            assertNotNull(e.getMessage());
        }

        // Test SMS handler
        try {
            boolean smsResult = smsChannelHandler.send(smsRequest);
            assertTrue(smsResult);
        } catch (Exception e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testChannelHandlerFailureHandling() throws Exception {
        // Given - Create multiple requests to test failure scenarios
        Event event = new Event("USER_REGISTERED", "failtest@example.com", 
                Map.of("name", "Fail Test"));

        // When
        notificationService.processEvent(event);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(event.eventId());
                    assertFalse(requests.isEmpty());

                    // Some requests might fail due to simulated failures
                    long failedCount = requests.stream()
                            .filter(r -> r.status() == NotificationStatus.FAILED)
                            .count();
                    
                    // Check that failed requests have failure reasons
                    requests.stream()
                            .filter(r -> r.status() == NotificationStatus.FAILED)
                            .forEach(r -> {
                                assertNotNull(r.failureReason(), 
                                        "Failed request should have failure reason");
                                assertFalse(r.failureReason().trim().isEmpty(), 
                                        "Failure reason should not be empty");
                            });
                });
    }

    @Test
    void testChannelHandlerPerformance() throws Exception {
        // Given - Multiple events to test performance
        Event[] events = {
                new Event("USER_REGISTERED", "perf1@example.com", Map.of("name", "User 1")),
                new Event("USER_REGISTERED", "perf2@example.com", Map.of("name", "User 2")),
                new Event("USER_REGISTERED", "perf3@example.com", Map.of("name", "User 3")),
                new Event("USER_REGISTERED", "perf4@example.com", Map.of("name", "User 4")),
                new Event("USER_REGISTERED", "perf5@example.com", Map.of("name", "User 5"))
        };

        long startTime = System.currentTimeMillis();

        // When - Process all events
        for (Event event : events) {
            notificationService.processEvent(event);
        }

        // Then - All should be processed within reasonable time
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    long totalRequests = 0;
                    for (Event event : events) {
                        var requests = storage.getNotificationRequestsByEventId(event.eventId());
                        totalRequests += requests.size();
                    }
                    
                    // Each event should generate at least 2 requests (EMAIL + SMS)
                    assertTrue(totalRequests >= events.length * 2, 
                            "Expected at least " + (events.length * 2) + " requests, got " + totalRequests);
                    
                    // All should be processed
                    long processedRequests = 0;
                    for (Event event : events) {
                        var requests = storage.getNotificationRequestsByEventId(event.eventId());
                        processedRequests += requests.stream()
                                .filter(r -> r.status() != NotificationStatus.PENDING)
                                .count();
                    }
                    
                    assertEquals(totalRequests, processedRequests, 
                            "All requests should be processed");
                });

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // Should complete within reasonable time (15 seconds max)
        assertTrue(totalTime < 15000, 
                "Performance test took too long: " + totalTime + "ms");
    }

    @Test
    void testUnsupportedChannelHandling() throws Exception {
        // Given - Manually create a request with unsupported channel
        NotificationRequest webhookRequest = NotificationRequest.create(
                "test-event", NotificationChannel.WEBHOOK, "http://example.com/webhook",
                "Test Subject", "Test message", Event.Priority.MEDIUM);

        // When
        notificationService.deliverNotification(webhookRequest);

        // Then - Should be marked as failed due to no handler
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var storedRequest = storage.getNotificationRequest(webhookRequest.requestId());
                    assertNotNull(storedRequest);
                    assertEquals(NotificationStatus.FAILED, storedRequest.status());
                    assertTrue(storedRequest.failureReason().contains("No handler available"));
                });
    }

    @Test
    void testChannelHandlerRecovery() throws Exception {
        // Given - Process multiple events to test recovery after failures
        Event[] events = new Event[10];
        for (int i = 0; i < 10; i++) {
            events[i] = new Event("USER_REGISTERED", "recovery" + i + "@example.com", 
                    Map.of("name", "Recovery User " + i));
        }

        // When - Process all events
        for (Event event : events) {
            notificationService.processEvent(event);
        }

        // Then - System should continue functioning despite some failures
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    long totalEvents = storage.getEventCount();
                    assertTrue(totalEvents >= 10, "All events should be stored");
                    
                    long totalRequests = storage.getNotificationRequestCount();
                    assertTrue(totalRequests > 0, "Should have notification requests");
                    
                    // Should have both successful and potentially failed deliveries
                    long successfulDeliveries = storage.getSuccessfulDeliveryCount();
                    long failedDeliveries = storage.getFailedDeliveryCount();
                    
                    assertTrue(successfulDeliveries + failedDeliveries > 0, 
                            "Should have processed deliveries");
                });
    }

    @Test
    void testChannelHandlerConfiguration() {
        // When & Then - Test that handlers are properly configured
        var serviceStats = notificationService.getServiceStats();
        
        @SuppressWarnings("unchecked")
        var availableChannels = (Iterable<NotificationChannel>) serviceStats.get("availableChannels");
        
        assertNotNull(availableChannels);
        
        boolean hasEmailChannel = false;
        boolean hasSmsChannel = false;
        
        for (NotificationChannel channel : availableChannels) {
            if (channel == NotificationChannel.EMAIL) hasEmailChannel = true;
            if (channel == NotificationChannel.SMS) hasSmsChannel = true;
        }
        
        assertTrue(hasEmailChannel, "EMAIL channel should be available");
        assertTrue(hasSmsChannel, "SMS channel should be available");
    }
}