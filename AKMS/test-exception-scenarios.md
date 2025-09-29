# Exception Handling Test Scenarios

## Manual Testing Guide

### **AKMS Service Tests**

#### 1. Test Customer Not Found Exception
```bash
# Test with invalid customer ID
curl -X POST http://localhost:8080/api/keys/generate \
-H "Content-Type: application/json" \
-d '{
  "customerId": "invalid-uuid-123",
  "name": "Test API Key",
  "permissions": ["READ", "WRITE"],
  "rateLimit": 100
}'

# Expected Response:
# {
#   "success": false,
#   "message": "Customer not found. Please check the customer ID and try again.",
#   "timestamp": "2024-01-15T10:30:00"
# }
```

#### 2. Test API Key Not Found Exception
```bash
# Test revoking non-existent API key
curl -X PUT http://localhost:8080/api/keys/invalid-key-id/revoke

# Expected Response:
# {
#   "success": false,
#   "message": "API key not found. Please check the key ID and try again.",
#   "timestamp": "2024-01-15T10:30:00"
# }
```

#### 3. Test Validation Errors
```bash
# Test with invalid data (missing required fields)
curl -X POST http://localhost:8080/api/keys/generate \
-H "Content-Type: application/json" \
-d '{
  "name": "",
  "permissions": []
}'

# Expected Response:
# {
#   "success": false,
#   "message": "Please check your input data and try again.",
#   "errors": {
#     "customerId": "Customer ID is required",
#     "name": "Name cannot be empty"
#   },
#   "timestamp": "2024-01-15T10:30:00"
# }
```

#### 4. Test API Key Validation
```bash
# Test with invalid API key
curl -X POST http://localhost:8080/api/keys/validate \
-H "Content-Type: application/json" \
-d '{
  "apiKey": "invalid-key-123"
}'

# Expected Response:
# {
#   "valid": false,
#   "reason": "Invalid, expired, or revoked API key"
# }
```

### **API Gateway Tests**

#### 1. Test Missing API Key
```bash
# Request without API key
curl -X GET http://localhost:8081/api/some-endpoint

# Expected Response:
# HTTP 401 Unauthorized
# Response handled by ApiKeyAuthFilter
```

#### 2. Test Invalid API Key
```bash
# Request with invalid API key
curl -X GET http://localhost:8081/api/some-endpoint \
-H "Authorization: Api-Key invalid-key-123"

# Expected Response:
# HTTP 401 Unauthorized
# Response handled by ApiKeyAuthFilter
```

#### 3. Test Service Unavailable
```bash
# Stop AKMS service first, then test API Gateway
curl -X GET http://localhost:8081/api/some-endpoint \
-H "Authorization: Api-Key valid-key"

# Expected Response:
# {
#   "success": false,
#   "message": "Service temporarily unavailable. Please try again later.",
#   "status": 503,
#   "timestamp": "2024-01-15T10:30:00"
# }
```

## **Quick Testing Commands**

### Run Unit Tests
```bash
# AKMS Service
cd AKMS/akms-service
./mvnw test -Dtest=GlobalExceptionHandlerTest

# API Gateway (if tests exist)
cd AKMS/api-gateway
./mvnw test
```

### Start Services for Manual Testing
```bash
# Terminal 1 - Start AKMS Service
cd AKMS/akms-service
./mvnw spring-boot:run

# Terminal 2 - Start API Gateway
cd AKMS/api-gateway
./mvnw spring-boot:run
```

### Create Test Data First
```bash
# 1. Create a customer first
curl -X POST http://localhost:8080/api/customers \
-H "Content-Type: application/json" \
-d '{
  "name": "Test Customer",
  "email": "test@example.com"
}'

# 2. Use the returned customer ID for API key generation
# 3. Test various error scenarios using the commands above
```

## **What to Look For**

✅ **Success Indicators:**
- Clear, user-friendly error messages
- Proper HTTP status codes (404, 400, 401, 409, 500)
- JSON responses with `success: false`
- No technical stack traces in responses
- Detailed logs in application logs for debugging

❌ **Issues to Watch For:**
- Technical error messages exposed to users
- Missing error handling (500 errors with stack traces)
- Incorrect HTTP status codes
- Inconsistent response formats