package com.sproutsai.api_gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/**
 * Service for handling rate limiting using Redis
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RedisTemplate<String, String> redisTemplate;

    // Lua script for atomic rate limiting using sliding window
    private static final String RATE_LIMIT_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])

        -- Remove expired entries
        redis.call('ZREMRANGEBYSCORE', key, 0, current_time - window)

        -- Count current requests in window
        local current_requests = redis.call('ZCARD', key)

        if current_requests < limit then
            -- Add current request
            redis.call('ZADD', key, current_time, current_time)
            -- Set expiry for the key
            redis.call('EXPIRE', key, window)
            return {1, limit - current_requests - 1}
        else
            return {0, 0}
        end
        """;

    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if request is within rate limit
     */
    public RateLimitResult checkRateLimit(String customerId, Long apiKeyId, Integer limitPerMinute) {
        log.debug("RATE_LIMIT_CHECK - Starting rate limit check: customer={}, apiKey={}, limit={}", customerId, apiKeyId, limitPerMinute);

        long currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        try {
            // Check minute limit
            log.debug("RATE_LIMIT_MINUTE - Checking minute window limit: customer={}, limit={}", customerId, limitPerMinute);
            RateLimitCheck minuteCheck = checkLimit(customerId, apiKeyId, "minute", limitPerMinute, 60, currentTime);

            if (!minuteCheck.allowed()) {
                log.warn("RATE_LIMIT_EXCEEDED - Minute window limit exceeded: customer={}, apiKey={}, remaining={}", customerId, apiKeyId, minuteCheck.remaining());
                return new RateLimitResult(false, RateLimitWindow.MINUTE, minuteCheck.remaining(), calculateResetTime(60));
            }

            // All checks passed
            long minRemaining = minuteCheck.remaining();
            log.info("RATE_LIMIT_ALLOWED - Request allowed: customer={}, remaining={}", customerId, minRemaining);

            return new RateLimitResult(true, null, minRemaining, null);

        } catch (Exception e) {
            log.error("RATE_LIMIT_ERROR - Error checking rate limit for customer: {}, apiKey: {}", customerId, apiKeyId, e);
            // In case of Redis failure, allow the request (fail open)
            log.warn("RATE_LIMIT_FAILOPEN - Allowing request due to Redis error");
            return new RateLimitResult(true, null, -1, null);
        }
    }

    /**
     * Get current rate limit status without consuming a request
     */
    public RateLimitStatus getRateLimitStatus(String customerId, Long apiKeyId,
                                            Integer limitPerMinute, Integer limitPerHour, Integer limitPerDay) {

        try {
            long minuteUsage = getCurrentUsage(customerId, apiKeyId, "minute", 60);
            long hourUsage = getCurrentUsage(customerId, apiKeyId, "hour", 3600);
            long dayUsage = getCurrentUsage(customerId, apiKeyId, "day", 86400);

            return new RateLimitStatus(
                limitPerMinute, Math.max(0, limitPerMinute - minuteUsage), minuteUsage,
                limitPerHour, Math.max(0, limitPerHour - hourUsage), hourUsage,
                limitPerDay, Math.max(0, limitPerDay - dayUsage), dayUsage
            );

        } catch (Exception e) {
            log.error("Error getting rate limit status for customer: {}, apiKey: {}", customerId, apiKeyId, e);
            return new RateLimitStatus(limitPerMinute, limitPerMinute, 0,
                                     limitPerHour, limitPerHour, 0,
                                     limitPerDay, limitPerDay, 0);
        }
    }

    /**
     * Reset rate limits for a customer/API key (admin function)
     */
    public void resetRateLimits(String customerId, Long apiKeyId) {
        log.info("Resetting rate limits for customer: {}, apiKey: {}", customerId, apiKeyId);

        try {
            String minuteKey = buildRateLimitKey(customerId, apiKeyId, "minute");
            String hourKey = buildRateLimitKey(customerId, apiKeyId, "hour");
            String dayKey = buildRateLimitKey(customerId, apiKeyId, "day");

            redisTemplate.delete(minuteKey);
            redisTemplate.delete(hourKey);
            redisTemplate.delete(dayKey);

            log.info("Rate limits reset successfully for customer: {}, apiKey: {}", customerId, apiKeyId);

        } catch (Exception e) {
            log.error("Error resetting rate limits for customer: {}, apiKey: {}", customerId, apiKeyId, e);
        }
    }

    // Private helper methods
//    todo : understand this method

    private RateLimitCheck checkLimit(String customerId, Long apiKeyId, String window, Integer limit, int windowSeconds, long currentTime) {

        String key = buildRateLimitKey(customerId, apiKeyId, window);

        @SuppressWarnings("rawtypes")
        RedisScript<List> script = RedisScript.of(RATE_LIMIT_SCRIPT, List.class);

        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(script,
            Collections.singletonList(key),
            String.valueOf(windowSeconds),
            String.valueOf(limit),
            String.valueOf(currentTime));

        if (result != null && result.size() == 2) {
            boolean allowed = ((Number) result.get(0)).intValue() == 1;
            long remaining = ((Number) result.get(1)).longValue();
            return new RateLimitCheck(allowed, remaining);
        }

        // Fallback - allow request
        return new RateLimitCheck(true, limit - 1);
    }

    private long getCurrentUsage(String customerId, Long apiKeyId, String window, int windowSeconds) {
        String key = buildRateLimitKey(customerId, apiKeyId, window);
        long currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        // Remove expired entries
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, currentTime - windowSeconds);

        // Count current entries
        Long count = redisTemplate.opsForZSet().count(key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return count != null ? count : 0;
    }

    private String buildRateLimitKey(String customerId, Long apiKeyId, String window) {
        return String.format("rate_limit:%s:%s:%s", customerId, apiKeyId, window);
    }

    private LocalDateTime calculateResetTime(int windowSeconds) {
        return LocalDateTime.now().plusSeconds(windowSeconds);
    }

    // DTOs and Records
    //todo: need to change resetTime to Instant
    public record RateLimitResult(
        boolean allowed,
        RateLimitWindow limitedBy,
        long remaining,
        LocalDateTime resetTime
    ) {}

    public record RateLimitStatus(
        int minuteLimit, long minuteRemaining, long minuteUsed,
        int hourLimit, long hourRemaining, long hourUsed,
        int dayLimit, long dayRemaining, long dayUsed
    ) {}

    private record RateLimitCheck(boolean allowed, long remaining) {}

    public enum RateLimitWindow {
        MINUTE, HOUR, DAY
    }
}
