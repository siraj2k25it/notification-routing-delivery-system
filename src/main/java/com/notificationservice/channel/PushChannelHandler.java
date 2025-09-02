package com.notificationservice.channel;

import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Push notification channel handler implementation.
 * Handles delivery to mobile devices and web browsers via push notifications.
 * Simulates push delivery with realistic delays and failure rates.
 */
@Component
public class PushChannelHandler implements NotificationChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(PushChannelHandler.class);
    private final Random random = new Random();

    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.PUSH;
    }

    @Override
    public boolean send(NotificationRequest request) throws Exception {
        log.info("📱 Sending PUSH notification to: {} with subject: '{}'",
                request.recipient(), request.subject());

        // Simulate realistic push notification processing time
        try {
            Thread.sleep(50 + random.nextInt(100)); // 50-150ms (faster than email)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Push notification sending interrupted", e);
        }

        // Simulate 8% failure rate (slightly better than email, worse than SMS)
        if (random.nextDouble() < 0.08) {
            String error = getRandomPushError();
            log.warn("❌ PUSH failed to: {} - {}", request.recipient(), error);
            throw new Exception(error);
        }

        log.info("✅ PUSH notification sent successfully to: {}", request.recipient());
        return true;
    }

    @Override
    public String getStatus() {
        return "Push notification service ready - FCM/APNS connected";
    }

    private String getRandomPushError() {
        String[] errors = {
                "Device token expired or invalid",
                "Push service temporarily unavailable",
                "Message payload too large",
                "Invalid push registration token",
                "Push notification quota exceeded",
                "Device not reachable (offline)",
                "Application not installed on device"
        };
        return errors[random.nextInt(errors.length)];
    }
}