package com.sproutsai.api_gateway.service;

import com.sproutsai.api_gateway.client.ApiKeyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Service for API Key validation via AKMS
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyClient apiKeyClient;

    public ApiKeyService(ApiKeyClient apiKeyClient) {
        this.apiKeyClient = apiKeyClient;
    }

//    @Cacheable(value = "apiKeys", key = "#apiKey")
@Cacheable(value = "apiKeys", key = "#apiKey", condition = "true", unless = "#result == null")
public Mono<ApiKeyMetadata> validateApiKey(String apiKey) {
        String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "...";
        log.info("CACHE_LOOKUP - Attempting API key validation: {}", maskedKey);

        return apiKeyClient.validateApiKey(apiKey)
                .flatMap(response -> {
                    if (response == null || !response.valid()) {
                        log.warn("VALIDATION_FAILED - API key validation failed or key not found: {}", maskedKey);
                        return Mono.empty();
                    }

                    Integer rateLimit = response.rateLimit() != null ? response.rateLimit() : 100;

                    ApiKeyMetadata metadata = new ApiKeyMetadata(
                            response.customerId(),
                            response.apiKeyId(),
                            response.permissions() != null ? new HashSet<>(response.permissions()) : new HashSet<>(),
                            rateLimit,
                            true
                    );

                    log.info("VALIDATION_SUCCESS - API key validated and cached: key={}, customer={}, rateLimit={}",
                            maskedKey, response.customerId(), rateLimit);
                    return Mono.just(metadata);
                })
                .doOnNext(metadata -> log.debug("CACHE_STORE - Caching API key metadata for: {}", maskedKey))
                .doOnError(error -> log.error("VALIDATION_ERROR - API key validation error for {}: {}", maskedKey, error.getMessage()));
    }


    /**
     * Clear cache for API key (when key is revoked/updated in AKMS)
     */
    @CacheEvict(value = "apiKeys", key = "#apiKey")
    public void evictApiKeyFromCache(String apiKey) {
        String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "...";
        log.info("CACHE_EVICT - Evicting API key from cache: {}", maskedKey);
    }

    /**
     * Clear all API key cache (admin function)
     */
    @CacheEvict(value = "apiKeys", allEntries = true)
    public void clearAllCache() {
        log.info("CACHE_CLEAR_ALL - Clearing all API key cache entries");
    }

    // DTOs and Records

    public record ApiKeyMetadata(
            String customerId,
            String apiKeyId,
            Set<String> permissions,
            Integer rateLimit,
//            LocalDateTime expiryDate,
            boolean active
    ) {}
}
