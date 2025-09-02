package com.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.model.DeliveryStatus;
import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationStatus;
import com.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testPublishEvent_Success() throws Exception {
        // Given
        Event event = new Event("USER_REGISTERED", "user@example.com", 
                Map.of("name", "John Doe"));
        
        when(notificationService.processEvent(any(Event.class)))
                .thenReturn(CompletableFuture.completedFuture("Event processed successfully"));

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.message").value("Event accepted for processing"))
                .andExpect(jsonPath("$.eventId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void testPublishEvent_InvalidEventType() throws Exception {
        // Given - Event with blank event type (should fail validation)
        Event invalidEvent = new Event("event-id", "", 
                Map.of("name", "John"), "user@example.com", 
                LocalDateTime.now(), Event.Priority.MEDIUM);

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEvent)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPublishEvent_InvalidRecipient() throws Exception {
        // Given - Event with blank recipient (should fail validation)
        Event invalidEvent = new Event("event-id", "USER_REGISTERED", 
                Map.of("name", "John"), "", 
                LocalDateTime.now(), Event.Priority.MEDIUM);

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidEvent)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPublishEvent_ServiceThrowsException() throws Exception {
        // Given
        Event event = new Event("USER_REGISTERED", "user@example.com", Map.of());
        
        when(notificationService.processEvent(any(Event.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("Service unavailable"))
                .andExpect(jsonPath("$.eventId").exists());
    }

    @Test
    void testPublishEvent_MalformedJson() throws Exception {
        // Given - Malformed JSON
        String malformedJson = "{\"eventType\": \"USER_REGISTERED\", \"recipient\":}";

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPublishEvent_MissingContentType() throws Exception {
        // Given
        Event event = new Event("USER_REGISTERED", "user@example.com", Map.of());

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void testGetEvent_Found() throws Exception {
        // Given
        String eventId = "event-123";
        DeliveryStatus deliveryStatus = DeliveryStatus.forEvent(eventId, NotificationStatus.SENT);
        
        when(notificationService.getDeliveryStatus(eventId)).thenReturn(deliveryStatus);

        // When & Then
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.channel").exists())
                .andExpect(jsonPath("$.retryCount").exists())
                .andExpect(jsonPath("$.lastAttempt").exists());
    }

    @Test
    void testGetEvent_NotFound() throws Exception {
        // Given
        String eventId = "non-existent";
        when(notificationService.getDeliveryStatus(eventId)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetEvent_WithChannelInfo() throws Exception {
        // Given
        String eventId = "event-123";
        DeliveryStatus deliveryStatus = new DeliveryStatus(
                eventId, "req-123", NotificationChannel.EMAIL, NotificationStatus.FAILED,
                2, LocalDateTime.now(), LocalDateTime.now(), "SMTP error", List.of());
        
        when(notificationService.getDeliveryStatus(eventId)).thenReturn(deliveryStatus);

        // When & Then
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.retryCount").value(2))
                .andExpect(jsonPath("$.lastAttempt").exists());
    }

    @Test
    void testSimpleHealthCheck() throws Exception {
        // Given
        Map<String, Object> healthInfo = Map.of(
                "service", Map.of("status", "healthy", "channelsAvailable", 2),
                "channels", Map.of("EMAIL", "ready", "SMS", "ready"),
                "statistics", Map.of("eventsProcessed", 100, "notificationsSent", 95)
        );
        
        when(notificationService.getHealthInfo()).thenReturn(healthInfo);

        // When & Then
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.service").value("notification-routing-delivery-system"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").exists())
                .andExpect(jsonPath("$.details.service.status").value("healthy"))
                .andExpect(jsonPath("$.details.statistics.eventsProcessed").value(100));
    }

    @Test
    void testPublishEvent_WithComplexPayload() throws Exception {
        // Given
        Map<String, Object> complexPayload = Map.of(
                "user", Map.of(
                        "name", "John Doe",
                        "email", "john@example.com",
                        "preferences", Map.of(
                                "notifications", true,
                                "language", "en"
                        )
                ),
                "metadata", Map.of(
                        "source", "web",
                        "timestamp", "2024-01-01T10:00:00",
                        "tags", List.of("new-user", "premium")
                )
        );
        
        Event complexEvent = new Event("USER_REGISTERED", "john@example.com", complexPayload);
        
        when(notificationService.processEvent(any(Event.class)))
                .thenReturn(CompletableFuture.completedFuture("Event processed successfully"));

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(complexEvent)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.eventId").exists());
    }

    @Test
    void testPublishEvent_WithAllPriorityLevels() throws Exception {
        // Test all priority levels
        Event.Priority[] priorities = Event.Priority.values();
        
        for (Event.Priority priority : priorities) {
            // Given
            Event event = new Event("event-id", "TEST_EVENT", 
                    Map.of("test", "data"), "user@example.com", 
                    LocalDateTime.now(), priority);
            
            when(notificationService.processEvent(any(Event.class)))
                    .thenReturn(CompletableFuture.completedFuture("Event processed successfully"));

            // When & Then
            mockMvc.perform(post("/api/v1/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(event)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("accepted"));
        }
    }

    @Test
    void testGetEvent_PathVariable() throws Exception {
        // Given
        String eventId = "test-event-123";
        DeliveryStatus deliveryStatus = DeliveryStatus.forEvent(eventId, NotificationStatus.PENDING);
        
        when(notificationService.getDeliveryStatus(eventId)).thenReturn(deliveryStatus);

        // When & Then
        mockMvc.perform(get("/api/v1/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId));
    }

    @Test
    void testPublishEvent_MinimalValidEvent() throws Exception {
        // Given - Minimal valid event (using convenience constructor)
        Event minimalEvent = new Event("USER_REGISTERED", "user@example.com", null);
        
        when(notificationService.processEvent(any(Event.class)))
                .thenReturn(CompletableFuture.completedFuture("Event processed successfully"));

        // When & Then
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(minimalEvent)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));
    }
}