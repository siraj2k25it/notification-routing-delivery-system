package com.notificationservice.channel;

import com.notificationservice.model.NotificationChannel;
import com.notificationservice.model.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Email channel handler implementation.
 * Simulates email delivery with realistic delays and failure rates.
 */
@Component
public class EmailChannelHandler implements NotificationChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelHandler.class);
    private final Random random = new Random();

    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean send(NotificationRequest request) throws Exception {
        log.info("🔄 Sending EMAIL to: {} with subject: '{}'",
                request.recipient(), request.subject());

        // Simulate realistic email processing time
        try {
            Thread.sleep(100 + random.nextInt(200)); // 100-300ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Email sending interrupted", e);
        }

        // Simulate 10% failure rate (realistic for email)
        if (random.nextDouble() < 0.1) {
            String error = getRandomEmailError();
            log.warn("❌ EMAIL failed to: {} - {}", request.recipient(), error);
            throw new Exception(error);
        }

        log.info("✅ EMAIL sent successfully to: {}", request.recipient());
        return true;
    }

    @Override
    public String getStatus() {
        return "Email service ready - SMTP connected";
    }

    private String getRandomEmailError() {
        String[] errors = {
                "SMTP server temporarily unavailable",
                "Invalid recipient email address",
                "Message rejected by spam filter",
                "Connection timeout to email server",
                "Daily sending limit exceeded"
        };
        return errors[random.nextInt(errors.length)];
    }
}