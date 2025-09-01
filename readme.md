# Notification Routing & Delivery System

A robust, scalable backend service for routing and delivering notifications across multiple channels (Email, SMS, Push, Webhook) based on configurable rules. Built with **Java 21** and **Spring Boot 3.x** for modern performance and maintainability.

## 🚀 Features

- **Multi-Channel Support**: Email, SMS, Push notifications, and Webhooks
- **Rule-Based Routing**: Configurable routing rules based on event type, priority, and custom conditions
- **Async Processing**: Non-blocking event ingestion and notification delivery
- **Retry Mechanism**: Exponential backoff with configurable retry limits
- **Dead Letter Handling**: Failed notifications after max retries are moved to dead letter storage
- **Status Monitoring**: REST APIs to query delivery status and failed notifications
- **Extensible Architecture**: Easy to add new notification channels
- **Clean Architecture**: Separation of concerns with modular design
- **Modern Java**: Leverages Java 21 features like Records, Pattern Matching, and Virtual Threads
- **Observability**: Built-in metrics, health checks, and distributed tracing support

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   REST API      │    │  Routing Engine │    │   Notification  │
│   (Events)      │───▶│   (Rules)       │───▶│   Channels      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Storage       │    │  Retry Service  │    │  Dead Letter    │
│   (Events)      │    │ (Exponential    │    │   Storage       │
└─────────────────┘    │  Backoff)       │    └─────────────────┘
                       └─────────────────┘
```

### Key Components

1. **Event Controller**: REST API for event ingestion (`POST /events`)
2. **Routing Engine**: Applies configurable rules to determine notification channels
3. **Notification Channels**: Pluggable handlers for different delivery methods
4. **Retry Service**: Handles failed deliveries with exponential backoff
5. **Storage Layer**: In-memory storage for events, requests, and dead letters
6. **Status API**: Monitor delivery status and system health

## 🛠️ Technologies Used

- **Java 21** (LTS) with modern features:
  - Records for immutable data classes
  - Pattern matching and switch expressions
  - Virtual threads for improved concurrency
  - Text blocks and string templates
- **Spring Boot 3.3.x** with:
  - Spring Web for REST APIs
  - Spring Boot Actuator for monitoring
  - Spring Data JPA for persistence
  - Spring Cache for performance
- **Maven** for dependency management
- **Jackson** for JSON processing
- **JUnit 5** with modern testing features
- **Testcontainers** for integration testing
- **SpringDoc OpenAPI 3** for API documentation
- **Micrometer** for metrics and observability
- **Docker** with multi-stage builds

## 📋 Prerequisites

- **Java 21** or higher (JDK 21+)
- **Maven 3.9+**
- **Docker** and **Docker Compose** (optional)
- **Git**

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/notification-routing-system.git
cd notification-routing-system
```

### 2. Build the Project

```bash
# Clean and compile
mvn clean compile

# Package (includes tests)
mvn clean package
```

### 3. Run Tests

```bash
# Unit tests
mvn test

# Integration tests
mvn test -Dtest=*IntegrationTest

# Test coverage report
mvn test jacoco:report
```

### 4. Start the Application

**Option A: Using Maven (Development)**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Option B: Using JAR**
```bash
java -jar target/notification-routing-system-1.0.0.jar
```

**Option C: Using Docker Compose (Recommended)**
```bash
# Start all services (app + database + monitoring)
docker-compose up -d

# Development mode with hot reload
docker-compose -f docker-compose.yml -f docker-compose.dev.yml up
```

The application will start on `http://localhost:8080`

### 5. API Documentation

Once running, visit:
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **API Docs**: `http://localhost:8080/v3/api-docs`
- **Health Check**: `http://localhost:8080/actuator/health`

## 📖 API Usage

### Send an Event

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_REGISTERED",
    "recipient": "user@example.com",
    "payload": {
      "name": "John Doe",
      "email": "user@example.com"
    },
    "priority": "MEDIUM"
  }'
```

### Check Delivery Status

```bash
curl http://localhost:8080/api/v1/status/delivery/{eventId}
```

### View Failed Deliveries

```bash
curl http://localhost:8080/api/v1/status/failed
```

### System Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

## 🔧 Configuration

### Application Properties (`application.yml`)

```yaml
notification:
  retry:
    max-attempts: 3
    base-delay-seconds: 2
    max-delay-seconds: 300
  
  channels:
    email:
      enabled: true
      timeout-seconds: 30
    sms:
      enabled: true
      timeout-seconds: 15
