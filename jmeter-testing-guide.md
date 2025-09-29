x# Apache JMeter Testing Guide for AKMS Application

## Prerequisites

### 1. System Requirements
- Apache JMeter 5.6.2 or higher
- Java 8 or higher
- Your AKMS application running (API Gateway on port 8081, AKMS Service on port 8084)
- Redis server running (for rate limiting)
- PostgreSQL database running

### 2. Install JMeter
```bash
# Option 1: Download from official website
# Visit https://jmeter.apache.org/download_jmeter.cgi

# Option 2: Using package managers
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install jmeter

# macOS with Homebrew
brew install jmeter

# Windows with Chocolatey
choco install jmeter
```

### 3. Start Your Services
```bash
# Start Redis (required for rate limiting)
redis-server

# Start PostgreSQL database
# Make sure your database is running on localhost:5434

# Start AKMS Service (port 8084)
cd AKMS/akms-service
./mvnw spring-boot:run

# Start API Gateway (port 8081)
cd AKMS/api-gateway
./mvnw spring-boot:run
```

## Step-by-Step Testing Process

### Step 1: Basic Setup and Health Check

1. **Verify Services Are Running**
   ```bash
   # Check API Gateway
   curl http://localhost:8081/actuator/health

   # Check AKMS Service
   curl http://localhost:8084/actuator/health
   ```

2. **Create Initial Test Data**
   ```bash
   # Generate a test API key directly through AKMS service
   curl -X POST http://localhost:8084/api/keys/generate \
     -H "Content-Type: application/json" \
     -d '{
       "customerId": "123e4567-e89b-12d3-a456-426614174000",
       "customerName": "JMeter Test Customer",
       "rateLimit": 1000,
       "expiresInDays": 30,
       "description": "API key for JMeter testing"
     }'
   ```

### Step 2: Authentication Testing

1. **Open JMeter**
   ```bash
   jmeter
   ```

2. **Load Authentication Test Plan**
   - File → Open → `jmeter-test-plans/01-authentication-test.jmx`

3. **Configure Variables**
   - Update `gateway_host` and `gateway_port` if different from localhost:8081
   - Update `akms_host` and `akms_port` if different from localhost:8084

4. **Run Authentication Tests**
   - Click the green "Start" button
   - Monitor results in "View Results Tree"
   - Expected outcomes:
     - ✅ Generate API Key: 201 Created
     - ✅ Valid API Key Test: 200 OK
     - ✅ Invalid API Key Test: 401 Unauthorized
     - ✅ Missing API Key Test: 401 Unauthorized

### Step 3: AKMS Endpoints Testing

1. **Load AKMS Endpoints Test Plan**
   - File → Open → `jmeter-test-plans/02-akms-endpoints-test.jmx`

2. **Run Endpoints Tests**
   - This test covers all AKMS endpoints:
     - GET /api/keys (with pagination)
     - GET /api/keys/{id}
     - GET /api/keys/customer/{customerId}
     - POST /api/keys/validate
     - GET /api/keys/expired
     - POST /api/keys/{id}/revoke
     - POST /api/keys/{id}/activate
     - GET /api/keys/status/{status}

3. **Monitor Results**
   - Check "Summary Report" for overall statistics
   - Verify all endpoints return 200 OK
   - Note response times and throughput

### Step 4: Rate Limiting Testing

1. **Load Rate Limiting Test Plan**
   - File → Open → `jmeter-test-plans/03-rate-limiting-test.jmx`

2. **Understand the Test Scenarios**
   - **Setup**: Creates API key with low rate limit (5 requests/minute)
   - **Burst Test**: Sends 10 rapid requests to trigger rate limiting
   - **Concurrent Test**: 5 users making requests simultaneously
   - **Recovery Test**: Waits 65 seconds, then tests if rate limit resets

3. **Run Rate Limiting Tests**
   - Expected results:
     - First 5 requests: 200 OK
     - Subsequent requests: 429 Too Many Requests
     - After wait period: 200 OK (rate limit reset)

4. **Verify Rate Limiting Headers**
   - Look for `X-RateLimit-Remaining` headers
   - Check `X-RateLimit-Reset` timestamps

### Step 5: Load Testing

1. **Prepare Test Data**
   - Create CSV file `api_keys.csv` in JMeter directory:
   ```csv
   api_key,customer_id
   your-api-key-1,customer-id-1
   your-api-key-2,customer-id-2
   ```

2. **Load Performance Test Plan**
   - File → Open → `jmeter-test-plans/04-load-testing.jmx`

3. **Configure Load Test Scenarios**
   - **Light Load**: 50 users, 5 loops, 30s ramp-up
   - **Medium Load**: 100 users, 10 loops, 60s ramp-up
   - **Stress Test**: 200 users, 15 loops, 120s ramp-up
   - **Spike Test**: 500 users, 3 loops, 30s ramp-up

