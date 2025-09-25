package com.sproutsai.api_gateway.client;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Client for communicating with API Key Management Service (AKMS)
 */
@Component
@Slf4j
public class ApiKeyManagementClient {


    private final WebClient webClient;

    public ApiKeyManagementClient(@Value("${app.akms.base-url}") String akmsBaseUrl,
                                 @Value("${app.akms.timeout:5000}") int timeoutMs) {
        this.webClient = WebClient.builder()
            .baseUrl(akmsBaseUrl)
            .build();
    }

    /**
     * Validate API key with AKMS
     */
    public Mono<ApiKeyValidationResponse> validateApiKey(String apiKey) {
        log.debug("Validating API key with AKMS: {}...", apiKey.substring(0, Math.min(8, apiKey.length())));
        
        return webClient.post()
            .uri("/api/keys/validate")
            .bodyValue(new ApiKeyValidationRequest(apiKey))
            .retrieve()
            .bodyToMono(ApiKeyValidationResponse.class)
            .timeout(Duration.ofMillis(5000))
            .doOnSuccess(response -> log.debug("API key validation successful for customer: {}", 
                response != null ? response.customerId() : "unknown"))
            .doOnError(error -> log.error("API key validation failed", error))
            .onErrorReturn(new ApiKeyValidationResponse(false, null, null, null, null, null));
    }

    /**
     * Get rate limit configuration for API key
     */
    public Mono<RateLimitConfig> getRateLimitConfig(String customerId, Long apiKeyId) {
        log.debug("Getting rate limit config for customer: {}, apiKey: {}", customerId, apiKeyId);
        
        return webClient.get()
            .uri("/api/keys/{apiKeyId}/rate-limits", apiKeyId)
            .header("X-Customer-ID", customerId)
            .retrieve()
            .bodyToMono(RateLimitConfig.class)
            .timeout(Duration.ofMillis(3000))
            .doOnError(error -> log.error("Failed to get rate limit config", error))
            .onErrorReturn(new RateLimitConfig(100, 5000, 100000)); // Default limits
    }

    // DTOs for AKMS communication
    
    public record ApiKeyValidationRequest(String apiKey) {}
    
//    public record ApiKeyValidationResponse(
//        boolean valid,
//        Long keyId,
//        String customerId,
//        String tenantId,
//        String customerName,
//        Set<String> permissions,
//        Integer rateLimitPerMinute,
//        Integer rateLimitPerHour,
//        Integer rateLimitPerDay
//    ) {}
//
//    public record RateLimitConfig(
//        Integer rateLimitPerMinute,
//        Integer rateLimitPerHour,
//        Integer rateLimitPerDay
//    ) {}

    /**
     * Updated to match AKMS ValidateKeyResponse structure
     */
    public record ApiKeyValidationResponse(
            boolean valid,
            String customerId,
            List<String> permissions,
            Integer rateLimit,
            LocalDateTime expiryDate,
            String reason
    ) {}

    /**
     * Kept for backward compatibility but deprecated
     */
    @Deprecated
    public record RateLimitConfig(
            Integer rateLimitPerMinute,
            Integer rateLimitPerHour,
            Integer rateLimitPerDay
    ) {}
}