```

### Routing Rules

The system comes with pre-configured routing rules:

1. **User Registration**: Email + SMS
2. **Payment Completed**: Email + Push
3. **High Priority Events**: Push + SMS
4. **Report Generated**: Email + Webhook
5. **Security Alerts**: Email + SMS + Push

## 🎯 Event Types & Examples

### User Registration

```json
{
  "eventType": "USER_REGISTERED",
  "recipient": "user@example.com",
  "payload": {
    "name": "John Doe"
  }
}
```
**Triggers**: Email + SMS notifications

### Payment Completed

```json
{
  "eventType": "PAYMENT_COMPLETED",
  "recipient": "customer@example.com",
  "payload": {
    "amount": "99.99",
    "orderId": "ORDER-123"
  }
}
```
**Triggers**: Email + Push notifications

### Security Alert

```json
{
  "eventType": "SECURITY_ALERT",
  "recipient": "user@example.com",
  "priority": "HIGH",
  "payload": {
    "alertType": "Suspicious login attempt"
  }
}
```
**Triggers**: Email + SMS + Push notifications

## 🔄 Retry Mechanism

- **Base Delay**: 2 seconds
- **Exponential Backoff**: delay = baseDelay * 2^retryCount
- **Jitter**: ±25% randomness to prevent thundering herd
- **Max Delay**: 300 seconds (5 minutes)
- **Max Attempts**: 3 retries
- **Dead Letter**: Failed notifications after max retries

## 📊 Monitoring & Observability

### Health Endpoint

```json
{
  "status": "healthy",
  "eventsProcessed": 1245,
  "notificationsSent": 3621,
  "deadLetterCount": 12,
  "timestamp": 1672531200000
}
```

### Delivery Status Response

```json
{
  "eventId": "evt-123",
  "requestId": "req-456",
  "channel": "EMAIL",
  "status": "SENT",
  "retryCount": 0,
  "createdAt": "2023-12-31T10:30:00",
  "lastAttemptAt": "2023-12-31T10:30:15"
}
```

## 🧪 Testing

### Run Unit Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn test -Dtest=*IntegrationTest
```

### Test Coverage

```bash
mvn test jacoco:report
```

View coverage report: `target/site/jacoco/index.html`

## 🚀 Extending the System

### Adding a New Notification Channel

1. **Create Handler**: Implement `NotificationChannelHandler`

```java
@Component
public class SlackChannelHandler implements NotificationChannelHandler {
    @Override
    public NotificationChannel getChannelType() {
        return NotificationChannel.SLACK;
    }
    
    @Override
    public boolean send(NotificationRequest request) throws Exception {
        // Slack-specific delivery logic
        return true;
    }
}
```

2. **Add to Enum**: Update `NotificationChannel` enum

```java
public enum NotificationChannel {
    EMAIL, SMS, PUSH, WEBHOOK, SLACK
}
```

3. **Configure Rules**: Add routing rules that include the new channel

### Adding Custom Routing Rules

```java
routingEngine.addRule(RoutingRule.builder()
    .name("VIP Customer Alert")
    .condition(event -> 
        "ORDER_PLACED".equals(event.getEventType()) &&
        "VIP".equals(event.getPayload().get("customerTier"))
    )
    .channels(Arrays.asList(NotificationChannel.EMAIL, NotificationChannel.PUSH))
    .messageTemplate("VIP order placed: ${orderId}")
    .priority(5)
    .build());
```

## 🏗️ Design Decisions & Trade-offs

### Architecture Choices

1. **In-Memory Storage**: 
   - **Pros**: Fast, simple, no external dependencies
   - **Cons**: Data lost on restart, not suitable for production scale
   - **Alternative**: Replace with JPA/Hibernate + PostgreSQL/MySQL

2. **Async Processing**: 
   - **Pros**: Non-blocking, better throughput
   - **Cons**: Eventual consistency, harder to debug
   - **Implementation**: Spring's `@Async` with thread pools

3. **Rule-Based Routing**: 
   - **Pros**: Flexible, configurable, extensible
   - **Cons**: Complex rules can impact performance
   - **Alternative**: Event-driven architecture with message queues

4. **Exponential Backoff**: 
   - **Pros**: Handles transient failures gracefully
   - **Cons**: Can delay urgent notifications
   - **Enhancement**: Priority-based retry schedules

### Assumptions Made

1. **Event Ordering**: Events can be processed out of order
2. **Channel Failures**: Simulated with random failure rates
3. **Message Templates**: Simple placeholder replacement
4. **Authentication**: Not implemented (would use JWT/OAuth2)
5. **Rate Limiting**: Not implemented (would use Redis/Bucket4j)

## 🚀 Production Readiness Enhancements

### Database Integration

Replace in-memory storage with persistent storage:

```java
@Entity
public class EventEntity {
    @Id
    private String eventId;
    // ... other fields with JPA annotations
}
```

### Message Queue Integration

For better scalability, integrate with message brokers:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
```

### Monitoring & Metrics

Add comprehensive monitoring:

```java
@Component
public class NotificationMetrics {
    private final MeterRegistry meterRegistry;
    private final Counter notificationsSent;
    private final Timer deliveryLatency;
}
```

### Security Enhancements

```java
@RestController
@PreAuthorize("hasRole('NOTIFICATION_PUBLISHER')")
public class EventController {
    // Secured endpoints
}
```

## 🐛 Known Limitations

1. **Storage**: In-memory storage is not persistent
2. **Scalability**: Single instance, no horizontal scaling
3. **Authentication**: No security implementation
4. **Rate Limiting**: No protection against spam/abuse
5. **Message Ordering**: No guaranteed message ordering
6. **Channel Configuration**: Hardcoded channel settings

## 🔮 Future Enhancements

- [ ] Database persistence (PostgreSQL/MySQL)
- [ ] Message queue integration (RabbitMQ/Apache Kafka)
- [ ] Redis caching for improved performance
- [ ] JWT-based authentication and authorization
- [ ] Rate limiting and throttling
- [ ] Advanced routing rules with complex expressions
- [ ] Template engine integration (Thymeleaf/Freemarker)
- [ ] Circuit breaker pattern for external services
- [ ] Distributed tracing (Zipkin/Jaeger)
- [ ] Container deployment (Docker/Kubernetes)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request
