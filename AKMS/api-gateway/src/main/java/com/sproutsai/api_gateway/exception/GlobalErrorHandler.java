package com.sproutsai.api_gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Global error handler for API Gateway
 * Provides user-friendly error messages for common gateway errors
 */
@Component
@Order(-1)
@Slf4j
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // Log the error for debugging
        log.error("API Gateway error: {}", ex.getMessage(), ex);

        // Determine appropriate status and message
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String userMessage = "An unexpected error occurred. Please try again later.";

        // Handle specific error types with user-friendly messages
        if (ex instanceof org.springframework.web.server.ResponseStatusException) {
            org.springframework.web.server.ResponseStatusException rsEx =
                (org.springframework.web.server.ResponseStatusException) ex;
            status = (HttpStatus) rsEx.getStatusCode();

            switch (status) {
                case NOT_FOUND:
                    userMessage = "The requested service endpoint was not found.";
                    break;
                case REQUEST_TIMEOUT:
                    userMessage = "Request timeout. The service took too long to respond.";
                    break;
                case BAD_GATEWAY:
                    userMessage = "Service temporarily unavailable. Please try again later.";
                    break;
                case SERVICE_UNAVAILABLE:
                    userMessage = "Service is currently unavailable. Please try again later.";
                    break;
                case GATEWAY_TIMEOUT:
                    userMessage = "Gateway timeout. The service did not respond in time.";
                    break;
                default:
                    userMessage = "Service error occurred. Please try again later.";
                    break;
            }
        } else if (ex instanceof java.net.ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            userMessage = "Unable to connect to the service. Please try again later.";
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            status = HttpStatus.REQUEST_TIMEOUT;
            userMessage = "Request timeout. Please try again.";
        }

        // Create user-friendly error response
        String errorResponse = String.format(
            "{" +
                "\"success\": false," +
                "\"message\": \"%s\"," +
                "\"status\": %d," +
                "\"timestamp\": \"%s\"" +
            "}",
            userMessage,
            status.value(),
            LocalDateTime.now()
        );

        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        DataBuffer buffer = response.bufferFactory()
            .wrap(errorResponse.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}