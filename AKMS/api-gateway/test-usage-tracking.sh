#!/bin/bash

# API Gateway Usage Tracking Test Script
# Run this script to test all scenarios of request logging

GATEWAY_URL="http://localhost:8081"
VALID_API_KEY="your-valid-api-key-here"
INVALID_API_KEY="invalid-key-123"

echo "🚀 Testing API Gateway Usage Tracking Flow"
echo "=========================================="

# Test 1: Missing API Key (should log 401)
echo "📋 Test 1: Missing API Key (Expected: 401)"
curl -v -X GET "$GATEWAY_URL/api/users" \
  -H "Content-Type: application/json" 2>&1 | head -20

echo -e "\n⏱️  Sleeping 2 seconds...\n"
sleep 2

# Test 2: Invalid API Key (should log 401)
echo "📋 Test 2: Invalid API Key (Expected: 401)"
curl -v -X GET "$GATEWAY_URL/api/users" \
  -H "Authorization: Api-Key $INVALID_API_KEY" \
  -H "Content-Type: application/json" 2>&1 | head -20

echo -e "\n⏱️  Sleeping 2 seconds...\n"
sleep 2

# Test 3: Valid API Key (should log 200 or downstream status)
echo "📋 Test 3: Valid API Key (Expected: 200 or downstream status)"
curl -v -X GET "$GATEWAY_URL/api/users" \
  -H "Authorization: Api-Key $VALID_API_KEY" \
  -H "Content-Type: application/json" 2>&1 | head -20

echo -e "\n⏱️  Sleeping 2 seconds...\n"
sleep 2

# Test 4: Rate Limiting Test (multiple rapid requests)
echo "📋 Test 4: Rate Limiting Test (Expected: Some 429s)"
for i in {1..10}; do
  echo "Request $i:"
  curl -s -o /dev/null -w "Status: %{http_code}, Time: %{time_total}s\n" \
    -X GET "$GATEWAY_URL/api/users" \
    -H "Authorization: Api-Key $VALID_API_KEY"
  sleep 0.1
done

echo -e "\n⏱️  Sleeping 2 seconds...\n"
sleep 2

# Test 5: Non-API endpoints (should skip auth but still log)
echo "📋 Test 5: Non-API Endpoints (Expected: Skip auth, log usage)"
curl -v -X GET "$GATEWAY_URL/actuator/health" 2>&1 | head -15

echo -e "\n⏱️  Sleeping 2 seconds...\n"
sleep 2

# Test 6: Key generation endpoint (should skip auth but still log)
echo "📋 Test 6: Key Generation Endpoint (Expected: Skip auth, log usage)"
curl -v -X POST "$GATEWAY_URL/api/keys/generate" \
  -H "Content-Type: application/json" \
  -d '{"customerId": "test-customer"}' 2>&1 | head -20

echo -e "\n⏱️  Sleeping 2 seconds...\n"
sleep 2

# Test 7: Different HTTP methods
echo "📋 Test 7: Different HTTP Methods"
for method in POST PUT DELETE PATCH; do
  echo "Testing $method:"
  curl -s -o /dev/null -w "Status: %{http_code}\n" \
    -X $method "$GATEWAY_URL/api/test" \
    -H "Authorization: Api-Key $VALID_API_KEY" \
    -H "Content-Type: application/json" \
    -d '{"test": "data"}'
  sleep 0.5
done

echo -e "\n✅ Test script completed!"
echo "📊 Check the application logs and RabbitMQ queue for usage tracking messages"
echo "🔍 Look for log entries containing 'Usage log message sent to queue'"