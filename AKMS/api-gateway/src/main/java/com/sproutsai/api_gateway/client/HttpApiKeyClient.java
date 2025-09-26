package com.sproutsai.api_gateway.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * HTTP implementation of ApiKeyClient for communicating with API Key Management Service (AKMS)
 */
@Component
@Slf4j
public class HttpApiKeyClient implements ApiKeyClient {

    private final WebClient webClient;

    public HttpApiKeyClient(@Value("${app.akms.base-url}") String akmsBaseUrl, @Value("${app.akms.timeout:5000}") int timeoutMs) {
        this.webClient = WebClient.builder()
            .baseUrl(akmsBaseUrl)
            .build();
    }

    @Override
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

    @Override
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
}