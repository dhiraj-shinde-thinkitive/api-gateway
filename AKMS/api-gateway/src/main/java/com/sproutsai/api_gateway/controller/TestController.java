package com.sproutsai.api_gateway.controller;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test controller to verify API Gateway functionality and usage tracking
 * This controller helps in testing without needing downstream services
 */
@RestController
@RequestMapping("/test")
public class TestController {

    private final AtomicLong requestCounter = new AtomicLong(0);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${app.messaging.usage-log-queue}")
    private String usageLogQueue;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        response.put("requestCount", requestCounter.incrementAndGet());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/queue-status")
    public ResponseEntity<Map<String, Object>> queueStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            // Try to get queue info - this will fail if RabbitMQ is not connected
            response.put("rabbitmq", "connected");
            response.put("queueName", usageLogQueue);
            response.put("status", "healthy");
        } catch (Exception e) {
            response.put("rabbitmq", "disconnected");
            response.put("error", e.getMessage());
            response.put("status", "unhealthy");
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/send-test-message")
    public ResponseEntity<Map<String, Object>> sendTestMessage(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> testMessage = new HashMap<>();
            testMessage.put("test", true);
            testMessage.put("timestamp", System.currentTimeMillis());
            testMessage.put("payload", payload);

            rabbitTemplate.convertAndSend(usageLogQueue, testMessage);

            response.put("status", "success");
            response.put("message", "Test message sent to queue");
            response.put("queueName", usageLogQueue);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/simulate-delay/{milliseconds}")
    public ResponseEntity<Map<String, Object>> simulateDelay(@PathVariable long milliseconds) throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread.sleep(Math.min(milliseconds, 5000)); // Max 5 seconds
        long end = System.currentTimeMillis();

        Map<String, Object> response = new HashMap<>();
        response.put("requestedDelay", milliseconds);
        response.put("actualDelay", end - start);
        response.put("timestamp", end);
        response.put("requestCount", requestCounter.incrementAndGet());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/simulate-error/{statusCode}")
    public ResponseEntity<Map<String, Object>> simulateError(@PathVariable int statusCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("simulatedStatus", statusCode);
        response.put("message", "This is a simulated error response");
        response.put("timestamp", System.currentTimeMillis());
        response.put("requestCount", requestCounter.incrementAndGet());

        return ResponseEntity.status(statusCode).body(response);
    }

    @GetMapping("/headers")
    public ResponseEntity<Map<String, Object>> showHeaders(@RequestHeader Map<String, String> headers) {
        Map<String, Object> response = new HashMap<>();
        response.put("headers", headers);
        response.put("timestamp", System.currentTimeMillis());
        response.put("requestCount", requestCounter.incrementAndGet());

        // Show specific headers added by the gateway
        Map<String, String> gatewayHeaders = new HashMap<>();
        headers.forEach((key, value) -> {
            if (key.toLowerCase().startsWith("x-")) {
                gatewayHeaders.put(key, value);
            }
        });
        response.put("gatewayHeaders", gatewayHeaders);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/echo")
    public ResponseEntity<Map<String, Object>> echo(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("receivedBody", body);
        response.put("timestamp", System.currentTimeMillis());
        response.put("requestCount", requestCounter.incrementAndGet());
        return ResponseEntity.ok(response);
    }
}