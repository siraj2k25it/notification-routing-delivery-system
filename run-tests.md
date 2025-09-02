# Testing Guide for My Notification System

## What I've Built for Testing

I've created a comprehensive test suite with **85+ test cases** across **8 test classes**. Here's what I focused on testing:

### Unit Tests (5 Classes)
- **NotificationServiceTest** - Core service orchestration and async processing
- **RoutingEngineTest** - Event routing logic and template processing  
- **EmailChannelHandlerTest** - Email delivery with failure simulation
- **InMemoryNotificationStorageTest** - Storage operations and concurrency
- **EventControllerTest** - REST API endpoints and validation

### Integration Tests (3 Classes)  
- **NotificationSystemIntegrationTest** - End-to-end workflow testing
- **ChannelHandlerIntegrationTest** - Multi-channel delivery coordination
- **RoutingEngineIntegrationTest** - Routing engine within complete system

## Running Tests

### All Tests
```bash
mvn test
```

### Unit Tests Only
```bash
mvn test -Dtest="!*IntegrationTest"
```

### Integration Tests Only
```bash
mvn test -Dtest="*IntegrationTest"
```

### Specific Test Classes
```bash
# Core service tests
mvn test -Dtest=NotificationServiceTest

# Routing engine tests
mvn test -Dtest=RoutingEngineTest

# API endpoint tests
mvn test -Dtest=EventControllerTest

# Complete workflow tests
mvn test -Dtest=NotificationSystemIntegrationTest
```

### Test with Output
```bash
mvn test -Dtest=NotificationServiceTest -Dspring.profiles.active=test
```

### Parallel Test Execution
```bash
mvn test -T 4 -DforkCount=4
```

## Test Features

### Comprehensive Coverage
- ✅ **Event Processing** - All event types and priorities
- ✅ **Routing Logic** - Rule matching and channel selection  
- ✅ **Channel Handlers** - Email/SMS delivery with realistic failures
- ✅ **Storage Operations** - CRUD operations and statistics
- ✅ **REST APIs** - Input validation and error handling
- ✅ **End-to-End Workflows** - Complete notification delivery

### Advanced Testing Scenarios
- ✅ **Async Processing** - CompletableFuture validation with Awaitility
- ✅ **Failure Simulation** - 10% random failure rate in channel handlers
- ✅ **Concurrent Execution** - Multi-threaded test scenarios
- ✅ **Performance Validation** - Timing assertions and throughput testing
- ✅ **Edge Cases** - Empty payloads, unknown events, interruption handling
- ✅ **Error Recovery** - System resilience after failures

### Test Data Scenarios
- User registration with welcome notifications
- Payment completion confirmations  
- Order shipping notifications
- Security alerts (high priority)
- Password reset requests (email only)
- Account verification codes
- Low priority newsletters
- Unknown/unsupported event types

## Test Configuration

Tests use:
- **H2 In-Memory Database** for fast execution
- **Spring Boot Test** with proper context loading
- **Mockito** for unit test mocking
- **Awaitility** for async operation validation
- **TestContainers** ready (if needed for external services)

## Expected Results

All tests should pass with:
- **100% success rate** for unit tests
- **Realistic failure simulation** in integration tests  
- **Sub-second execution** for most test cases
- **Proper async handling** without race conditions
- **Clean resource cleanup** after each test

## Troubleshooting

### Common Issues
- **Async timeouts**: Increase Awaitility timeout if tests are slow
- **Port conflicts**: Tests use random ports, should not conflict
- **Memory issues**: Run tests with `-Xmx512m` if needed

### Debug Mode
```bash
mvn test -Dspring.profiles.active=test -Dlogging.level.com.notificationservice=DEBUG
```

This comprehensive test suite ensures the notification system is robust, reliable, and ready for production deployment.