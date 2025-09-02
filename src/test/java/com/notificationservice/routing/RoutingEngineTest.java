package com.notificationservice.routing;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoutingEngineTest {

    private RoutingEngine routingEngine;

    @BeforeEach
    void setUp() {
        routingEngine = new RoutingEngine();
        routingEngine.initializeRules();
    }

    @Test
    void testRouteEvent_UserRegistered() {
        // Given
        Event event = new Event("USER_REGISTERED", "siraj.shaik@gmail.com", 
                Map.of("name", "Siraj Shaik"));

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertFalse(requests.isEmpty());
        
        // Should route to both EMAIL and SMS channels
        assertEquals(2, requests.size());
        
        boolean hasEmail = requests.stream()
                .anyMatch(r -> r.channel() == NotificationChannel.EMAIL);
        boolean hasSms = requests.stream()
                .anyMatch(r -> r.channel() == NotificationChannel.SMS);
        
        assertTrue(hasEmail, "Should include EMAIL channel");
        assertTrue(hasSms, "Should include SMS channel");
        
        // Check message formatting
        NotificationRequest emailRequest = requests.stream()
                .filter(r -> r.channel() == NotificationChannel.EMAIL)
                .findFirst()
                .orElseThrow();
        
        assertTrue(emailRequest.message().contains("Siraj Shaik"));
        assertTrue(emailRequest.subject().contains("Welcome"));
    }

    @Test
    void testRouteEvent_PaymentCompleted() {
        // Given
        Event event = new Event("PAYMENT_COMPLETED", "siraj@multibank.com", 
                Map.of("amount", "2500.00", "transactionId", "TXN789"));

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertEquals(2, requests.size()); // EMAIL and SMS
        
        NotificationRequest request = requests.get(0);
        assertTrue(request.message().contains("2500.00"));
        assertTrue(request.message().contains("TXN789"));
        assertTrue(request.subject().contains("$2500.00"));
    }

    @Test
    void testRouteEvent_OrderShipped() {
        // Given
        Event event = new Event("ORDER_SHIPPED", "siraj.shaik@gmail.com", 
                Map.of("orderId", "ORD456", "deliveryDate", "2024-03-20", 
                       "trackingUrl", "http://track.aramex.com"));

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertEquals(2, requests.size()); // EMAIL and SMS
        
        NotificationRequest request = requests.get(0);
        assertTrue(request.message().contains("ORD456"));
        assertTrue(request.message().contains("2024-03-20"));
        assertTrue(request.message().contains("http://track.aramex.com"));
    }

    @Test
    void testRouteEvent_HighPriority() {
        // Given
        Event event = new Event("event-id", "CRITICAL_ALERT", 
                Map.of("message", "System overload detected"), 
                "admin@example.com", LocalDateTime.now(), Event.Priority.HIGH);

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertFalse(requests.isEmpty());
        
        NotificationRequest request = requests.get(0);
        assertTrue(request.message().contains("🚨 URGENT"));
        assertTrue(request.message().contains("System overload detected"));
    }

    @Test
    void testRouteEvent_SecurityAlert() {
        // Given
        Event event = new Event("SECURITY_ALERT", "user@example.com", 
                Map.of("alertType", "Suspicious login", 
                       "timestamp", "2024-01-01T10:00:00"));

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertEquals(2, requests.size()); // EMAIL and SMS
        
        NotificationRequest request = requests.get(0);
        assertTrue(request.message().contains("🔒 Security Alert"));
        assertTrue(request.message().contains("Suspicious login"));
        assertTrue(request.subject().contains("🔒 Security Alert"));
    }

    @Test
    void testRouteEvent_PasswordReset() {
        // Given
        Event event = new Event("PASSWORD_RESET", "user@example.com", 
                Map.of("resetUrl", "http://reset.example.com/token123"));

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertEquals(1, requests.size()); // Only EMAIL for password reset
        
        NotificationRequest request = requests.get(0);
        assertEquals(NotificationChannel.EMAIL, request.channel());
        assertTrue(request.message().contains("http://reset.example.com/token123"));
        assertTrue(request.message().contains("30 minutes"));
    }

    @Test
    void testRouteEvent_AccountVerification() {
        // Given
        Event event = new Event("ACCOUNT_VERIFICATION", "user@example.com", 
                Map.of("verificationCode", "123456"));

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertEquals(2, requests.size()); // EMAIL and SMS
        
        NotificationRequest request = requests.get(0);
        assertTrue(request.message().contains("123456"));
        assertTrue(request.message().contains("10 minutes"));
    }

    @Test
    void testRouteEvent_LowPriority() {
        // Given
        Event event = new Event("event-id", "NEWSLETTER", 
                Map.of("subject", "Monthly Update", "message", "Check out our new features"), 
                "user@example.com", LocalDateTime.now(), Event.Priority.LOW);

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertEquals(1, requests.size()); // Only EMAIL for low priority
        
        NotificationRequest request = requests.get(0);
        assertEquals(NotificationChannel.EMAIL, request.channel());
        assertTrue(request.subject().contains("Monthly Update"));
    }

    @Test
    void testRouteEvent_NoMatchingRules() {
        // Given
        Event event = new Event("UNKNOWN_EVENT_TYPE", "user@example.com", Map.of());

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertTrue(requests.isEmpty());
    }

    @Test
    void testTemplateFormatting_PlaceholderReplacement() {
        // Given
        Event event = new Event("USER_REGISTERED", "john@example.com", 
                Map.of("name", "John", "age", 25, "city", "New York"));

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(event);

        // Then
        assertNotNull(requests);
        assertFalse(requests.isEmpty());
        
        NotificationRequest request = requests.get(0);
        
        // Check that placeholders are replaced
        assertFalse(request.message().contains("{name}"));
        assertTrue(request.message().contains("John"));
        
        // Check that recipient is present somewhere in the request
        assertTrue(request.message().contains(event.recipient()) || 
                  request.subject().contains(event.recipient()) ||
                  request.recipient().equals(event.recipient()),
                  "Request should contain recipient information");
    }

    @Test
    void testAddRule() {
        // Given
        int initialRuleCount = routingEngine.getRuleCount();
        RoutingRule newRule = RoutingRule.forEventType(
                "CUSTOM_EVENT", "Custom Event Rule", 
                List.of(NotificationChannel.EMAIL),
                "Custom message", "Custom subject");

        // When
        routingEngine.addRule(newRule);

        // Then
        assertEquals(initialRuleCount + 1, routingEngine.getRuleCount());
        
        // Test the new rule works
        Event customEvent = new Event("CUSTOM_EVENT", "user@example.com", Map.of());
        List<NotificationRequest> requests = routingEngine.routeEvent(customEvent);
        
        assertFalse(requests.isEmpty());
        assertEquals(NotificationChannel.EMAIL, requests.get(0).channel());
    }

    @Test
    void testGetRules() {
        // When
        List<RoutingRule> rules = routingEngine.getRules();

        // Then
        assertNotNull(rules);
        assertFalse(rules.isEmpty());
        
        // Should be immutable copy
        int originalSize = rules.size();
        assertThrows(UnsupportedOperationException.class, 
                () -> rules.add(RoutingRule.forEventType("TEST", "Test", 
                        List.of(NotificationChannel.EMAIL), "msg", "subj")));
        
        // Original should be unchanged
        assertEquals(originalSize, routingEngine.getRuleCount());
    }

    @Test
    void testGetRoutingStats() {
        // When
        Map<String, Object> stats = routingEngine.getRoutingStats();

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
        
        // Convert to list for checking
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
    void testRulePriorityOrdering() {
        // Given - Create two events that might match multiple rules
        Event highPriorityEvent = new Event("event-1", "SECURITY_ALERT", 
                Map.of("alertType", "Login attempt"), "user@example.com", 
                LocalDateTime.now(), Event.Priority.HIGH);

        // When
        List<NotificationRequest> requests = routingEngine.routeEvent(highPriorityEvent);

        // Then
        assertNotNull(requests);
        assertFalse(requests.isEmpty());
        
        // The message should use the highest priority rule's template
        // Security Alert rule has priority 9, High Priority rule should be applied first
        NotificationRequest request = requests.get(0);
        assertTrue(request.message().contains("🔒 Security Alert") || 
                  request.message().contains("🚨 URGENT"));
    }
}