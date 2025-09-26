package com.sproutsai.api_gateway.client;

import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interface for API Key Management operations
 * Abstracts the communication with API Key Management Service (AKMS)
 */
public interface ApiKeyClient {

    /**
     * Validate API key with AKMS
     * @param apiKey the API key to validate
     * @return Mono containing validation response
     */
    Mono<ApiKeyValidationResponse> validateApiKey(String apiKey);

    /**
     * Get rate limit configuration for API key
     * @param customerId the customer identifier
     * @param apiKeyId the API key identifier
     * @return Mono containing rate limit configuration
     */
    Mono<RateLimitConfig> getRateLimitConfig(String customerId, Long apiKeyId);

    // DTOs for API Key operations

    record ApiKeyValidationRequest(String apiKey) {}

    /**
     * Response structure matching AKMS ValidateKeyResponse
     */
    record ApiKeyValidationResponse(
            boolean valid,
            String customerId,
            List<String> permissions,
            Integer rateLimit,
            LocalDateTime expiryDate,
            String reason
    ) {}

    /**
     * Rate limit configuration
     * @deprecated Kept for backward compatibility
     */
    @Deprecated
    record RateLimitConfig(
            Integer rateLimitPerMinute,
            Integer rateLimitPerHour,
            Integer rateLimitPerDay
    ) {}
}