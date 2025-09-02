package com.notificationservice.storage;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import com.notificationservice.model.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryNotificationStorageTest {

    private InMemoryNotificationStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryNotificationStorage();
    }

    @Test
    void testSaveAndGetEvent() {
        // Given
        Event event = new Event("USER_REGISTERED", "user@example.com", 
                Map.of("name", "John Doe"));

        // When
        storage.saveEvent(event);
        Event retrieved = storage.getEvent(event.eventId());

        // Then
        assertNotNull(retrieved);
        assertEquals(event.eventId(), retrieved.eventId());
        assertEquals(event.eventType(), retrieved.eventType());
        assertEquals(event.recipient(), retrieved.recipient());
    }

    @Test
    void testGetEvent_NotFound() {
        // When
        Event result = storage.getEvent("non-existent");

        // Then
        assertNull(result);
    }

    @Test
    void testGetAllEvents() {
        // Given
        Event event1 = new Event("USER_REGISTERED", "user1@example.com", Map.of());
        Event event2 = new Event("PAYMENT_COMPLETED", "user2@example.com", Map.of());
        
        storage.saveEvent(event1);
        storage.saveEvent(event2);

        // When
        List<Event> allEvents = storage.getAllEvents();

        // Then
        assertEquals(2, allEvents.size());
        assertTrue(allEvents.contains(event1));
        assertTrue(allEvents.contains(event2));
    }

    @Test
    void testSaveAndGetNotificationRequest() {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);

        // When
        storage.saveNotificationRequest(request);
        NotificationRequest retrieved = storage.getNotificationRequest(request.requestId());

        // Then
        assertNotNull(retrieved);
        assertEquals(request.requestId(), retrieved.requestId());
        assertEquals(request.eventId(), retrieved.eventId());
        assertEquals(request.channel(), retrieved.channel());
    }

    @Test
    void testUpdateNotificationRequest() {
        // Given
        NotificationRequest original = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);
        
        storage.saveNotificationRequest(original);
        
        NotificationRequest updated = original.withStatus(NotificationStatus.SENT);

        // When
        storage.updateNotificationRequest(updated);
        NotificationRequest retrieved = storage.getNotificationRequest(original.requestId());

        // Then
        assertEquals(NotificationStatus.SENT, retrieved.status());
        assertEquals(original.requestId(), retrieved.requestId());
    }

    @Test
    void testGetNotificationRequestsByEventId() {
        // Given
        String eventId = "event-1";
        NotificationRequest emailRequest = NotificationRequest.create(
                eventId, NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);
        NotificationRequest smsRequest = NotificationRequest.create(
                eventId, NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);
        NotificationRequest otherRequest = NotificationRequest.create(
                "event-2", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);

        storage.saveNotificationRequest(emailRequest);
        storage.saveNotificationRequest(smsRequest);
        storage.saveNotificationRequest(otherRequest);

        // When
        List<NotificationRequest> requests = storage.getNotificationRequestsByEventId(eventId);

        // Then
        assertEquals(2, requests.size());
        assertTrue(requests.stream().allMatch(r -> eventId.equals(r.eventId())));
        assertTrue(requests.contains(emailRequest));
        assertTrue(requests.contains(smsRequest));
        assertFalse(requests.contains(otherRequest));
    }

    @Test
    void testGetFailedNotificationRequests() {
        // Given
        NotificationRequest successRequest = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withStatus(NotificationStatus.SENT);
        
        NotificationRequest failedRequest = NotificationRequest.create(
                "event-2", NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withFailure("SMTP error");
        
        NotificationRequest deadLetterRequest = NotificationRequest.create(
                "event-3", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withStatus(NotificationStatus.DEAD_LETTER);

        storage.saveNotificationRequest(successRequest);
        storage.saveNotificationRequest(failedRequest);
        storage.saveNotificationRequest(deadLetterRequest);

        // When
        List<NotificationRequest> failedRequests = storage.getFailedNotificationRequests();

        // Then
        assertEquals(2, failedRequests.size());
        assertTrue(failedRequests.contains(failedRequest));
        assertTrue(failedRequests.contains(deadLetterRequest));
        assertFalse(failedRequests.contains(successRequest));
    }

    @Test
    void testSaveToDeadLetter() {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withRetry(3, "Max retries exceeded");

        // When
        storage.saveToDeadLetter(request);

        // Then
        List<NotificationRequest> deadLetters = storage.getDeadLetterRequests();
        assertEquals(1, deadLetters.size());
        assertEquals(request.requestId(), deadLetters.get(0).requestId());
    }

    @Test
    void testGetDeadLetterRequests() {
        // Given
        NotificationRequest request1 = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);
        NotificationRequest request2 = NotificationRequest.create(
                "event-2", NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);

        storage.saveToDeadLetter(request1);
        storage.saveToDeadLetter(request2);

        // When
        List<NotificationRequest> deadLetters = storage.getDeadLetterRequests();

        // Then
        assertEquals(2, deadLetters.size());
        assertTrue(deadLetters.contains(request1));
        assertTrue(deadLetters.contains(request2));
    }

    @Test
    void testGetEventCount() {
        // Given
        assertEquals(0, storage.getEventCount());

        Event event1 = new Event("USER_REGISTERED", "user1@example.com", Map.of());
        Event event2 = new Event("PAYMENT_COMPLETED", "user2@example.com", Map.of());

        // When
        storage.saveEvent(event1);
        assertEquals(1, storage.getEventCount());

        storage.saveEvent(event2);

        // Then
        assertEquals(2, storage.getEventCount());
    }

    @Test
    void testGetNotificationRequestCount() {
        // Given
        assertEquals(0, storage.getNotificationRequestCount());

        NotificationRequest request1 = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);
        NotificationRequest request2 = NotificationRequest.create(
                "event-2", NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);

        // When
        storage.saveNotificationRequest(request1);
        assertEquals(1, storage.getNotificationRequestCount());

        storage.saveNotificationRequest(request2);

        // Then
        assertEquals(2, storage.getNotificationRequestCount());
    }

    @Test
    void testGetSuccessfulDeliveryCount() {
        // Given
        NotificationRequest sentRequest = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withStatus(NotificationStatus.SENT);
        
        NotificationRequest failedRequest = NotificationRequest.create(
                "event-2", NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withFailure("Error");

        storage.saveNotificationRequest(sentRequest);
        storage.saveNotificationRequest(failedRequest);

        // When
        long successfulCount = storage.getSuccessfulDeliveryCount();

        // Then
        assertEquals(1, successfulCount);
    }

    @Test
    void testGetFailedDeliveryCount() {
        // Given
        NotificationRequest sentRequest = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withStatus(NotificationStatus.SENT);
        
        NotificationRequest failedRequest = NotificationRequest.create(
                "event-2", NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withFailure("Error");

        storage.saveNotificationRequest(sentRequest);
        storage.saveNotificationRequest(failedRequest);

        // When
        long failedCount = storage.getFailedDeliveryCount();

        // Then
        assertEquals(1, failedCount);
    }

    @Test
    void testGetDeadLetterCount() {
        // Given
        NotificationRequest request1 = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);
        NotificationRequest request2 = NotificationRequest.create(
                "event-2", NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);

        storage.saveToDeadLetter(request1);
        storage.saveToDeadLetter(request2);

        // When
        long deadLetterCount = storage.getDeadLetterCount();

        // Then
        assertEquals(2, deadLetterCount);
    }

    @Test
    void testClearAll() {
        // Given
        Event event = new Event("USER_REGISTERED", "user@example.com", Map.of());
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);

        storage.saveEvent(event);
        storage.saveNotificationRequest(request);
        storage.saveToDeadLetter(request);

        // When
        storage.clearAll();

        // Then
        assertEquals(0, storage.getEventCount());
        assertEquals(0, storage.getNotificationRequestCount());
        assertEquals(0, storage.getDeadLetterCount());
        assertNull(storage.getEvent(event.eventId()));
        assertNull(storage.getNotificationRequest(request.requestId()));
    }

    @Test
    void testGetStorageStats() {
        // Given
        Event event = new Event("USER_REGISTERED", "user@example.com", Map.of());
        NotificationRequest sentRequest = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withStatus(NotificationStatus.SENT);
        NotificationRequest failedRequest = NotificationRequest.create(
                "event-2", NotificationChannel.SMS, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM)
                .withFailure("Error");
        NotificationRequest deadRequest = NotificationRequest.create(
                "event-3", NotificationChannel.EMAIL, "user@example.com",
                "Test", "Test message", Event.Priority.MEDIUM);

        storage.saveEvent(event);
        storage.saveNotificationRequest(sentRequest);
        storage.saveNotificationRequest(failedRequest);
        storage.saveToDeadLetter(deadRequest);

        // When
        String stats = storage.getStorageStats();

        // Then
        assertNotNull(stats);
        assertTrue(stats.contains("Events: 1"));
        assertTrue(stats.contains("Notifications: 2"));
        assertTrue(stats.contains("Successful: 1"));
        assertTrue(stats.contains("Failed: 1"));
        assertTrue(stats.contains("Dead Letter: 1"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When - Perform concurrent operations
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Save events
                        Event event = new Event("USER_REGISTERED_" + threadId + "_" + j, 
                                "user" + threadId + "@example.com", Map.of());
                        storage.saveEvent(event);

                        // Save notification requests
                        NotificationRequest request = NotificationRequest.create(
                                event.eventId(), NotificationChannel.EMAIL, 
                                event.recipient(), "Test", "Test message", 
                                Event.Priority.MEDIUM);
                        storage.saveNotificationRequest(request);

                        // Update some requests
                        if (j % 2 == 0) {
                            storage.updateNotificationRequest(
                                    request.withStatus(NotificationStatus.SENT));
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Verify final state
        assertEquals(threadCount * operationsPerThread, storage.getEventCount());
        assertEquals(threadCount * operationsPerThread, storage.getNotificationRequestCount());
        
        // Should have some successful deliveries (half were updated to SENT)
        long successfulCount = storage.getSuccessfulDeliveryCount();
        assertTrue(successfulCount > 0);
        assertEquals(threadCount * operationsPerThread / 2, successfulCount);
    }
}