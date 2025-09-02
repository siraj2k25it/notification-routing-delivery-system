package com.notificationservice.integration;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.routing.RoutingEngine;
import com.notificationservice.routing.RoutingRule;
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
 * Integration tests for routing engine functionality within the complete system.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.com.notificationservice=DEBUG"
})
class RoutingEngineIntegrationTest {

    @Autowired
    private RoutingEngine routingEngine;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationStorage storage;

    @Test
    void testRoutingEngineInitialization() {
        // Then
        assertNotNull(routingEngine);
        assertTrue(routingEngine.getRuleCount() > 0);
        
        var rules = routingEngine.getRules();
        assertFalse(rules.isEmpty());
        
        // Should have rules for common event types
        boolean hasUserRegisteredRule = rules.stream()
                .anyMatch(rule -> rule.name().contains("User Registration"));
        boolean hasPaymentRule = rules.stream()
                .anyMatch(rule -> rule.name().contains("Payment"));
        boolean hasSecurityRule = rules.stream()
                .anyMatch(rule -> rule.name().contains("Security"));
        
        assertTrue(hasUserRegisteredRule, "Should have user registration rule");
        assertTrue(hasPaymentRule, "Should have payment rule");
        assertTrue(hasSecurityRule, "Should have security rule");
    }

