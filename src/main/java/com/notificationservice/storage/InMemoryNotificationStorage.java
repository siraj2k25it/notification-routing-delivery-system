package com.notificationservice.storage;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationRequest;
import com.notificationservice.model.NotificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of NotificationStorage.
 * Thread-safe storage using ConcurrentHashMap for development and testing.
 */
@Repository
public class InMemoryNotificationStorage implements NotificationStorage {

    private static final Logger log = LoggerFactory.getLogger(InMemoryNotificationStorage.class);

    private final ConcurrentHashMap<String, Event> events = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NotificationRequest> notificationRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NotificationRequest> deadLetterRequests = new ConcurrentHashMap<>();

    @Override
    public void saveEvent(Event event) {
        events.put(event.eventId(), event);
        log.debug("Saved event: {} of type: {}", event.eventId(), event.eventType());
    }

    @Override
    public Event getEvent(String eventId) {
        return events.get(eventId);
    }

    @Override
    public List<Event> getAllEvents() {
        return List.copyOf(events.values());
    }

    @Override
    public void saveNotificationRequest(NotificationRequest request) {
        notificationRequests.put(request.requestId(), request);
        log.debug("Saved notification request: {} for channel: {}",
                request.requestId(), request.channel());
    }

    @Override
    public void updateNotificationRequest(NotificationRequest request) {
        notificationRequests.put(request.requestId(), request);
        log.debug("Updated notification request: {} with status: {}",
                request.requestId(), request.status());
    }

    @Override
    public NotificationRequest getNotificationRequest(String requestId) {
        return notificationRequests.get(requestId);
    }

    @Override
    public List<NotificationRequest> getNotificationRequestsByEventId(String eventId) {
        return notificationRequests.values().stream()
                .filter(request -> eventId.equals(request.eventId()))
                .toList();
    }

    @Override
    public List<NotificationRequest> getFailedNotificationRequests() {
        return notificationRequests.values().stream()
                .filter(request -> NotificationStatus.FAILED.equals(request.status()) ||
                        NotificationStatus.DEAD_LETTER.equals(request.status()))
                .toList();
    }

    @Override
    public void saveToDeadLetter(NotificationRequest request) {
        deadLetterRequests.put(request.requestId(), request);
        log.warn("Moved to dead letter: {} after {} retries",
                request.requestId(), request.retryCount());
    }

    @Override
    public List<NotificationRequest> getDeadLetterRequests() {
        return List.copyOf(deadLetterRequests.values());
    }

    // Statistics methods
    @Override
    public long getEventCount() {
        return events.size();
    }

    @Override
    public long getNotificationRequestCount() {
        return notificationRequests.size();
    }

    @Override
    public long getSuccessfulDeliveryCount() {
        return notificationRequests.values().stream()
                .filter(request -> NotificationStatus.SENT.equals(request.status()))
                .count();
    }

    @Override
    public long getFailedDeliveryCount() {
        return notificationRequests.values().stream()
                .filter(request -> NotificationStatus.FAILED.equals(request.status()))
                .count();
    }

    @Override
    public long getDeadLetterCount() {
        return deadLetterRequests.size();
    }

    // Utility methods for monitoring
    public void clearAll() {
        events.clear();
        notificationRequests.clear();
        deadLetterRequests.clear();
        log.info("Cleared all storage data");
    }

    public String getStorageStats() {
        return String.format(
                "Storage Stats - Events: %d, Notifications: %d, Successful: %d, Failed: %d, Dead Letter: %d",
                getEventCount(), getNotificationRequestCount(), getSuccessfulDeliveryCount(),
                getFailedDeliveryCount(), getDeadLetterCount()
        );
    }
}