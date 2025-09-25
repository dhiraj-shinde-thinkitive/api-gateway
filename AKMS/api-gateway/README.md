# Sprouts API Gateway - Phase 1 (Corrected Architecture)

This is the Phase 1 implementation of the Sprouts API Gateway with **correct microservices architecture** - the gateway is stateless and delegates data operations to external services.

## Features Implemented

### ✅ Phase 1 - Basic Gateway
- **API Key Authentication**: Validates API keys via external AKMS service
- **Request Routing**: Routes requests to configured microservices
- **Rate Limiting**: Redis-based distributed rate limiting (per minute/hour/day)
- **Usage Logging**: Asynchronous logging via RabbitMQ to external logging service

## Corrected Architecture

```
Client Request → API Gateway → [Auth via AKMS + Rate Limit] → Target Service
                     ↓
              [Usage Logging via RabbitMQ]
                     ↓
              [External Logging Service]
```

### Service Separation:
- **Gateway**: Stateless routing, auth validation, rate limiting
- **AKMS**: API Key Management Service (separate microservice with PostgreSQL)
- **Logging Service**: Dedicated service for usage analytics (separate microservice)

## Prerequisites

- Java 21
- Maven 3.6+
- Redis Server (for caching & rate limiting)
- RabbitMQ Server (for message queuing)
- **AKMS Service** running on port 8084 (separate microservice)
- **Logging Service** (consumes RabbitMQ messages)

## Quick Start

### 1. Start Required Services

```bash
# Start Redis (default port 6379)
redis-server

# Start RabbitMQ (default port 5672)
rabbitmq-server
```

### 2. Build and Run

```bash
cd api-gateway
mvn clean install
mvn spring-boot:run
```

The gateway will start on port `8080`.

### 3. Test the Gateway

**Note**: For testing, you'll need the AKMS service running with test data, or mock the AKMS responses.

#### Test API Key Authentication

```bash
# Valid API Key (requires AKMS service with test data)
curl -H "Authorization: Api-Key your-test-api-key" \
     http://localhost:8080/api/test

# Invalid API Key - Should return 401
curl -H "Authorization: Api-Key invalid-key" \
     http://localhost:8080/api/test

# Missing API Key - Should return 401
curl http://localhost:8080/api/test
```

#### Test Rate Limiting

```bash
# Make multiple requests quickly to test rate limiting (requires AKMS)
for i in {1..10}; do
  curl -H "Authorization: Api-Key your-test-api-key" \
       http://localhost:8080/api/test
done
```

## Configuration

### Application Properties

Key configuration in `application.yml`:

```yaml
# AKMS Service Configuration
app:
  akms:
    base-url: http://localhost:8084
    timeout: 5000

# Cache TTL
app:
  cache:
    api-key-ttl: 300 # 5 minutes

# Rate Limiting Defaults
app:
  rate-limit:
    default-requests-per-minute: 100

# Message Queue
app:
  messaging:
    usage-log-queue: usage.logs
    usage-log-exchange: gateway.exchange
```

### Gateway Routes

Currently configured routes in `application.yml`:

- `/admin/**` → `http://localhost:8081` (Admin APIs)
- `/customer/**` → `http://localhost:8082` (Customer APIs) 
- `/api/**` → `http://localhost:8083` (Business APIs)

## External Services

### AKMS (API Key Management Service)
- **Port**: 8084
- **Database**: PostgreSQL
- **Responsibilities**: API key storage, validation, customer management

### Logging Service
- **Message Queue**: Consumes from `usage.logs` queue
- **Database**: Stores usage analytics and logs
- **Responsibilities**: Usage tracking, analytics, reporting

## Monitoring

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

## API Key Management

API key management is handled by the separate AKMS service. The gateway only:
- Validates keys via AKMS API calls
- Caches validation results in Redis
- Does not store any API key data locally

## Message Queue Integration

Usage logs are sent asynchronously to RabbitMQ:
- **Exchange**: `gateway.exchange`
- **Queue**: `usage.logs`
- **Dead Letter Queue**: `usage.logs.dlq`

## Caching

API key validation results are cached in Redis:
- **Cache Name**: `apiKeys`
- **TTL**: 5 minutes (configurable)
- **Eviction**: Manual via cache invalidation

## Next Steps (Phase 2)

- [ ] JWT integration for admin/customer APIs
- [ ] API Key Management Service (separate microservice)
- [ ] Customer self-service portal
- [ ] Advanced analytics and reporting
- [ ] Swagger/OpenAPI documentation

## Development Notes

- Uses Spring Cloud Gateway for reactive request handling
- BCrypt for API key hashing
- Lua scripts for atomic rate limiting in Redis
- Builder pattern for usage log construction
- Comprehensive error handling and logging

## ✅ **Architecture Corrections Applied**

Based on your feedback, the following corrections have been made:

1. **❌ Removed JPA Dependencies**: Gateway is now stateless
2. **❌ Removed JWT Dependencies**: Not needed for Phase 1  
3. **❌ Removed H2 Database**: No local database in gateway
4. **✅ Added AKMS Client**: WebClient for external API key validation
5. **✅ Updated Service Flow**: Gateway → AKMS → Target Service
6. **✅ Proper Separation**: Gateway only handles routing, auth validation, rate limiting

## Troubleshooting

### Common Issues:

1. **Redis Connection Failed**: Ensure Redis is running on localhost:6379
2. **RabbitMQ Connection Failed**: Ensure RabbitMQ is running on localhost:5672
3. **AKMS Connection Failed**: Ensure AKMS service is running on localhost:8084
4. **Rate Limiting Not Working**: Check Redis connectivity and Lua script execution
5. **Usage Logs Not Processed**: Check RabbitMQ queue and logging service connectivity

### Logs Location:
Check application logs for detailed error information and request tracing.

**The implementation now correctly follows microservices best practices! 🎯**
