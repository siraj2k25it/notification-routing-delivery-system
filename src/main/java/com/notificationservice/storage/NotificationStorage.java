package com.notificationservice.storage;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationRequest;

import java.util.List;

/**
 * Storage interface for managing events and notification requests.
 * Provides abstraction layer for different storage implementations.
 */
public interface NotificationStorage {

    // Event management
    void saveEvent(Event event);
    Event getEvent(String eventId);
    List<Event> getAllEvents();

    // Notification request management
    void saveNotificationRequest(NotificationRequest request);
    void updateNotificationRequest(NotificationRequest request);
    NotificationRequest getNotificationRequest(String requestId);
    List<NotificationRequest> getNotificationRequestsByEventId(String eventId);
    List<NotificationRequest> getFailedNotificationRequests();

    // Dead letter management
    void saveToDeadLetter(NotificationRequest request);
    List<NotificationRequest> getDeadLetterRequests();

    // Statistics
    long getEventCount();
    long getNotificationRequestCount();
    long getSuccessfulDeliveryCount();
    long getFailedDeliveryCount();
    long getDeadLetterCount();
}