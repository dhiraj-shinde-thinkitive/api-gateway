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

    @Cacheable(value = "apiKeys", key = "#apiKey")
    public Mono<ApiKeyMetadata> validateApiKey(String apiKey) {
        log.debug("Validating API key: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");

        return apiKeyClient.validateApiKey(apiKey)
                .flatMap(response -> {
                    if (response == null || !response.valid()) {
                        log.warn("API key validation failed or key not found");
                        return Mono.empty();
                    }

                    Integer rateLimit = response.rateLimit() != null ? response.rateLimit() : 100;

                    ApiKeyMetadata metadata = new ApiKeyMetadata(
                            response.customerId(),
                            response.permissions() != null ? new HashSet<>(response.permissions()) : new HashSet<>(),
                            rateLimit,
                            true
                    );

                    log.debug("API key validation successful for customer: {}", response.customerId());
                    return Mono.just(metadata);
                });
    }


    /**
     * Clear cache for API key (when key is revoked/updated in AKMS)
     */
    @CacheEvict(value = "apiKeys", key = "#apiKey")
    public void evictApiKeyFromCache(String apiKey) {
        log.debug("Evicting API key from cache: {}...", apiKey.substring(0, Math.min(8, apiKey.length())));
    }

    /**
     * Clear all API key cache (admin function)
     */
    @CacheEvict(value = "apiKeys", allEntries = true)
    public void clearAllCache() {
        log.info("Clearing all API key cache");
    }

    // DTOs and Records

    public record ApiKeyMetadata(
            String customerId,
            Set<String> permissions,
            Integer rateLimit,
//            LocalDateTime expiryDate,
            boolean active
    ) {}
}
