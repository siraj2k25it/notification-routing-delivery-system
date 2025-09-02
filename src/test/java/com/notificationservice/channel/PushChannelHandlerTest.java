package com.notificationservice.channel;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.*;

class PushChannelHandlerTest {

    private PushChannelHandler pushHandler;

    @BeforeEach
    void setUp() {
        pushHandler = new PushChannelHandler();
    }

    @Test
    void testGetChannelType() {
        // When
        NotificationChannel channelType = pushHandler.getChannelType();

        // Then
        assertEquals(NotificationChannel.PUSH, channelType);
    }

    @Test
    void testGetStatus() {
        // When
        String status = pushHandler.getStatus();

        // Then
        assertNotNull(status);
        assertTrue(status.contains("Push notification service"));
        assertTrue(status.contains("ready") || status.contains("connected"));
        assertTrue(status.contains("FCM") || status.contains("APNS"));
    }

    @Test
    void testSend_ValidRequest() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.PUSH, "user-device-token-123",
                "🚨 Urgent Alert", "System overload detected", Event.Priority.HIGH);

        // When & Then - Due to random failure simulation, we handle both success and failure
        try {
            boolean result = pushHandler.send(request);
            // If no exception, should return true
            assertTrue(result);
        } catch (Exception e) {
            // If exception thrown, it should be one of the expected error messages
            String errorMessage = e.getMessage();
            assertTrue(
                    errorMessage.contains("Device token expired") ||
                    errorMessage.contains("Push service temporarily unavailable") ||
                    errorMessage.contains("Message payload too large") ||
                    errorMessage.contains("Invalid push registration token") ||
                    errorMessage.contains("Push notification quota exceeded") ||
                    errorMessage.contains("Device not reachable") ||
                    errorMessage.contains("Application not installed"),
                    "Unexpected error message: " + errorMessage
            );
        }
    }

    @Test
    void testSend_HandlesInterruption() {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.PUSH, "user-device-token",
                "Test Subject", "Test message", Event.Priority.HIGH);

        // When - Interrupt the current thread to simulate interruption
        Thread.currentThread().interrupt();

        // Then
        Exception exception = assertThrows(Exception.class, () -> {
            pushHandler.send(request);
        });
        
        assertTrue(exception.getMessage().contains("interrupted"));
        
        // Clean up interrupt status
        Thread.interrupted();
    }

    @RepeatedTest(10) // Test the random failure behavior
    void testSend_RandomFailureBehavior() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.PUSH, "device-token-test",
                "Test Subject", "Test push notification", Event.Priority.HIGH);

        // When & Then
        try {
            boolean result = pushHandler.send(request);
            // If no exception, should return true (successful send)
            assertTrue(result);
        } catch (Exception e) {
            // If exception thrown, it should be one of the expected error messages
            String errorMessage = e.getMessage();
            assertTrue(
                    errorMessage.contains("Device token expired") ||
                    errorMessage.contains("Push service temporarily unavailable") ||
                    errorMessage.contains("Message payload too large") ||
                    errorMessage.contains("Invalid push registration token") ||
                    errorMessage.contains("Push notification quota exceeded") ||
                    errorMessage.contains("Device not reachable") ||
                    errorMessage.contains("Application not installed"),
                    "Unexpected error message: " + errorMessage
            );
        }
    }

    @Test
    void testSend_MeasuresExecutionTime() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.PUSH, "performance-test-token",
                "Performance Test", "Testing push performance", Event.Priority.HIGH);

        // When
        long startTime = System.currentTimeMillis();
        try {
            pushHandler.send(request);
        } catch (Exception e) {
            // Ignore exceptions for this timing test
        }
        long endTime = System.currentTimeMillis();

        // Then - Should take at least 50ms due to simulated processing time
        long executionTime = endTime - startTime;
        assertTrue(executionTime >= 50, 
                "Execution time should be at least 50ms, was: " + executionTime + "ms");
        
        // Should not take too long (max ~200ms including random delay)
        assertTrue(executionTime < 500, 
                "Execution time should be less than 500ms, was: " + executionTime + "ms");
    }

    @Test
    void testSend_WithHighPriorityUrgentMessage() throws Exception {
        // Given - This demonstrates the "if priority = HIGH then send push notification" requirement
        NotificationRequest urgentRequest = NotificationRequest.create(
                "urgent-event", NotificationChannel.PUSH, "urgent-device-token",
                "🚨 CRITICAL SYSTEM ALERT", 
                "🚨 URGENT: Database connection lost - Please take immediate action.",
                Event.Priority.HIGH);

        // When & Then - Should handle urgent high-priority notifications
        try {
            boolean result = pushHandler.send(urgentRequest);
            assertTrue(result);
        } catch (Exception e) {
            // Should be one of the expected random errors, not content-related
            assertTrue(e.getMessage().contains("Device token") || 
                      e.getMessage().contains("Push service") ||
                      e.getMessage().contains("payload") ||
                      e.getMessage().contains("registration") ||
                      e.getMessage().contains("quota") ||
                      e.getMessage().contains("not reachable") ||
                      e.getMessage().contains("not installed"));
        }
    }
}