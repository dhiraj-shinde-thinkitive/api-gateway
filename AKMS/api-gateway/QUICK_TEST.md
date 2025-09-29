# 🚀 Quick Test Guide - API Gateway Usage Tracking

## **Option 1: 30-Second Quick Test**

```bash
# 1. Start services
docker-compose -f docker-compose-test.yml up -d

# 2. Start API Gateway
mvn spring-boot:run

# 3. Run automated tests
./test-usage-tracking.sh
```

## **Option 2: Manual Step-by-Step**

### **1. Setup (2 minutes)**

```bash
# Terminal 1: Start dependencies
docker-compose -f docker-compose-test.yml up -d

# Terminal 2: Start API Gateway
mvn spring-boot:run

# Terminal 3: Watch logs
tail -f logs/api-gateway.log | grep -E "(Usage log|FLOW_START|FLOW_END)"
```

### **2. Test All Scenarios (1 minute)**

```bash
# Missing API Key → 401, logged
curl -v http://localhost:8081/api/users

# Invalid API Key → 401, logged
curl -v -H "Authorization: Api-Key invalid-123" http://localhost:8081/api/users

# Non-API endpoint → 200, logged
curl -v http://localhost:8081/actuator/health

# Test endpoint → 200, logged
curl -v http://localhost:8081/test/health
```

### **3. Verify Results (30 seconds)**

```bash
# Check RabbitMQ Management: http://localhost:15672 (guest/guest)
# Look for messages in 'usage.logs' queue

# Or check queue via API
curl http://localhost:8081/test/queue-status
```

## **Expected Results**

✅ **4 usage log messages** in RabbitMQ queue
✅ **Each message contains:** timestamp, endpoint, status, response time, API key info
✅ **Different scenarios:** 401 (missing key), 401 (invalid key), 200 (actuator), 200 (test)
✅ **Application logs show:** "Usage log message sent to queue"

## **Integration Tests**

```bash
# Run comprehensive integration tests with Testcontainers
mvn test -Dtest=UsageTrackingIntegrationTest

# Or run all tests
mvn test
```

That's it! Your usage tracking is working if you see messages flowing through the RabbitMQ queue.