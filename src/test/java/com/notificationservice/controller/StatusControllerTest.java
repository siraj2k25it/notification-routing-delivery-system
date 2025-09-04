package com.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notificationservice.model.DeliveryStatus;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetDeliveryStatus_Success() throws Exception {
        // Given
        String eventId = "test-event-123";
        DeliveryStatus deliveryStatus = new DeliveryStatus(
                eventId, "req-456", NotificationChannel.EMAIL, 
                NotificationStatus.SENT, 0, LocalDateTime.now(), 
                LocalDateTime.now(), null, List.of()
        );
        
        when(notificationService.getDeliveryStatus(eventId)).thenReturn(deliveryStatus);

        // When & Then
        mockMvc.perform(get("/api/v1/status/delivery/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.retryCount").value(0));
    }

    @Test
    void testGetDeliveryStatus_NotFound() throws Exception {
        // Given
        String eventId = "non-existent-event";
        when(notificationService.getDeliveryStatus(eventId)).thenReturn(null);

        // When & Then
        mockMvc.perform(get("/api/v1/status/delivery/{eventId}", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAllDeliveryStatuses_MultipleChannels() throws Exception {
        // Given
        String eventId = "high-priority-event";
        List<DeliveryStatus> statuses = List.of(
                new DeliveryStatus(eventId, "req-push", NotificationChannel.PUSH, 
                        NotificationStatus.SENT, 0, LocalDateTime.now(), 
                        LocalDateTime.now(), null, List.of()),
                new DeliveryStatus(eventId, "req-email", NotificationChannel.EMAIL, 
                        NotificationStatus.SENT, 0, LocalDateTime.now(), 
                        LocalDateTime.now(), null, List.of()),
                new DeliveryStatus(eventId, "req-sms", NotificationChannel.SMS, 
                        NotificationStatus.SENT, 0, LocalDateTime.now(), 
                        LocalDateTime.now(), null, List.of())
        );
        
        when(notificationService.getAllDeliveryStatuses(eventId)).thenReturn(statuses);

        // When & Then
        mockMvc.perform(get("/api/v1/status/delivery/{eventId}/all", eventId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventId").value(eventId))
                .andExpect(jsonPath("$[0].channel").value("PUSH"))
                .andExpect(jsonPath("$[1].channel").value("EMAIL"))
                .andExpect(jsonPath("$[2].channel").value("SMS"))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[1].status").value("SENT"))
                .andExpect(jsonPath("$[2].status").value("SENT"));
    }

    @Test
    void testGetAllDeliveryStatuses_NotFound() throws Exception {
        // Given
        String eventId = "non-existent-event";
        when(notificationService.getAllDeliveryStatuses(eventId)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/status/delivery/{eventId}/all", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAllDeliveryStatuses_HighPriorityDemonstration() throws Exception {
        // Given - This test specifically demonstrates HIGH priority → PUSH + SMS + EMAIL
        String eventId = "urgent-system-alert";
        List<DeliveryStatus> highPriorityStatuses = List.of(
                new DeliveryStatus(eventId, "req-push-urgent", NotificationChannel.PUSH, 
                        NotificationStatus.SENT, 0, LocalDateTime.now(), 
                        LocalDateTime.now(), null, List.of()),
                new DeliveryStatus(eventId, "req-sms-urgent", NotificationChannel.SMS, 
                        NotificationStatus.SENT, 0, LocalDateTime.now(), 
                        LocalDateTime.now(), null, List.of()),
                new DeliveryStatus(eventId, "req-email-urgent", NotificationChannel.EMAIL, 
                        NotificationStatus.SENT, 0, LocalDateTime.now(), 
                        LocalDateTime.now(), null, List.of())
        );
        
        when(notificationService.getAllDeliveryStatuses(eventId)).thenReturn(highPriorityStatuses);

        // When & Then - Verify all 3 channels for HIGH priority
        mockMvc.perform(get("/api/v1/status/delivery/{eventId}/all", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.channel == 'PUSH')]").exists())
                .andExpect(jsonPath("$[?(@.channel == 'SMS')]").exists()) 
                .andExpect(jsonPath("$[?(@.channel == 'EMAIL')]").exists());
    }

    @Test
    void testGetFailedDeliveries() throws Exception {
        // Given
        DeliveryStatus failedDelivery = new DeliveryStatus(
                "failed-event", "req-failed", NotificationChannel.EMAIL,
                NotificationStatus.FAILED, 3, LocalDateTime.now(),
                LocalDateTime.now(), "SMTP timeout", List.of()
        );
        
        when(notificationService.getFailedDeliveries()).thenReturn(List.of(failedDelivery));

        // When & Then
        mockMvc.perform(get("/api/v1/status/failed"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId").value("failed-event"))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].failureReason").value("SMTP timeout"));
    }

    @Test
    void testGetHealthStatus() throws Exception {
        // Given
        Map<String, Object> healthInfo = Map.of(
                "service", Map.of("status", "healthy", "channelsAvailable", 4),
                "statistics", Map.of("eventsProcessed", 1500, "notificationsSent", 4200)
        );
        
        when(notificationService.getHealthInfo()).thenReturn(healthInfo);

        // When & Then
        mockMvc.perform(get("/api/v1/status/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.service.status").value("healthy"))
                .andExpect(jsonPath("$.service.channelsAvailable").value(4))
                .andExpect(jsonPath("$.statistics.eventsProcessed").value(1500));
    }

    @Test
    void testGetMetrics() throws Exception {
        // Given
        Map<String, Object> metrics = Map.of(
                "totalEvents", 2500,
                "successfulDeliveries", 2350,
                "failedDeliveries", 150,
                "channels", List.of("EMAIL", "SMS", "PUSH", "WEBHOOK")
        );
        
        when(notificationService.getServiceStats()).thenReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/v1/status/metrics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalEvents").value(2500))
                .andExpect(jsonPath("$.successfulDeliveries").value(2350))
                .andExpect(jsonPath("$.channels.length()").value(4));
    }

    @Test
    void testGetAllDeliveryStatuses_MixedSuccessAndFailure() throws Exception {
        // Given - Realistic scenario with mixed delivery results
        String eventId = "mixed-results-event";
        List<DeliveryStatus> mixedStatuses = List.of(
                new DeliveryStatus(eventId, "req-push-success", NotificationChannel.PUSH, 
                        NotificationStatus.SENT, 0, LocalDateTime.now(), 
                        LocalDateTime.now(), null, List.of()),
                new DeliveryStatus(eventId, "req-email-failed", NotificationChannel.EMAIL, 
                        NotificationStatus.FAILED, 2, LocalDateTime.now(), 
                        LocalDateTime.now(), "SMTP server unavailable", List.of()),
                new DeliveryStatus(eventId, "req-sms-retry", NotificationChannel.SMS, 
                        NotificationStatus.PENDING, 1, LocalDateTime.now(), 
                        LocalDateTime.now(), "Temporary gateway error", List.of())
        );
        
        when(notificationService.getAllDeliveryStatuses(eventId)).thenReturn(mixedStatuses);

        // When & Then
        mockMvc.perform(get("/api/v1/status/delivery/{eventId}/all", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].status").value("SENT"))
                .andExpect(jsonPath("$[1].status").value("FAILED"))
                .andExpect(jsonPath("$[2].status").value("PENDING"))
                .andExpect(jsonPath("$[1].failureReason").value("SMTP server unavailable"));
    }
}