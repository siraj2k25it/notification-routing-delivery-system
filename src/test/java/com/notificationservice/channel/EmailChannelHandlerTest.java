package com.notificationservice.channel;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.*;

class EmailChannelHandlerTest {

    private EmailChannelHandler emailHandler;

    @BeforeEach
    void setUp() {
        emailHandler = new EmailChannelHandler();
    }

    @Test
    void testGetChannelType() {
        // When
        NotificationChannel channelType = emailHandler.getChannelType();

        // Then
        assertEquals(NotificationChannel.EMAIL, channelType);
    }

    @Test
    void testGetStatus() {
        // When
        String status = emailHandler.getStatus();

        // Then
        assertNotNull(status);
        assertTrue(status.contains("Email service"));
        assertTrue(status.contains("ready") || status.contains("connected"));
    }

    @Test
    void testSend_ValidRequest() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "siraj.shaik@gmail.com",
                "Test Subject", "Test message body", Event.Priority.MEDIUM);

        // When & Then - Due to random failure simulation, we can't guarantee success
        // but we can verify that the method completes properly
        try {
            boolean result = emailHandler.send(request);
            // If no exception, should return true
            assertTrue(result);
        } catch (Exception e) {
            // If exception thrown, it should be one of the expected error messages
            String errorMessage = e.getMessage();
            assertTrue(
                    errorMessage.contains("SMTP server temporarily unavailable") ||
                    errorMessage.contains("Invalid recipient email address") ||
                    errorMessage.contains("Message rejected by spam filter") ||
                    errorMessage.contains("Connection timeout to email server") ||
                    errorMessage.contains("Daily sending limit exceeded"),
                    "Unexpected error message: " + errorMessage
            );
        }
    }

    @Test
    void testSend_HandlesInterruption() {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test Subject", "Test message", Event.Priority.MEDIUM);

        // When - Interrupt the current thread to simulate interruption
        Thread.currentThread().interrupt();

        // Then
        Exception exception = assertThrows(Exception.class, () -> {
            emailHandler.send(request);
        });
        
        assertTrue(exception.getMessage().contains("interrupted"));
        
        // Clean up interrupt status
        Thread.interrupted();
    }

    @RepeatedTest(20) // Run multiple times to test the random failure behavior
    void testSend_RandomFailureBehavior() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "siraj@multibank.com",
                "Test Subject", "Test message", Event.Priority.MEDIUM);

        // When & Then
        try {
            boolean result = emailHandler.send(request);
            // If no exception, should return true (successful send)
            assertTrue(result);
        } catch (Exception e) {
            // If exception thrown, it should be one of the expected error messages
            String errorMessage = e.getMessage();
            assertTrue(
                    errorMessage.contains("SMTP server temporarily unavailable") ||
                    errorMessage.contains("Invalid recipient email address") ||
                    errorMessage.contains("Message rejected by spam filter") ||
                    errorMessage.contains("Connection timeout to email server") ||
                    errorMessage.contains("Daily sending limit exceeded"),
                    "Unexpected error message: " + errorMessage
            );
        }
    }

    @Test
    void testSend_MeasuresExecutionTime() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "siraj.shaik@outlook.com",
                "Test Subject", "Test message", Event.Priority.MEDIUM);

        // When
        long startTime = System.currentTimeMillis();
        try {
            emailHandler.send(request);
        } catch (Exception e) {
            // Ignore exceptions for this timing test
        }
        long endTime = System.currentTimeMillis();

        // Then - Should take at least 100ms due to simulated processing time
        long executionTime = endTime - startTime;
        assertTrue(executionTime >= 100, 
                "Execution time should be at least 100ms, was: " + executionTime + "ms");
        
        // Should not take too long (max ~500ms including random delay)
        assertTrue(executionTime < 1000, 
                "Execution time should be less than 1000ms, was: " + executionTime + "ms");
    }

    @Test
    void testSend_WithComplexSubjectAndMessage() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "siraj@multibank.com",
                "🎉 Welcome to MultiBank - Action Required! 📧",
                "Dear User,\n\nWelcome to our amazing platform! " +
                "Here are your next steps:\n" +
                "1. Verify your email\n" +
                "2. Complete your profile\n" +
                "3. Start exploring\n\n" +
                "Best regards,\nThe Team",
                Event.Priority.HIGH);

        // When & Then - Should handle complex content without issues
        try {
            boolean result = emailHandler.send(request);
            assertTrue(result);
        } catch (Exception e) {
            // Should be one of the expected random errors, not a content-related error
            assertTrue(e.getMessage().contains("SMTP") || 
                      e.getMessage().contains("Invalid") ||
                      e.getMessage().contains("rejected") ||
                      e.getMessage().contains("timeout") ||
                      e.getMessage().contains("limit"));
        }
    }

    @Test
    void testSend_WithSpecialCharacters() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user+test@example.co.uk",
                "Test with special chars: àáâãäåæçèéêë",
                "Message with émojis 🚀 and spëcial chârs ånd ñumbers 123!@#$%^&*()",
                Event.Priority.MEDIUM);

        // When & Then - Should handle special characters
        try {
            boolean result = emailHandler.send(request);
            assertTrue(result);
        } catch (Exception e) {
            // Should be one of the expected random errors, not encoding issues
            assertFalse(e.getMessage().contains("encoding") || 
                       e.getMessage().contains("character"),
                       "Should not fail due to character encoding issues");
        }
    }

    @Test
    void testSend_WithLongContent() throws Exception {
        // Given
        String longSubject = "Very ".repeat(50) + "Long Subject";
        String longMessage = "This is a very long message. ".repeat(100);
        
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                longSubject, longMessage, Event.Priority.LOW);

        // When & Then - Should handle long content
        try {
            boolean result = emailHandler.send(request);
            assertTrue(result);
        } catch (Exception e) {
            // Should be one of the expected random errors, not size-related
            assertFalse(e.getMessage().contains("size") || 
                       e.getMessage().contains("length") ||
                       e.getMessage().contains("too large"),
                       "Should not fail due to content size issues");
        }
    }
}