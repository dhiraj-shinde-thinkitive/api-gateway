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

        // Step 1: Log incoming request
        logRequest(exchange, extractApiKey(exchange));

        // Skip API key authentication for actuator endpoints
        if (path.startsWith("/actuator/")) {
            log.debug("Skipping API key authentication for actuator endpoint: {}", path);
            return chain.filter(exchange);
        }

        // Skip API key authentication for non-API paths (if any)
        if (!path.startsWith("/api/")) {
            log.debug("Skipping API key authentication for non-API path: {}", path);
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String apiKey = extractApiKey(exchange);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Missing API key for request: {}", exchange.getRequest().getPath());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Step 2: Validate API key reactively
        return apiKeyService.validateApiKey(apiKey)
                .flatMap(metadata -> {
                    if (metadata == null) {
                        log.warn("Invalid API key: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
//                        logUsage(exchange, null, HttpStatus.UNAUTHORIZED.value(),
//                                System.currentTimeMillis() - startTime, "Invalid API key");
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

//                    ApiKeyService.ApiKeyMetadata metadata = metadataOpt.get();

                    // Step 3: Rate limiting - use same rate limit for all periods
                    RateLimitService.RateLimitResult rateLimitResult = rateLimitService.checkRateLimit( metadata.customerId(), null, metadata.rateLimit());

                    if (!rateLimitResult.allowed()) {
                        log.warn("Rate limit exceeded for customer: {}, limited by: {}",  metadata.customerId(), rateLimitResult.limitedBy());

                        // Add rate limit headers
                        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(rateLimitResult.remaining()));
                        exchange.getResponse().getHeaders().add("X-RateLimit-Reset", rateLimitResult.resetTime().toString());

//                        logUsage(exchange, metadata, HttpStatus.TOO_MANY_REQUESTS.value(),
//                                System.currentTimeMillis() - startTime, "Rate limit exceeded");
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }

                    // Step 4: Enrich request with metadata
                    ServerWebExchange enrichedExchange = exchange.mutate()
                            .request(r -> r.headers(h -> {
                                h.add("X-Api-Client-Id", metadata.customerId());
                            }))
                            .build();

                    // Step 5: Forward request and log usage
                    return chain.filter(enrichedExchange)
                            .doFinally(signalType -> {
                                long responseTime = System.currentTimeMillis() - startTime;
                                int statusCode = enrichedExchange.getResponse().getStatusCode() != null ?
                                    enrichedExchange.getResponse().getStatusCode().value() : 200;
//                                logUsage(enrichedExchange, metadata, statusCode, responseTime, null);
                            });
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
}
