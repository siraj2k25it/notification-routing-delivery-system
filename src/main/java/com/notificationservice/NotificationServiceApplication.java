package com.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for the Notification Routing & Delivery System.
 * 
 * This Spring Boot application provides a comprehensive notification service
 * with support for multiple channels (Email, SMS, Push, Webhook) and 
 * intelligent routing based on configurable rules.
 * 
 * @author Siraj Shaik
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}