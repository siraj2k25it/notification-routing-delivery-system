package com.notificationservice.service;

import com.notificationservice.channel.NotificationChannelHandler;
import com.notificationservice.model.*;
import com.notificationservice.routing.RoutingEngine;
import com.notificationservice.storage.NotificationStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private RoutingEngine routingEngine;

    @Mock
    private NotificationStorage storage;

    @Mock
    private NotificationChannelHandler emailHandler;

    @Mock
    private NotificationChannelHandler smsHandler;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        when(emailHandler.getChannelType()).thenReturn(NotificationChannel.EMAIL);
        when(smsHandler.getChannelType()).thenReturn(NotificationChannel.SMS);

        List<NotificationChannelHandler> handlers = List.of(emailHandler, smsHandler);
        notificationService = new NotificationService(routingEngine, storage, handlers);
    }

    @Test
    void testProcessEvent_Success() throws Exception {
        // Given
        Event event = new Event("USER_REGISTERED", "siraj.shaik@outlook.com", 
                Map.of("name", "Siraj Shaik"));
        
        NotificationRequest emailRequest = NotificationRequest.create(
                event.eventId(), NotificationChannel.EMAIL, 
                event.recipient(), "Welcome", "Welcome Siraj!", 
                Event.Priority.MEDIUM);
        
        when(routingEngine.routeEvent(event)).thenReturn(List.of(emailRequest));

        // When
        CompletableFuture<String> result = notificationService.processEvent(event);

        // Then
        assertEquals("Event processed successfully", result.get());
        verify(storage).saveEvent(event);
        verify(storage).saveNotificationRequest(emailRequest);
        verify(routingEngine).routeEvent(event);
    }

    @Test
    void testProcessEvent_NoRoutingRules() throws Exception {
        // Given
        Event event = new Event("UNKNOWN_EVENT", "siraj.shaik@outlook.com", Map.of());
        when(routingEngine.routeEvent(event)).thenReturn(List.of());

        // When
        CompletableFuture<String> result = notificationService.processEvent(event);

        // Then
        assertEquals("No matching routing rules", result.get());
        verify(storage).saveEvent(event);
        verify(routingEngine).routeEvent(event);
    }

    @Test
    void testProcessEvent_Exception() throws Exception {
        // Given
        Event event = new Event("USER_REGISTERED", "siraj@multibank.com", Map.of());
        when(routingEngine.routeEvent(event)).thenThrow(new RuntimeException("Routing failed"));

        // When
        CompletableFuture<String> result = notificationService.processEvent(event);

        // Then
        assertTrue(result.get().startsWith("Error: "));
        verify(storage).saveEvent(event);
    }

    @Test
    void testDeliverNotification_Success() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com", 
                "Test", "Test message", Event.Priority.MEDIUM);
        
        when(emailHandler.send(request)).thenReturn(true);

        // When
        notificationService.deliverNotification(request);

        // Then
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(storage).updateNotificationRequest(captor.capture());
        
        NotificationRequest updatedRequest = captor.getValue();
        assertEquals(NotificationStatus.SENT, updatedRequest.status());
        verify(emailHandler).send(request);
    }

    @Test
    void testDeliverNotification_HandlerReturnsFalse() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com", 
                "Test", "Test message", Event.Priority.MEDIUM);
        
        when(emailHandler.send(request)).thenReturn(false);

        // When
        notificationService.deliverNotification(request);

        // Then
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(storage).updateNotificationRequest(captor.capture());
        
        NotificationRequest updatedRequest = captor.getValue();
        assertEquals(NotificationStatus.FAILED, updatedRequest.status());
        assertEquals("Handler returned false", updatedRequest.failureReason());
    }

    @Test
    void testDeliverNotification_HandlerThrowsException() throws Exception {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com", 
                "Test", "Test message", Event.Priority.MEDIUM);
        
        when(emailHandler.send(request)).thenThrow(new Exception("SMTP error"));

        // When
        notificationService.deliverNotification(request);

        // Then
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(storage).updateNotificationRequest(captor.capture());
        
        NotificationRequest updatedRequest = captor.getValue();
        assertEquals(NotificationStatus.FAILED, updatedRequest.status());
        assertEquals("SMTP error", updatedRequest.failureReason());
    }

    @Test
    void testDeliverNotification_NoHandlerFound() {
        // Given
        NotificationRequest request = NotificationRequest.create(
                "event-1", NotificationChannel.WEBHOOK, "user@example.com", 
                "Test", "Test message", Event.Priority.MEDIUM);

        // When
        notificationService.deliverNotification(request);

        // Then
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(storage).updateNotificationRequest(captor.capture());
        
        NotificationRequest updatedRequest = captor.getValue();
        assertEquals(NotificationStatus.FAILED, updatedRequest.status());
        assertTrue(updatedRequest.failureReason().contains("No handler available"));
    }

    @Test
    void testGetDeliveryStatus_EventFound() {
        // Given
        String eventId = "event-1";
        Event event = new Event("USER_REGISTERED", "user@example.com", Map.of());
        NotificationRequest request = NotificationRequest.create(
                eventId, NotificationChannel.EMAIL, "user@example.com", 
                "Test", "Test message", Event.Priority.MEDIUM);
        
        when(storage.getEvent(eventId)).thenReturn(event);
        when(storage.getNotificationRequestsByEventId(eventId)).thenReturn(List.of(request));

        // When
        DeliveryStatus result = notificationService.getDeliveryStatus(eventId);

        // Then
        assertNotNull(result);
        assertEquals(eventId, result.eventId());
    }

    @Test
    void testGetDeliveryStatus_EventNotFound() {
        // Given
        String eventId = "non-existent";
        when(storage.getEvent(eventId)).thenReturn(null);

        // When
        DeliveryStatus result = notificationService.getDeliveryStatus(eventId);

        // Then
        assertNull(result);
    }

    @Test
    void testGetAllDeliveryStatuses_MultipleChannels() {
        // Given - HIGH priority event with multiple channels
        String eventId = "multi-channel-event";
        Event highPriorityEvent = new Event(eventId, "CRITICAL_ALERT", 
                Map.of("message", "System overload"), "admin@example.com",
                LocalDateTime.now(), Event.Priority.HIGH);
        
        NotificationRequest pushRequest = NotificationRequest.create(
                eventId, NotificationChannel.PUSH, "device-token-123", 
                "Urgent Alert", "System overload detected", Event.Priority.HIGH)
                .withStatus(NotificationStatus.SENT);
        
        NotificationRequest emailRequest = NotificationRequest.create(
                eventId, NotificationChannel.EMAIL, "admin@example.com", 
                "Urgent Alert", "System overload detected", Event.Priority.HIGH)
                .withStatus(NotificationStatus.SENT);
        
        NotificationRequest smsRequest = NotificationRequest.create(
                eventId, NotificationChannel.SMS, "+971-50-789-4567", 
                "Urgent Alert", "System overload detected", Event.Priority.HIGH)
                .withStatus(NotificationStatus.SENT);

        when(storage.getEvent(eventId)).thenReturn(highPriorityEvent);
        when(storage.getNotificationRequestsByEventId(eventId))
                .thenReturn(List.of(pushRequest, emailRequest, smsRequest));

        // When
        List<DeliveryStatus> results = notificationService.getAllDeliveryStatuses(eventId);

        // Then
        assertNotNull(results);
        assertEquals(3, results.size(), "Should return status for all 3 channels");
        
        // Verify all channels are present
        Set<NotificationChannel> channels = results.stream()
                .map(DeliveryStatus::channel)
                .collect(Collectors.toSet());
        
        assertTrue(channels.contains(NotificationChannel.PUSH), "Should include PUSH channel");
        assertTrue(channels.contains(NotificationChannel.EMAIL), "Should include EMAIL channel");
        assertTrue(channels.contains(NotificationChannel.SMS), "Should include SMS channel");
        
        // Verify all are marked as sent
        assertTrue(results.stream().allMatch(status -> 
                NotificationStatus.SENT.equals(status.status())), 
                "All notifications should be marked as SENT");
    }

    @Test
    void testGetAllDeliveryStatuses_EventNotFound() {
        // Given
        String nonExistentEventId = "non-existent";
        when(storage.getEvent(nonExistentEventId)).thenReturn(null);

        // When
        List<DeliveryStatus> results = notificationService.getAllDeliveryStatuses(nonExistentEventId);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list for non-existent event");
    }

    @Test
    void testGetAllDeliveryStatuses_NoRequests() {
        // Given
        String eventId = "event-no-requests";
        Event event = new Event(eventId, "TEST_EVENT", Map.of(), "user@example.com", LocalDateTime.now(), Event.Priority.MEDIUM);
        when(storage.getEvent(eventId)).thenReturn(event);
        when(storage.getNotificationRequestsByEventId(eventId)).thenReturn(List.of());

        // When
        List<DeliveryStatus> results = notificationService.getAllDeliveryStatuses(eventId);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when no requests exist");
    }

    @Test
    void testGetFailedDeliveries() {
        // Given
        NotificationRequest failedRequest = NotificationRequest.create(
                "event-1", NotificationChannel.EMAIL, "user@example.com", 
                "Test", "Test message", Event.Priority.MEDIUM)
                .withFailure("SMTP error");
        
        when(storage.getFailedNotificationRequests()).thenReturn(List.of(failedRequest));

        // When
        List<DeliveryStatus> result = notificationService.getFailedDeliveries();

        // Then
        assertEquals(1, result.size());
        assertEquals("event-1", result.get(0).eventId());
    }

    @Test
    void testGetServiceStats() {
        // Given
        when(storage.getEventCount()).thenReturn(10L);
        when(storage.getSuccessfulDeliveryCount()).thenReturn(8L);
        when(storage.getFailedDeliveryCount()).thenReturn(2L);
        when(storage.getDeadLetterCount()).thenReturn(0L);
        when(routingEngine.getRuleCount()).thenReturn(5);

        // When
        Map<String, Object> stats = notificationService.getServiceStats();

        // Then
        assertEquals(10L, stats.get("eventsProcessed"));
        assertEquals(8L, stats.get("notificationsSent"));
        assertEquals(2L, stats.get("failedDeliveries"));
        assertEquals(0L, stats.get("deadLetterCount"));
        assertEquals(5, stats.get("routingRules"));
    }

    @Test
    void testGetHealthInfo() {
        // Given
        when(routingEngine.getRuleCount()).thenReturn(5);
        when(emailHandler.getStatus()).thenReturn("Email service ready");
        when(smsHandler.getStatus()).thenReturn("SMS service ready");

        // When
        Map<String, Object> health = notificationService.getHealthInfo();

        // Then
        assertNotNull(health);
        assertTrue(health.containsKey("service"));
        assertTrue(health.containsKey("channels"));
        assertTrue(health.containsKey("statistics"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> serviceInfo = (Map<String, Object>) health.get("service");
        assertEquals("healthy", serviceInfo.get("status"));
        assertEquals(2, serviceInfo.get("channelsAvailable"));
    }
}