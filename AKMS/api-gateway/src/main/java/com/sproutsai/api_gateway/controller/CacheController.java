package com.sproutsai.api_gateway.controller;

import com.sproutsai.api_gateway.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for cache management operations
 * Internal endpoints for AKMS service to invalidate cache
 */
@RestController
@RequestMapping("/internal/cache")
@RequiredArgsConstructor
@Slf4j
public class CacheController {

    private final ApiKeyService apiKeyService;

    /**
     * Evict specific API key from cache
     * Called by AKMS when key status changes (revoked/expired/deleted)
     */
    @PostMapping("/evict/{apiKey}")
    public ResponseEntity<Map<String, String>> evictApiKey(@PathVariable String apiKey) {
        String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "...";
        log.info("CACHE_EVICT_REQUEST - Evicting API key from cache: {}", maskedKey);

        try {
            apiKeyService.evictApiKeyFromCache(apiKey);
            log.info("CACHE_EVICT_SUCCESS - Successfully evicted API key from cache: {}", maskedKey);

            return ResponseEntity.ok(Map.of("status", "success", "message", "API key evicted from cache successfully"));
        } catch (Exception e) {
            log.error("CACHE_EVICT_ERROR - Failed to evict API key from cache: {}, error: {}", maskedKey, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", "Failed to evict API key from cache"));
        }
    }

    /**
     * Clear all API key cache entries
     * Admin endpoint for emergency cache clearing
     */
    @PostMapping("/clear-all")
    public ResponseEntity<Map<String, String>> clearAllCache() {
        log.info("CACHE_CLEAR_ALL_REQUEST - Clearing all API key cache");

        try {
            apiKeyService.clearAllCache();
            log.info("CACHE_CLEAR_ALL_SUCCESS - Successfully cleared all API key cache");
            return ResponseEntity.ok(Map.of("status", "success","message", "All cache entries cleared successfully"));
        } catch (Exception e) {
            log.error("CACHE_CLEAR_ALL_ERROR - Failed to clear cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("status", "error","message", "Failed to clear cache"));
        }
    }

    /**
     * Get cache statistics (optional - for monitoring)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, String>> getCacheStats() {
        log.debug("CACHE_STATS_REQUEST - Getting cache statistics");
        return ResponseEntity.ok(Map.of("status", "success", "cache_name", "apiKeys", "ttl", "15 minutes", "message", "Cache is operational"));
    }
}