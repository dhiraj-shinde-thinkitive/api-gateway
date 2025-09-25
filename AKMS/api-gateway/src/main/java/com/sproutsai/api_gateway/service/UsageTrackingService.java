//package com.sproutsai.api_gateway.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import org.springframework.web.server.ServerWebExchange;
//
//import java.time.LocalDateTime;
//import java.util.UUID;
//
///**
// * Service for tracking API usage and logging
// */
//@Service
//public class UsageTrackingService {
//
//    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);
//
//
//    private final RabbitTemplate rabbitTemplate;
//    // ObjectMapper available for future JSON processing needs
//    @SuppressWarnings("unused")
//    private final ObjectMapper objectMapper;
//
//    @Value("${app.messaging.usage-log-exchange}")
//    private String usageLogExchange;
//
//    @Value("${app.messaging.usage-log-queue}")
//    private String usageLogQueue;
//
//    public UsageTrackingService(RabbitTemplate rabbitTemplate,
//                               ObjectMapper objectMapper) {
//        this.rabbitTemplate = rabbitTemplate;
//        this.objectMapper = objectMapper;
//    }
//
//    /**
//     * Log API usage asynchronously via message queue
//     */
//    @Async
//    public void logUsageAsync(ServerWebExchange exchange, ApiKeyService.ApiKeyMetadata metadata,
//                             Integer responseStatus, Long responseTimeMs, String errorMessage) {
//
//        try {
//            UsageLogMessage message = createUsageLogMessage(exchange, metadata, responseStatus,
//                                                          responseTimeMs, errorMessage);
//
//            // Send to message queue for async processing
//            rabbitTemplate.convertAndSend(usageLogExchange, usageLogQueue, message);
//
//            log.debug("Usage log message sent to queue for customer: {}", metadata != null ? metadata.customerId() : "unknown");
//
//        } catch (Exception e) {
//            log.error("Failed to send usage log message to queue", e);
//
//            // Note: In production, consider implementing a fallback mechanism
//            // such as local file logging or alternative message queue
//        }
//    }
//
//    /**
//     * Process usage log message from queue
//     * Note: In the corrected architecture, this would forward to a separate logging service
//     */
//    public void processUsageLogMessage(UsageLogMessage message) {
//        try {
//            log.info("Processing usage log message for customer: {} - endpoint: {} - status: {}",
//                    message.customerId(), message.endpoint(), message.responseStatus());
//
//            // TODO: Forward to dedicated logging service via HTTP call or another message queue
//            // For now, just log the message details
//            log.debug("Usage log details: {}", message);
//
//        } catch (Exception e) {
//            log.error("Failed to process usage log message: {}", message, e);
//        }
//    }
//
//    // Private helper methods
//
//    private UsageLogMessage createUsageLogMessage(ServerWebExchange exchange,
//                                                ApiKeyService.ApiKeyMetadata metadata,
//                                                Integer responseStatus, Long responseTimeMs,
//                                                String errorMessage) {
//
//        String correlationId = UUID.randomUUID().toString();
//        String sessionId = exchange.getRequest().getHeaders().getFirst("X-Session-ID");
//
//        return new UsageLogMessage(
//            null, // No keyId available in simplified metadata
//            metadata != null ? metadata.customerId() : null,
//            null, // No tenantId in simplified metadata
//            null, // No customerName in simplified metadata
//            exchange.getRequest().getPath().value(),
//            exchange.getRequest().getMethod().name(),
//            exchange.getRequest().getPath().value(),
//            exchange.getRequest().getQueryParams().toString(),
//            exchange.getRequest().getHeaders().getFirst("User-Agent"),
//            getClientIp(exchange),
//            responseStatus,
//            responseTimeMs,
//            getRequestSize(exchange),
//            null, // Response size not available here
//            errorMessage,
//            sessionId,
//            correlationId,
//            LocalDateTime.now()
//        );
//    }
//
//    private String getClientIp(ServerWebExchange exchange) {
//        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
//        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
//            return xForwardedFor.split(",")[0].trim();
//        }
//
//        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
//        if (xRealIp != null && !xRealIp.isEmpty()) {
//            return xRealIp;
//        }
//
//        return exchange.getRequest().getRemoteAddress() != null ?
//            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
//    }
//
//    private Long getRequestSize(ServerWebExchange exchange) {
//        String contentLength = exchange.getRequest().getHeaders().getFirst("Content-Length");
//        if (contentLength != null) {
//            try {
//                return Long.parseLong(contentLength);
//            } catch (NumberFormatException e) {
//                log.debug("Invalid Content-Length header: {}", contentLength);
//            }
//        }
//        return null;
//    }
//
//    // DTOs and Records
//
//    public record UsageLogMessage(
//        Long apiKeyId,
//        String customerId,
//        String tenantId,
//        String customerName,
//        String endpoint,
//        String httpMethod,
//        String requestPath,
//        String queryParams,
//        String userAgent,
//        String clientIp,
//        Integer responseStatus,
//        Long responseTimeMs,
//        Long requestSizeBytes,
//        Long responseSizeBytes,
//        String errorMessage,
//        String sessionId,
//        String correlationId,
//        LocalDateTime timestamp
//    ) {}
//}
