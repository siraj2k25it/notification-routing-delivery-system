package com.notificationservice.routing;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;

import java.util.List;
import java.util.function.Predicate;

/**
 * Record representing a routing rule that determines which channels to use for events.
 */
public record RoutingRule(
        String name,
        Predicate<Event> condition,
        List<NotificationChannel> channels,
        String messageTemplate,
        String subjectTemplate,
        int priority // Higher number = higher priority
) {

    // Static factory methods for common patterns
    public static RoutingRule forEventType(String eventType, String name,
                                           List<NotificationChannel> channels,
                                           String messageTemplate, String subjectTemplate) {
        return new RoutingRule(
                name,
                event -> eventType.equals(event.eventType()),
                channels,
                messageTemplate,
                subjectTemplate,
                1
        );
    }

    public static RoutingRule forPriority(Event.Priority priority, String name,
                                          List<NotificationChannel> channels,
                                          String messageTemplate, String subjectTemplate,
                                          int rulePriority) {
        return new RoutingRule(
                name,
                event -> priority.equals(event.priority()),
                channels,
                messageTemplate,
                subjectTemplate,
                rulePriority
        );
    }

    public static RoutingRule forHighPriority(List<NotificationChannel> channels,
                                              String messageTemplate, String subjectTemplate) {
        return new RoutingRule(
                "High Priority Events",
                event -> Event.Priority.HIGH.equals(event.priority()) ||
                        Event.Priority.CRITICAL.equals(event.priority()),
                channels,
                messageTemplate,
                subjectTemplate,
                10
        );
    }

    public static RoutingRule create(String name, Predicate<Event> condition,
                                     List<NotificationChannel> channels,
                                     String messageTemplate, String subjectTemplate,
                                     int priority) {
        return new RoutingRule(name, condition, channels, messageTemplate, subjectTemplate, priority);
    }
}