    @Test
    void testDynamicRuleAddition() throws Exception {
        // Given
        int initialRuleCount = routingEngine.getRuleCount();
        
        RoutingRule customRule = RoutingRule.forEventType(
                "CUSTOM_INTEGRATION_TEST",
                "Custom Integration Test Rule",
                List.of(NotificationChannel.EMAIL),
                "Custom integration test message for {name}",
                "Custom Integration Test"
        );

        // When - Add rule and test it
        routingEngine.addRule(customRule);

        // Then - Rule count should increase
        assertEquals(initialRuleCount + 1, routingEngine.getRuleCount());

        // Test the new rule works end-to-end
        Event customEvent = new Event("CUSTOM_INTEGRATION_TEST", "test@example.com", 
                Map.of("name", "Test User"));

        notificationService.processEvent(customEvent);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(customEvent.eventId());
                    assertFalse(requests.isEmpty());
                    
                    var emailRequest = requests.stream()
                            .filter(r -> r.channel() == NotificationChannel.EMAIL)
                            .findFirst();
                    
                    assertTrue(emailRequest.isPresent());
                    assertTrue(emailRequest.get().message().contains("Test User"));
                    assertTrue(emailRequest.get().message().contains("Custom integration test"));
                });
    }

    @Test
    void testPriorityBasedRouting() throws Exception {
        // Given - Events with different priorities
        Event lowPriorityEvent = new Event("low-priority-event", "LOW_PRIORITY_UPDATE",
                Map.of("subject", "Newsletter", "message", "Monthly update"), 
                "user@example.com", null, Event.Priority.LOW);
        
        Event highPriorityEvent = new Event("high-priority-event", "CRITICAL_ALERT",
                Map.of("message", "System failure detected"), 
                "admin@example.com", null, Event.Priority.HIGH);

        // When
        notificationService.processEvent(lowPriorityEvent);
        notificationService.processEvent(highPriorityEvent);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Low priority should only route to EMAIL
                    var lowPriorityRequests = storage.getNotificationRequestsByEventId(lowPriorityEvent.eventId());
                    assertFalse(lowPriorityRequests.isEmpty());
                    
                    boolean onlyEmail = lowPriorityRequests.stream()
                            .allMatch(r -> r.channel() == NotificationChannel.EMAIL);
                    assertTrue(onlyEmail, "Low priority should only use EMAIL channel");

                    // High priority should route to multiple channels
                    var highPriorityRequests = storage.getNotificationRequestsByEventId(highPriorityEvent.eventId());
                    assertFalse(highPriorityRequests.isEmpty());
                    
                    boolean hasMultipleChannels = highPriorityRequests.size() > 1;
                    assertTrue(hasMultipleChannels, "High priority should use multiple channels");
                });
    }

    @Test
    void testRoutingRulePriorityOrdering() throws Exception {
        // Given - Event that could match multiple rules (SECURITY_ALERT with HIGH priority)
        Event securityEvent = new Event("security-high-priority", "SECURITY_ALERT",
                Map.of("alertType", "Brute force attack", "timestamp", "2024-01-01T10:00:00"),
                "admin@example.com", null, Event.Priority.HIGH);

        // When
        notificationService.processEvent(securityEvent);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(securityEvent.eventId());
                    assertFalse(requests.isEmpty(), "Should have notification requests");

                    // Should use the highest priority rule's template
                    var firstRequest = requests.get(0);
                    
                    // Security Alert rule should take precedence (has higher priority)
                    boolean hasSecurityTemplate = firstRequest.message().contains("🔒 Security Alert") ||
                                                  firstRequest.message().contains("🚨 URGENT") ||
                                                  firstRequest.message().contains("Brute force attack");
                    assertTrue(hasSecurityTemplate, 
                            "Should use high-priority rule template. Message was: " + firstRequest.message());
                });
    }

    @Test
    void testTemplateProcessingIntegration() throws Exception {
        // Given - Event with complex payload
        Event complexEvent = new Event("USER_REGISTERED", "john.doe@example.com",
                Map.of(
                        "name", "John Doe",
                        "userId", "user-12345",
                        "registrationDate", "2024-01-01",
                        "referralCode", "REF-ABC123",
                        "accountType", "premium"
                ));

        // When
        notificationService.processEvent(complexEvent);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(complexEvent.eventId());
                    assertFalse(requests.isEmpty());

                    var firstRequest = requests.get(0);
                    
                    // All placeholders should be replaced
                    assertFalse(firstRequest.message().contains("{name}"), 
                            "Name placeholder should be replaced");
                    assertTrue(firstRequest.message().contains("John Doe"), 
                            "Should contain actual name");
                    
                    assertFalse(firstRequest.message().contains("{recipient}"), 
                            "Recipient placeholder should be replaced");
                    assertTrue(firstRequest.message().contains("john.doe@example.com") ||
                              firstRequest.subject().contains("john.doe@example.com") ||
                              firstRequest.recipient().contains("john.doe@example.com"), 
                            "Should contain recipient email somewhere in the request");
                    
                    // Custom payload values should be replaced if used in templates
                    if (firstRequest.message().contains("user-12345")) {
                        assertFalse(firstRequest.message().contains("{userId}"));
                    }
                });
    }

    @Test
    void testMultipleEventTypeRouting() throws Exception {
        // Given - Different event types that should route differently
        Event userEvent = new Event("USER_REGISTERED", "user@example.com", 
                Map.of("name", "New User"));
        Event paymentEvent = new Event("PAYMENT_COMPLETED", "customer@example.com", 
                Map.of("amount", "99.99", "transactionId", "TXN-123"));
        Event passwordEvent = new Event("PASSWORD_RESET", "forgot@example.com", 
                Map.of("resetUrl", "https://example.com/reset"));

        // When
        notificationService.processEvent(userEvent);
        notificationService.processEvent(paymentEvent);
        notificationService.processEvent(passwordEvent);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // User registration: EMAIL + SMS
                    var userRequests = storage.getNotificationRequestsByEventId(userEvent.eventId());
                    assertTrue(userRequests.size() >= 2, "User registration should route to multiple channels");
                    
                    // Payment completed: EMAIL + SMS
                    var paymentRequests = storage.getNotificationRequestsByEventId(paymentEvent.eventId());
                    assertTrue(paymentRequests.size() >= 2, "Payment completion should route to multiple channels");
                    
                    // Password reset: EMAIL only
                    var passwordRequests = storage.getNotificationRequestsByEventId(passwordEvent.eventId());
                    assertEquals(1, passwordRequests.size(), "Password reset should only route to EMAIL");
                    assertEquals(NotificationChannel.EMAIL, passwordRequests.get(0).channel());
                });
    }

    @Test
    void testRoutingWithEmptyPayload() throws Exception {
        // Given - Event with no payload
        Event emptyPayloadEvent = new Event("USER_REGISTERED", "empty@example.com", null);

        // When
        notificationService.processEvent(emptyPayloadEvent);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var requests = storage.getNotificationRequestsByEventId(emptyPayloadEvent.eventId());
                    assertFalse(requests.isEmpty());
                    
                    var firstRequest = requests.get(0);
                    
                    // Should still work with empty payload, using default templates
                    assertNotNull(firstRequest.message());
                    assertFalse(firstRequest.message().trim().isEmpty());
                    
                    // Should replace basic placeholders even without payload
                    assertFalse(firstRequest.message().contains("{recipient}"));
                    assertFalse(firstRequest.message().contains("{eventType}"));
                });
    }

    @Test
    void testRoutingStatistics() {
        // When
        var stats = routingEngine.getRoutingStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalRules"));
        assertTrue(stats.containsKey("ruleNames"));
        assertTrue(stats.containsKey("channelCoverage"));
        
        assertTrue((Integer) stats.get("totalRules") > 0);
        
        @SuppressWarnings("unchecked")
        List<String> ruleNames = (List<String>) stats.get("ruleNames");
        assertFalse(ruleNames.isEmpty());
        
        @SuppressWarnings("unchecked")
        var channelCoverageObj = stats.get("channelCoverage");
        assertTrue(channelCoverageObj instanceof Iterable);
        
        // Convert to set for checking
        java.util.Set<NotificationChannel> channels;
        if (channelCoverageObj instanceof java.util.Set) {
            channels = (java.util.Set<NotificationChannel>) channelCoverageObj;
        } else {
            channels = new java.util.HashSet<>((java.util.Collection<NotificationChannel>) channelCoverageObj);
        }
        
        assertTrue(channels.contains(NotificationChannel.EMAIL));
        assertTrue(channels.contains(NotificationChannel.SMS));
    }

    @Test
    void testRoutingEngineWithServiceStatistics() throws Exception {
        // Given - Process some events to generate statistics
        Event[] events = {
                new Event("USER_REGISTERED", "stats1@example.com", Map.of("name", "User 1")),
                new Event("PAYMENT_COMPLETED", "stats2@example.com", Map.of("amount", "50.00")),
                new Event("ORDER_SHIPPED", "stats3@example.com", Map.of("orderId", "ORD-123"))
        };

        // When
        for (Event event : events) {
            notificationService.processEvent(event);
        }

        // Wait for processing
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(storage.getEventCount() >= 3);
                    assertTrue(storage.getNotificationRequestCount() > 0);
                });

        // Then - Verify routing statistics through service
        var serviceStats = notificationService.getServiceStats();
        assertEquals(routingEngine.getRuleCount(), serviceStats.get("routingRules"));
        
        var healthInfo = notificationService.getHealthInfo();
        @SuppressWarnings("unchecked")
        Map<String, Object> serviceInfo = (Map<String, Object>) healthInfo.get("service");
        assertEquals(routingEngine.getRuleCount(), serviceInfo.get("routingRulesActive"));
    }

    @Test
    void testNoMatchingRulesHandling() throws Exception {
        // Given - Event type that has no matching rules
        Event unknownEvent = new Event("COMPLETELY_UNKNOWN_EVENT_TYPE", "unknown@example.com", 
                Map.of("data", "test"));

        // When
        notificationService.processEvent(unknownEvent);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Event should be stored
                    var storedEvent = storage.getEvent(unknownEvent.eventId());
                    assertNotNull(storedEvent);

                    // But no notification requests should be generated
                    var requests = storage.getNotificationRequestsByEventId(unknownEvent.eventId());
                    assertTrue(requests.isEmpty(), "No requests should be generated for unknown event types");
                });
    }

    @Test
    void testRoutingEngineThreadSafety() throws Exception {
        // Given - Multiple threads processing events concurrently
        int threadCount = 5;
        int eventsPerThread = 10;
        
        Thread[] threads = new Thread[threadCount];
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int e = 0; e < eventsPerThread; e++) {
                    Event event = new Event("USER_REGISTERED", 
                            "thread" + threadId + "event" + e + "@example.com",
                            Map.of("name", "User " + threadId + "-" + e));
                    
                    try {
                        notificationService.processEvent(event);
                    } catch (Exception ex) {
                        fail("Exception in thread " + threadId + ": " + ex.getMessage());
                    }
                }
            });
        }

        // When - Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(10000); // 10 second timeout per thread
        }

        // Then - All events should be processed correctly
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    long eventCount = storage.getEventCount();
                    assertTrue(eventCount >= threadCount * eventsPerThread, 
                            "Should have processed all events from all threads");
                    
                    long requestCount = storage.getNotificationRequestCount();
                    assertTrue(requestCount >= eventCount * 2, 
                            "Should have generated requests for all events (EMAIL + SMS)");
                });
    }
}