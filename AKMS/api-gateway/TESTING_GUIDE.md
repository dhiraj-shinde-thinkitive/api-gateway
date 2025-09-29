# API Gateway Usage Tracking - Testing Guide

This guide provides comprehensive testing methods for the request logging and usage tracking functionality.

## 🚀 Quick Start Testing

### 1. **Start Required Services**

```bash
# Start RabbitMQ and Redis
docker-compose -f docker-compose-test.yml up -d

# Verify services are running
docker ps
curl http://localhost:15672  # RabbitMQ Management UI (guest/guest)
```

### 2. **Start API Gateway**

```bash
# From the api-gateway directory
mvn spring-boot:run

# Or build and run
mvn clean package
java -jar target/api-gateway-0.0.1-SNAPSHOT.jar
```

### 3. **Run Automated Tests**

```bash
# Run the test script
./test-usage-tracking.sh
```

## 📋 Manual Testing Scenarios

### **Scenario 1: Missing API Key (401)**

```bash
curl -v http://localhost:8081/api/users

# Expected:
# - HTTP 401 Unauthorized
# - Usage log with: status=401, error="Missing API key"
```

### **Scenario 2: Invalid API Key (401)**

```bash
curl -v -H "Authorization: Api-Key invalid-key-123" \
  http://localhost:8081/api/users

# Expected:
# - HTTP 401 Unauthorized
# - Usage log with: status=401, error="Invalid API key", apiKeyUsed="invalid-..."
```

### **Scenario 3: Valid API Key (depends on downstream)**

```bash
# You'll need a real API key from your AKMS service
curl -v -H "Authorization: Api-Key your-valid-key-here" \
  http://localhost:8081/api/users

# Expected:
# - HTTP status depends on AKMS service response
# - Usage log with: customerId, apiKeyId, responseTime
```

### **Scenario 4: Rate Limiting (429)**

```bash
# Send multiple rapid requests with same API key
for i in {1..20}; do
  curl -s -o /dev/null -w "Status: %{http_code}\n" \
    -H "Authorization: Api-Key your-valid-key-here" \
    http://localhost:8081/api/users
done

# Expected:
# - First few: HTTP 200/success
# - Later requests: HTTP 429 Too Many Requests
# - Usage logs for both success and rate-limited requests
```

### **Scenario 5: Non-API Endpoints (Skip Auth)**

```bash
# These endpoints skip authentication but still log usage
curl -v http://localhost:8081/actuator/health
curl -v http://localhost:8081/test/health

# Expected:
# - HTTP 200 (assuming endpoints exist)
# - Usage logs with: customerId=null, no error
```

### **Scenario 6: Test Different HTTP Methods**

```bash
# Test various HTTP methods
curl -X POST -H "Authorization: Api-Key your-key" \
  -H "Content-Type: application/json" \
  -d '{"test":"data"}' \
  http://localhost:8081/api/test

curl -X PUT -H "Authorization: Api-Key your-key" \
  http://localhost:8081/api/test/123

curl -X DELETE -H "Authorization: Api-Key your-key" \
  http://localhost:8081/api/test/123

# Expected:
# - Each method logged separately
# - Usage logs show correct httpMethod
```

## 🔍 Monitoring and Verification

### **1. Application Logs**

```bash
# Watch application logs for usage tracking messages
tail -f logs/api-gateway.log | grep -E "(Usage log|FLOW_START|FLOW_END)"

# Look for these log patterns:
# - "FLOW_START [trace-id] - Incoming request: method=GET, path=/api/users"
# - "Usage log message sent to queue for customer: customer-123"
# - "FLOW_END [trace-id] - Request completed: status=200, responseTime=150ms"
```

### **2. RabbitMQ Management Console**

```bash
# Open in browser: http://localhost:15672
# Login: guest/guest

# Check:
# - Exchange: gateway.exchange
# - Queue: usage.logs
# - Message rates and counts
# - Browse messages to see actual usage data
```

### **3. Test Endpoints for Verification**

```bash
# Check queue connectivity
curl http://localhost:8081/test/queue-status

# Send test message to queue
curl -X POST http://localhost:8081/test/send-test-message \
  -H "Content-Type: application/json" \
  -d '{"test": "verification"}'

# Test response timing
curl http://localhost:8081/test/simulate-delay/1000

# Test error responses
curl http://localhost:8081/test/simulate-error/500

# Check gateway headers
curl -H "Authorization: Api-Key test" \
  http://localhost:8081/test/headers
```

## 🧪 Integration Tests

### **Run Automated Integration Tests**

```bash
# Requires Docker for Testcontainers
mvn test -Dtest=UsageTrackingIntegrationTest

# Or run all tests
mvn test
```

### **Test Classes Available:**

1. **`UsageTrackingIntegrationTest`** - Tests complete flow with real RabbitMQ
2. **`ApiGatewayApplicationTests`** - Basic application context loading

## 📊 Expected Usage Log Message Format

```json
{
  "apiKeyId": 12345,
  "customerId": "customer-abc123",
  "tenantId": null,
  "customerName": null,
  "endpoint": "/api/users",
  "httpMethod": "GET",
  "requestPath": "/api/users",
  "queryParams": "?limit=10&offset=0",
  "userAgent": "Mozilla/5.0...",
  "clientIp": "192.168.1.100",
  "responseStatus": 200,
  "responseTimeMs": 150,
  "requestSizeBytes": 0,
  "responseSizeBytes": null,
  "errorMessage": null,
  "sessionId": "session-12345",
  "correlationId": "uuid-67890",
  "timestamp": "2025-09-29T19:00:00.123",
  "apiKeyUsed": "abc123de..."
}
```

## 🚨 Troubleshooting

### **Common Issues:**

1. **RabbitMQ Connection Refused**
   ```bash
   # Start RabbitMQ
   docker-compose -f docker-compose-test.yml up rabbitmq -d

   # Check if running
   docker logs api-gateway-rabbitmq
   ```

2. **No Usage Messages in Queue**
   ```bash
   # Check application logs for errors
   tail -f logs/api-gateway.log | grep -i error

   # Verify queue configuration
   curl http://localhost:8081/test/queue-status
   ```

3. **Redis Connection Issues**
   ```bash
   # Start Redis
   docker-compose -f docker-compose-test.yml up redis -d

   # Test connection
   redis-cli -h localhost ping
   ```

4. **API Key Validation Fails**
   ```bash
   # Ensure AKMS service is running on port 8084
   curl http://localhost:8084/actuator/health

   # Check application.yml for correct AKMS URL
   ```

## 📈 Performance Testing

### **Load Testing with Apache Bench (ab)**

```bash
# Install apache bench
sudo apt-get install apache2-utils

# Test with valid API key
ab -n 100 -c 10 -H "Authorization: Api-Key your-key" \
  http://localhost:8081/api/test

# Test rate limiting
ab -n 200 -c 20 -H "Authorization: Api-Key your-key" \
  http://localhost:8081/api/test
```

### **Monitor Performance:**

- Watch for response time degradation
- Monitor RabbitMQ queue depth
- Check memory usage during load
- Verify all requests are logged

## ✅ Success Criteria

A successful test should show:

1. **All HTTP requests logged** (success, failure, rate-limited)
2. **Accurate timing data** (responseTimeMs > 0)
3. **Proper error categorization** (missing key, invalid key, rate limit)
4. **RabbitMQ messages flowing** to usage.logs queue
5. **No application errors** in logs
6. **Performance maintained** under load

This testing strategy ensures your usage tracking is working correctly and ready for production billing and analytics.