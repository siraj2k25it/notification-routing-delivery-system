package com.notificationservice.routing;

import com.notificationservice.model.Event;
import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service responsible for routing events to appropriate notification channels
 * based on configurable rules.
 */
@Service
public class RoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);

    // Thread-safe list for concurrent access
    private final List<RoutingRule> rules = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void initializeRules() {
        log.info("🔧 Initializing routing rules...");

        // User registration rules - Welcome notifications
        rules.add(RoutingRule.forEventType(
                "USER_REGISTERED",
                "User Registration Welcome",
                List.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                "Welcome {name}! Your account has been created successfully. Get started by exploring our features!",
                "Welcome to our platform, {name}!"
        ));

        // Payment completion rules - Confirmation notifications
        rules.add(RoutingRule.forEventType(
                "PAYMENT_COMPLETED",
                "Payment Confirmation",
                List.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                "Payment of ${amount} has been processed successfully. Transaction ID: {transactionId}",
                "Payment Confirmation - ${amount}"
        ));

        // Order status rules - E-commerce notifications
        rules.add(RoutingRule.forEventType(
                "ORDER_SHIPPED",
                "Order Shipped Notification",
                List.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                "Great news! Your order #{orderId} has been shipped and will arrive by {deliveryDate}. Track: {trackingUrl}",
                "Your order #{orderId} has shipped!"
        ));

        // High priority rules - Urgent notifications
        rules.add(RoutingRule.forHighPriority(
                List.of(NotificationChannel.SMS, NotificationChannel.EMAIL),
                "🚨 URGENT: {message} - Please take immediate action.",
                "🚨 Urgent Notification"
        ));

        // Security alerts - Critical security notifications
        rules.add(RoutingRule.create(
                "Security Alert",
                event -> "SECURITY_ALERT".equals(event.eventType()),
                List.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                "🔒 Security Alert: {alertType} detected for your account at {timestamp}. If this wasn't you, please secure your account immediately.",
                "🔒 Security Alert - {alertType}",
                9
        ));

        // Password reset rules
        rules.add(RoutingRule.forEventType(
                "PASSWORD_RESET",
                "Password Reset Request",
                List.of(NotificationChannel.EMAIL),
                "You requested a password reset. Click here to reset: {resetUrl}. This link expires in 30 minutes.",
                "Password Reset Request"
        ));

        // Account verification rules
        rules.add(RoutingRule.forEventType(
                "ACCOUNT_VERIFICATION",
                "Account Verification",
                List.of(NotificationChannel.EMAIL, NotificationChannel.SMS),
                "Please verify your account using this code: {verificationCode}. Code expires in 10 minutes.",
                "Account Verification Required"
        ));

        // Low priority rules - Newsletter, updates
        rules.add(RoutingRule.forPriority(
                Event.Priority.LOW,
                "Low Priority Updates",
                List.of(NotificationChannel.EMAIL),
                "{message}",
                "Update: {subject}",
                1
        ));

        // Sort rules by priority (highest first)
        sortRulesByPriority();

        log.info("✅ Initialized {} routing rules", rules.size());
        rules.forEach(rule -> log.debug("  📋 Rule: {} (priority: {})", rule.name(), rule.priority()));
    }

    /**
     * Route an event to appropriate notification channels based on rules.
     */
    public List<NotificationRequest> routeEvent(Event event) {
        log.debug("🔀 Routing event: {} of type: {} for recipient: {}",
                event.eventId(), event.eventType(), event.recipient());

        Set<NotificationChannel> selectedChannels = new HashSet<>();
        String finalMessage = "";
        String finalSubject = "";

        // Apply all matching rules
        for (RoutingRule rule : rules) {
            if (rule.condition().test(event)) {
                log.debug("✅ Rule '{}' matched for event: {}", rule.name(), event.eventId());
                selectedChannels.addAll(rule.channels());

                // Use the highest priority rule's templates (first match wins due to sorting)
                if (finalMessage.isEmpty()) {
                    finalMessage = formatTemplate(rule.messageTemplate(), event);
                    finalSubject = formatTemplate(rule.subjectTemplate(), event);
                }
            }
        }

        if (selectedChannels.isEmpty()) {
            log.warn("⚠️ No routing rules matched for event: {} of type: {}",
                    event.eventId(), event.eventType());
            return List.of();
        }

        // Create notification requests for each selected channel
        String finalSubject1 = finalSubject;
        String finalMessage1 = finalMessage;
        List<NotificationRequest> requests = selectedChannels.stream()
                .map(channel -> NotificationRequest.create(
                        event.eventId(),
                        channel,
                        event.recipient(),
                        finalSubject1,
                        finalMessage1,
                        event.priority()
                ))
                .toList();

        log.info("🎯 Generated {} notification requests for event: {} → channels: {}",
                requests.size(), event.eventId(), selectedChannels);

        return requests;
    }

    /**
     * Format message templates by replacing placeholders with event data.
     */
    private String formatTemplate(String template, Event event) {
        String result = template;

        // Replace placeholders with actual values from event payload
        if (event.payload() != null) {
            for (Map.Entry<String, Object> entry : event.payload().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        // Replace common placeholders
        result = result.replace("{eventType}", event.eventType());
        result = result.replace("{recipient}", event.recipient());
        result = result.replace("{timestamp}", event.timestamp().toString());
        result = result.replace("{eventId}", event.eventId());

        return result;
    }

    public void addRule(RoutingRule rule) {
        rules.add(rule);
        sortRulesByPriority();
        log.info("➕ Added new routing rule: {} with priority: {}", rule.name(), rule.priority());
    }

    public List<RoutingRule> getRules() {
        return List.copyOf(rules);
    }

    public int getRuleCount() {
        return rules.size();
    }

    private void sortRulesByPriority() {
        rules.sort((r1, r2) -> Integer.compare(r2.priority(), r1.priority()));
    }

    /**
     * Get statistics about rule matching
     */
    public Map<String, Object> getRoutingStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRules", rules.size());
        stats.put("ruleNames", rules.stream().map(RoutingRule::name).toList());
        stats.put("channelCoverage", getAllSupportedChannels());
        return stats;
    }

    private Set<NotificationChannel> getAllSupportedChannels() {
        return rules.stream()
                .flatMap(rule -> rule.channels().stream())
                .collect(HashSet::new, Set::add, Set::addAll);
    }
}