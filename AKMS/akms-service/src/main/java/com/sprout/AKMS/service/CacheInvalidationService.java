package com.sprout.AKMS.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Service for invalidating cache in API Gateway
 * Called when API key status changes (revoked/expired/deleted)
 */
@Service
@Slf4j
public class CacheInvalidationService {

    private final WebClient webClient;

    public CacheInvalidationService(@Value("${app.api-gateway.base-url:http://localhost:8080}") String apiGatewayBaseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(apiGatewayBaseUrl)
                .build();

        log.info("CacheInvalidationService initialized with API Gateway URL: {}", apiGatewayBaseUrl);
    }

    /**
     * Invalidate API key in API Gateway cache
     * Since we don't store raw keys, we'll clear all cache when key status changes
     * This is a temporary solution - ideally we'd track raw keys securely
     * @param keyId The API key database ID for logging
     */
    //todo: unhash the api key from DB and then remove the specific api key from redis cache do not remove all the keys directly
    public void invalidateApiKeyCache(Long keyId) {
        log.info("CACHE_INVALIDATION_START - Invalidating cache due to key status change: keyId={}", keyId);

        // For now, clear all cache when any key status changes
        // This ensures revoked keys are immediately invalidated
        webClient.post()
                .uri("/internal/cache/clear-all")
                .retrieve()
                .onStatus(status -> status.isError(), response -> Mono.error(new RuntimeException("Cache invalidation failed")))

                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(response -> {
                    log.info("CACHE_INVALIDATION_SUCCESS - Successfully invalidated cache for keyId: {}", keyId);
                })
                .doOnError(error -> {
                    log.error("CACHE_INVALIDATION_ERROR - Error invalidating cache for keyId: {}, error: {}", keyId, error.getMessage());
                })
                .onErrorResume(error -> {
                    // Don't fail the main operation if cache invalidation fails
                    log.warn("CACHE_INVALIDATION_FALLBACK - Cache invalidation failed, continuing with main operation");
                    return Mono.empty();
                })
                .subscribe(); // Fire and forget - don't block main operation
    }

    /**
     * Clear all cache entries in API Gateway
     * Admin function for emergency cache clearing
     */
    public void clearAllCache() {
        log.info("CACHE_CLEAR_ALL_START - Clearing all cache in API Gateway");

        webClient.post()
                .uri("/internal/cache/clear-all")
                .retrieve()
                .onStatus(status -> status.isError(),response -> Mono.error(new RuntimeException("Cache invalidation failed")))
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(response -> {
                    log.info("CACHE_CLEAR_ALL_SUCCESS - Successfully cleared all cache in API Gateway");
                })
                .doOnError(error -> {
                    log.error("CACHE_CLEAR_ALL_ERROR - Error clearing all cache: {}", error.getMessage());
                })
                .onErrorResume(error -> {
                    log.warn("CACHE_CLEAR_ALL_FALLBACK - Clear all cache failed, operation completed anyway");
                    return Mono.empty();
                })
                .subscribe();
    }
}