4. **Run Load Tests Progressively**
   - Start with light load test
   - Monitor system resources (CPU, memory, database connections)
   - Gradually increase load
   - Document breaking points and performance degradation

## Performance Monitoring

### Key Metrics to Monitor

1. **Response Times**
   - Average: Should be < 500ms under normal load
   - 95th Percentile: Should be < 1000ms
   - 99th Percentile: Should be < 2000ms

2. **Throughput**
   - Requests per second (RPS)
   - Transactions per second (TPS)

3. **Error Rate**
   - Should be < 1% under normal conditions
   - Rate limiting (429) errors are expected in rate limit tests

4. **System Resources**
   ```bash
   # Monitor system resources
   htop

   # Monitor database connections
   psql -U sprouts-dev -d sprouts-ai -h localhost -p 5434 -c "SELECT count(*) FROM pg_stat_activity;"

   # Monitor Redis
   redis-cli info stats
   ```

### JMeter Listeners Configuration

1. **Summary Report** - Overall statistics
2. **View Results Tree** - Individual request details
3. **Aggregate Graph** - Visual performance metrics
4. **Graph Results** - Response time trends
5. **View Results in Table** - Detailed tabular data

## Test Scenarios and Expected Results

### 1. Functional Testing
- **API Key Generation**: 201 Created with valid API key
- **Authentication**: Valid keys pass, invalid keys rejected
- **CRUD Operations**: All endpoints respond correctly
- **Error Handling**: Proper error codes and messages

### 2. Security Testing
- **Authorization**: Only authenticated requests pass
- **API Key Validation**: Invalid/expired keys rejected
- **Rate Limiting**: Enforced per customer limits

### 3. Performance Testing
- **Response Times**: Within acceptable limits
- **Concurrent Users**: System handles load gracefully
- **Resource Usage**: No memory leaks or connection exhaustion

### 4. Rate Limiting Testing
- **Burst Protection**: Rapid requests properly limited
- **Per-Customer Limits**: Each customer has independent limits
- **Reset Behavior**: Limits reset after time window

## Troubleshooting Common Issues

### 1. Connection Refused Errors
```bash
# Check if services are running
netstat -tlnp | grep :8081
netstat -tlnp | grep :8084

# Check application logs
tail -f AKMS/api-gateway/logs/application.log
tail -f AKMS/akms-service/logs/application.log
```

### 2. Database Connection Issues
```bash
# Test database connectivity
psql -U sprouts-dev -d sprouts-ai -h localhost -p 5434 -c "SELECT 1;"
```

### 3. Redis Connection Issues
```bash
# Test Redis connectivity
redis-cli ping
```

### 4. Authentication Failures
- Ensure API keys are properly generated
- Check Authorization header format: `Api-Key <your-key>`
- Verify API key hasn't expired

### 5. Rate Limiting Not Working
- Confirm Redis is running and accessible
- Check rate limit configuration in API Gateway
- Verify customer ID mapping

## Best Practices

### 1. Test Environment Setup
- Use dedicated test database
- Isolate test Redis instance
- Monitor system resources during tests

### 2. Test Data Management
- Create fresh API keys for each test run
- Clean up test data after tests
- Use realistic customer IDs and data

### 3. Progressive Load Testing
- Start with single user tests
- Gradually increase load
- Monitor breaking points
- Document performance baselines

### 4. Results Analysis
- Save test results for comparison
- Create performance baseline reports
- Track performance trends over time
- Set up alerts for performance degradation

## Command Line Testing (Alternative)

If you prefer command-line testing:

```bash
# Run JMeter tests from command line
jmeter -n -t jmeter-test-plans/01-authentication-test.jmx -l results/auth-test-results.jtl

jmeter -n -t jmeter-test-plans/02-akms-endpoints-test.jmx -l results/endpoints-test-results.jtl

jmeter -n -t jmeter-test-plans/03-rate-limiting-test.jmx -l results/rate-limit-test-results.jtl

jmeter -n -t jmeter-test-plans/04-load-testing.jmx -l results/load-test-results.jtl

# Generate HTML reports
jmeter -g results/load-test-results.jtl -o results/html-report/
```

## Continuous Integration Integration

For CI/CD pipelines:

```bash
# Add to your pipeline script
#!/bin/bash
set -e

# Start services
docker-compose up -d redis postgres

# Wait for services to be ready
sleep 30

# Run performance tests
jmeter -n -t jmeter-test-plans/04-load-testing.jmx -l results/ci-load-test.jtl

# Check for failures
if grep -q "false" results/ci-load-test.jtl; then
    echo "Performance tests failed!"
    exit 1
fi

echo "Performance tests passed!"
```

This comprehensive testing approach ensures your AKMS application is thoroughly tested for functionality, performance, and reliability.