package com.notificationservice.channel;

import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * SMS channel handler implementation.
 * Simulates SMS delivery with realistic delays and failure rates.
 */
@Component
public class SmsChannelHandler implements NotificationChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelHandler.class);
    private final Random random = new Random();

    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.SMS;
    }

    @Override
    public boolean send(NotificationRequest request) throws Exception {
        log.info("📱 Sending SMS to: {} with message: '{}'",
                request.recipient(), truncateMessage(request.message()));

        // Simulate SMS processing time (faster than email)
        try {
            Thread.sleep(50 + random.nextInt(100)); // 50-150ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("SMS sending interrupted", e);
        }

        // Simulate 15% failure rate (higher than email due to carrier issues)
        if (random.nextDouble() < 0.15) {
            String error = getRandomSmsError();
            log.warn("❌ SMS failed to: {} - {}", request.recipient(), error);
            throw new Exception(error);
        }

        log.info("✅ SMS sent successfully to: {}", request.recipient());
        return true;
    }

    @Override
    public String getStatus() {
        return "SMS gateway connected - Ready to send";
    }

    private String truncateMessage(String message) {
        if (message == null) return "null";
        return message.length() > 50 ? message.substring(0, 47) + "..." : message;
    }

    private String getRandomSmsError() {
        String[] errors = {
                "SMS gateway rate limit exceeded",
                "Invalid phone number format",
                "Carrier blocked the message",
                "Insufficient SMS credits",
                "Network timeout error",
                "Recipient phone is unreachable"
        };
        return errors[random.nextInt(errors.length)];
    }
}