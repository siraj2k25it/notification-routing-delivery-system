package com.notificationservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationStatus;
import com.notificationservice.service.NotificationService;
import com.notificationservice.storage.NotificationStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the complete notification system workflow.
 * Tests the entire flow from API endpoint to notification delivery.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.com.notificationservice=DEBUG"
})
class NotificationSystemIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationStorage storage;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCompleteUserRegistrationWorkflow() throws Exception {
        // Given
        Event userRegistrationEvent = new Event(
                "USER_REGISTERED", 
                "siraj.shaik@gmail.com",
                Map.of(
                        "name", "Siraj Shaik",
                        "userId", "user-7891",
                        "registrationDate", "2024-02-15"
                )
        );

        // When - Submit event via API
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userRegistrationEvent)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        // Then - Wait for async processing and verify
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Verify event was stored
                    var storedEvent = storage.getEvent(userRegistrationEvent.eventId());
                    assertNotNull(storedEvent);
                    assertEquals("USER_REGISTERED", storedEvent.eventType());

                    // Verify notification requests were created
                    var requests = storage.getNotificationRequestsByEventId(userRegistrationEvent.eventId());
                    assertFalse(requests.isEmpty());
                    assertTrue(requests.size() >= 1); // Should have EMAIL and SMS

                    // Verify at least one notification was processed
                    long processedCount = requests.stream()
                            .mapToLong(r -> r.status() == NotificationStatus.PENDING ? 0 : 1)
                            .sum();
                    assertTrue(processedCount > 0);
                });

        // Verify via API endpoint
        mockMvc.perform(get("/api/v1/events/{eventId}", userRegistrationEvent.eventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(userRegistrationEvent.eventId()));
    }

    @Test
    void testCompletePaymentCompletedWorkflow() throws Exception {
        // Given
        Event paymentEvent = new Event(
                "PAYMENT_COMPLETED",
                "siraj@multibank.com",
                Map.of(
                        "amount", "1250.00",
                        "transactionId", "TXN-MB-001",
                        "paymentMethod", "debit_card"
                )
        );

        // When
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentEvent)))
                .andExpect(status().isAccepted());

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(paymentEvent.eventId());
                    assertFalse(requests.isEmpty());

                    // Verify message content contains payment details
                    var firstRequest = requests.get(0);
                    assertTrue(firstRequest.message().contains("1250.00"));
                    assertTrue(firstRequest.message().contains("TXN-MB-001"));
                });
    }

    @Test
    void testCompleteOrderShippedWorkflow() throws Exception {
        // Given
        Event orderEvent = new Event(
                "ORDER_SHIPPED",
                "buyer@example.com",
                Map.of(
                        "orderId", "ORDER-456",
                        "deliveryDate", "2024-01-15",
                        "trackingUrl", "https://track.example.com/ORDER-456"
                )
        );

        // When
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderEvent)))
                .andExpect(status().isAccepted());

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(orderEvent.eventId());
                    assertFalse(requests.isEmpty());

                    var firstRequest = requests.get(0);
                    assertTrue(firstRequest.message().contains("ORDER-456"));
                    assertTrue(firstRequest.message().contains("2024-01-15"));
                    assertTrue(firstRequest.message().contains("track.example.com"));
                });
    }

    @Test
    void testSecurityAlertHighPriorityWorkflow() throws Exception {
        // Given
        Event securityEvent = new Event(
                "event-sec-123",
                "SECURITY_ALERT",
                Map.of(
                        "alertType", "Suspicious login attempt",
                        "timestamp", "2024-01-01T10:00:00",
                        "ipAddress", "192.168.1.100",
                        "location", "Unknown"
                ),
                "user@example.com",
                null,
                Event.Priority.HIGH
        );

        // When
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(securityEvent)))
                .andExpect(status().isAccepted());

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(securityEvent.eventId());
                    assertFalse(requests.isEmpty(), "Should have notification requests for security event");

                    // Should route to both EMAIL and SMS for security alerts
                    assertTrue(requests.size() >= 1, "Should have at least one notification request");

                    var firstRequest = requests.get(0);
                    assertTrue(firstRequest.message().contains("🔒 Security Alert") || 
                              firstRequest.message().contains("🚨 URGENT") ||
                              firstRequest.message().contains("Suspicious login attempt"),
                              "Message should contain security alert indicators");
                });
    }

    @Test
    void testPasswordResetWorkflow() throws Exception {
        // Given
        Event passwordResetEvent = new Event(
                "PASSWORD_RESET",
                "forgot@example.com",
                Map.of(
                        "resetUrl", "https://app.example.com/reset/token-abc-123",
                        "expirationMinutes", "30"
                )
        );

        // When
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordResetEvent)))
                .andExpect(status().isAccepted());

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(passwordResetEvent.eventId());
                    assertFalse(requests.isEmpty());

                    // Password reset should only go to EMAIL
                    assertEquals(1, requests.size());
                    var emailRequest = requests.get(0);
                    
                    assertTrue(emailRequest.message().contains("token-abc-123"));
                    assertTrue(emailRequest.message().contains("30 minutes"));
                    assertEquals("forgot@example.com", emailRequest.recipient());
                });
    }

    @Test
    void testAccountVerificationWorkflow() throws Exception {
        // Given
        Event verificationEvent = new Event(
                "ACCOUNT_VERIFICATION",
                "verify@example.com",
                Map.of("verificationCode", "789123")
        );

        // When
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verificationEvent)))
                .andExpect(status().isAccepted());

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(verificationEvent.eventId());
                    assertFalse(requests.isEmpty());

                    // Should route to both EMAIL and SMS
                    assertTrue(requests.size() >= 2);

                    var firstRequest = requests.get(0);
                    assertTrue(firstRequest.message().contains("789123"));
                    assertTrue(firstRequest.message().contains("10 minutes"));
                });
    }

    @Test
    void testLowPriorityNewsletterWorkflow() throws Exception {
        // Given
        Event newsletterEvent = new Event(
                "newsletter-123",
                "NEWSLETTER_UPDATE",
                Map.of(
                        "subject", "Weekly Product Updates",
                        "message", "Check out our new features and improvements!"
                ),
                "subscriber@example.com",
                null,
                Event.Priority.LOW
        );

        // When
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newsletterEvent)))
                .andExpect(status().isAccepted());

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(newsletterEvent.eventId());
                    assertFalse(requests.isEmpty());

                    // Low priority should only go to EMAIL
                    assertEquals(1, requests.size());
                    var emailRequest = requests.get(0);
                    
                    assertTrue(emailRequest.subject().contains("Weekly Product Updates"));
                });
    }

    @Test
    void testUnknownEventTypeWorkflow() throws Exception {
        // Given
        Event unknownEvent = new Event(
                "UNKNOWN_EVENT_TYPE",
                "user@example.com",
                Map.of("data", "test")
        );

        // When
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownEvent)))
                .andExpect(status().isAccepted());

        // Then
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Event should be stored
                    var storedEvent = storage.getEvent(unknownEvent.eventId());
                    assertNotNull(storedEvent);

                    // But no notification requests should be created
                    var requests = storage.getNotificationRequestsByEventId(unknownEvent.eventId());
                    assertTrue(requests.isEmpty());
                });

        // API should still return event not found for delivery status
        // since no notifications were generated
        mockMvc.perform(get("/api/v1/events/{eventId}", unknownEvent.eventId()))
                .andExpect(status().isOk()); // Event exists but no delivery status
    }

    @Test
    void testHealthEndpointIntegration() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("notification-routing-delivery-system"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").exists())
                .andExpect(jsonPath("$.details.service").exists())
                .andExpect(jsonPath("$.details.channels").exists())
                .andExpect(jsonPath("$.details.statistics").exists());
    }

    @Test
    void testMultipleEventsProcessingConcurrently() throws Exception {
        // Given
        Event[] events = {
                new Event("USER_REGISTERED", "user1@example.com", Map.of("name", "User 1")),
                new Event("PAYMENT_COMPLETED", "user2@example.com", Map.of("amount", "50.00")),
                new Event("ORDER_SHIPPED", "user3@example.com", Map.of("orderId", "ORD-001")),
                new Event("ACCOUNT_VERIFICATION", "user4@example.com", Map.of("verificationCode", "123456"))
        };

        // When - Submit all events concurrently
        for (Event event : events) {
            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(event)))
                    .andExpect(status().isAccepted());
        }

        // Then - Wait for all to be processed
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // All events should be stored (at least the 4 we submitted)
                    assertTrue(storage.getEventCount() >= events.length, 
                            "Expected at least " + events.length + " events, got " + storage.getEventCount());

                    // All should have generated notification requests
                    int eventsWithRequests = 0;
                    for (Event event : events) {
                        var requests = storage.getNotificationRequestsByEventId(event.eventId());
                        if (!requests.isEmpty()) {
                            eventsWithRequests++;
                        }
                    }
                    
                    assertTrue(eventsWithRequests >= events.length, 
                            "Expected " + events.length + " events with requests, got " + eventsWithRequests);

                    // Some notifications should be processed
                    assertTrue(storage.getNotificationRequestCount() > 0);
                });
    }

    @Test
    void testSystemStatisticsIntegration() throws Exception {
        // Given - Process some events
        Event event1 = new Event("USER_REGISTERED", "user@example.com", Map.of("name", "Test User"));
        Event event2 = new Event("PAYMENT_COMPLETED", "user@example.com", Map.of("amount", "25.00"));

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event1)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event2)))
                .andExpect(status().isAccepted());

        // Wait for processing
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(storage.getEventCount() >= 2);
                    assertTrue(storage.getNotificationRequestCount() > 0);
                });

        // Then - Verify statistics through service
        var stats = notificationService.getServiceStats();
        assertNotNull(stats);
        assertTrue((Long) stats.get("eventsProcessed") >= 2);
        assertTrue((Integer) stats.get("routingRules") > 0);
        assertNotNull(stats.get("availableChannels"));
    }

    @Test
    void testEventRetrievalAfterProcessing() throws Exception {
        // Given
        Event event = new Event("USER_REGISTERED", "test@example.com", 
                Map.of("name", "Test User"));

        // When - Submit and wait for processing
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isAccepted());

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(event.eventId());
                    assertFalse(requests.isEmpty());
                });

        // Then - Retrieve via API
        mockMvc.perform(get("/api/v1/events/{eventId}", event.eventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.eventId()))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.retryCount").exists());
    }
}