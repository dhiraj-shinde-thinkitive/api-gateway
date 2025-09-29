package com.sproutsai.api_gateway.filter;

import com.sproutsai.api_gateway.service.ApiKeyService;
import com.sproutsai.api_gateway.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter for API Key authentication and rate limiting.
 */
@Component
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    private final ApiKeyService apiKeyService;
    private final RateLimitService rateLimitService;
//    private final UsageTrackingService usageTrackingService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, RateLimitService rateLimitService) {
        this.apiKeyService = apiKeyService;
        this.rateLimitService = rateLimitService;
//        this.usageTrackingService = usageTrackingService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String traceId = generateTraceId();

        log.info("FLOW_START [{}] - Incoming request: method={}, path={}", traceId, exchange.getRequest().getMethod(), path);

        // Step 1: Log incoming request
        logRequest(exchange, extractApiKey(exchange));

        // Skip API key authentication for actuator endpoints
        if (path.startsWith("/actuator/")) {
            log.info("FLOW_SKIP [{}] - Skipping API key authentication for actuator endpoint: {}", traceId, path);
            return chain.filter(exchange);
        }

        // Skip API key authentication for non-API paths (if any)
        if (!path.startsWith("/api/")) {
            log.info("FLOW_SKIP [{}] - Skipping API key authentication for non-API path: {}", traceId, path);
            return chain.filter(exchange);
        }

        // Skip authentication for /api/keys/generate
        if (path.equals("/api/keys/generate")) {
            log.info("FLOW_SKIP [{}] - Skipping API key authentication for key generation endpoint", traceId);
            return chain.filter(exchange);
        }

        // Skip authentication for internal cache management endpoints
        if (path.startsWith("/internal/cache")) {
            log.info("FLOW_SKIP [{}] - Skipping API key authentication for internal cache endpoint: {}", traceId, path);
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String apiKey = extractApiKey(exchange);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AUTH_FAILED [{}] - Missing API key for request: {}", traceId, path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        log.info("AUTH_START [{}] - Starting API key validation for path: {}", traceId, path);

        // Step 2: Validate API key reactively
        return apiKeyService.validateApiKey(apiKey)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("AUTH_FAILED [{}] - Invalid API key: {}", traceId, apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return Mono.error(new RuntimeException("Invalid API key"));
                }))
                .flatMap(metadata -> {
                    if (metadata == null) {
                        log.warn("AUTH_FAILED [{}] - Invalid API key: {}", traceId, apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    log.info("AUTH_SUCCESS [{}] - API key validated for customer: {}", traceId, metadata.customerId());

                    // Step 3: Rate limiting - use same rate limit for all periods
                    log.info("RATE_LIMIT_START [{}] - Checking rate limits for customer: {}, limit: {}",
                            traceId, metadata.customerId(), metadata.rateLimit());

                    RateLimitService.RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(metadata.customerId(), Long.valueOf(metadata.apiKeyId()), metadata.rateLimit());

                    // 🟩 Always add rate-limit headers regardless of allowed/exceeded
                    exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(metadata.rateLimit()));
                    exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(rateLimitResult.remaining()));


                    if (!rateLimitResult.allowed()) {
                            log.warn("RATE_LIMIT_EXCEEDED [{}] - Customer: {}, limited by: {}, remaining: {}", traceId, metadata.customerId(), rateLimitResult.limitedBy(), rateLimitResult.remaining());
                        exchange.getResponse().getHeaders().add("X-RateLimit-Reset", rateLimitResult.resetTime().toString());

                        // Add rate limit headers
//                        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(rateLimitResult.remaining()));
//                        exchange.getResponse().getHeaders().add("X-RateLimit-Reset", rateLimitResult.resetTime().toString());

//                        exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(rateLimitResult.resetTime().toEpochSecond())); // if resetTime is Instant

                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }

                    // Step 4: Enrich request with metadata
                    log.debug("REQUEST_ENRICHMENT [{}] - Adding customer ID header: {}", traceId, metadata.customerId());

                    ServerWebExchange enrichedExchange = exchange.mutate()
                            .request(r -> r.headers(h -> {
                                h.add("X-Api-Client-Id", metadata.customerId());
                                h.add("X-Trace-Id", traceId);
                            }))
                            .build();

                    // Step 5: Forward request and log usage
                    log.info("REQUEST_FORWARD [{}] - Forwarding authenticated request to downstream service", traceId);

                    return chain.filter(enrichedExchange)
                            .doFinally(signalType -> {
                                long responseTime = System.currentTimeMillis() - startTime;
                                int statusCode = enrichedExchange.getResponse().getStatusCode() != null ? enrichedExchange.getResponse().getStatusCode().value() : 200;

                                log.info("FLOW_END [{}] - Request completed: status={}, responseTime={}ms, customer={}", traceId, statusCode, responseTime, metadata.customerId());
                            });
                })
                .onErrorResume(throwable -> {
                    if (throwable.getMessage() != null && throwable.getMessage().contains("Invalid API key")) {
                        // The error was already set in the response status above
                        return exchange.getResponse().setComplete();
                    }
                    log.error("Unexpected error in API key validation", throwable);
                    exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    return exchange.getResponse().setComplete();
                });
    }

    private String extractApiKey(ServerWebExchange exchange) {
        // Extract from Authorization header with "Api-Key " prefix as per requirements
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Api-Key ")) {
            return authHeader.substring(8); // Remove "Api-Key " prefix
        }

        // Fallback to query param for testing purposes
        String queryApiKey = exchange.getRequest().getQueryParams().getFirst("api_key");
        if (queryApiKey != null) {
            return queryApiKey;
        }

        return null;
    }

    private void logRequest(ServerWebExchange exchange, String apiKey) {
        log.info("Incoming request path={}, method={}, apiKeyHash={}",
                exchange.getRequest().getPath(),
                exchange.getRequest().getMethod(),
                apiKey != null ? apiKey.hashCode() : "N/A"
        );
    }

//    private void logUsage(ServerWebExchange exchange, ApiKeyService.ApiKeyMetadata metadata,
//                         Integer responseStatus, Long responseTimeMs, String errorMessage) {
//        usageTrackingService.logUsageAsync(exchange, metadata, responseStatus, responseTimeMs, errorMessage);
//    }

    @Override
    public int getOrder() {
        return -1; // Run early in the chain
    }

    private String generateTraceId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
