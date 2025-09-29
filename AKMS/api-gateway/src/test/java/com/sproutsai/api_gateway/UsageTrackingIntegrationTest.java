package com.sproutsai.api_gateway;

import com.sproutsai.api_gateway.service.UsageTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for usage tracking functionality
 * Uses Testcontainers to spin up real RabbitMQ instance for testing
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class UsageTrackingIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3.13-management")
            .withUser("testuser", "testpass")
            .withVhost("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "testuser");
        registry.add("spring.rabbitmq.password", () -> "testpass");
        registry.add("spring.rabbitmq.virtual-host", () -> "test");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final List<UsageTrackingService.UsageLogMessage> receivedMessages = new ArrayList<>();
    private CountDownLatch messageLatch = new CountDownLatch(1);

    @RabbitListener(queues = "${app.messaging.usage-log-queue}")
    public void receiveUsageLogMessage(UsageTrackingService.UsageLogMessage message) {
        receivedMessages.add(message);
        messageLatch.countDown();
    }

    @Test
    public void testUsageTrackingForMissingApiKey() throws InterruptedException {
        // Reset latch for this test
        messageLatch = new CountDownLatch(1);
        receivedMessages.clear();

        // Make request without API key
        String url = "http://localhost:" + port + "/api/test";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Verify HTTP response
        assertThat(response.getStatusCodeValue()).isEqualTo(401);

        // Wait for message to be processed
        boolean messageReceived = messageLatch.await(5, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();

        // Verify usage tracking message
        assertThat(receivedMessages).hasSize(1);
        UsageTrackingService.UsageLogMessage usageMessage = receivedMessages.get(0);

        assertThat(usageMessage.responseStatus()).isEqualTo(401);
        assertThat(usageMessage.endpoint()).isEqualTo("/api/test");
        assertThat(usageMessage.httpMethod()).isEqualTo("GET");
        assertThat(usageMessage.errorMessage()).isEqualTo("Missing API key");
        assertThat(usageMessage.customerId()).isNull();
        assertThat(usageMessage.responseTimeMs()).isGreaterThan(0L);
    }

    @Test
    public void testUsageTrackingForInvalidApiKey() throws InterruptedException {
        // Reset latch for this test
        messageLatch = new CountDownLatch(1);
        receivedMessages.clear();

        // Make request with invalid API key
        String url = "http://localhost:" + port + "/api/test";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Api-Key invalid-key-123");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        // Verify HTTP response
        assertThat(response.getStatusCodeValue()).isEqualTo(401);

        // Wait for message to be processed
        boolean messageReceived = messageLatch.await(5, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();

        // Verify usage tracking message
        assertThat(receivedMessages).hasSize(1);
        UsageTrackingService.UsageLogMessage usageMessage = receivedMessages.get(0);

        assertThat(usageMessage.responseStatus()).isEqualTo(401);
        assertThat(usageMessage.endpoint()).isEqualTo("/api/test");
        assertThat(usageMessage.errorMessage()).contains("Invalid API key");
        assertThat(usageMessage.apiKeyUsed()).isEqualTo("invalid-...");
    }

    @Test
    public void testUsageTrackingForNonApiEndpoints() throws InterruptedException {
        // Reset latch for this test
        messageLatch = new CountDownLatch(1);
        receivedMessages.clear();

        // Make request to actuator endpoint (should skip auth but log usage)
        String url = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Verify HTTP response (should be successful)
        assertThat(response.getStatusCodeValue()).isEqualTo(200);

        // Wait for message to be processed
        boolean messageReceived = messageLatch.await(5, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();

        // Verify usage tracking message
        assertThat(receivedMessages).hasSize(1);
        UsageTrackingService.UsageLogMessage usageMessage = receivedMessages.get(0);

        assertThat(usageMessage.responseStatus()).isEqualTo(200);
        assertThat(usageMessage.endpoint()).isEqualTo("/actuator/health");
        assertThat(usageMessage.customerId()).isNull(); // No authentication required
        assertThat(usageMessage.errorMessage()).isNull();
    }

    @Test
    public void testUsageTrackingForTestEndpoint() throws InterruptedException {
        // Reset latch for this test
        messageLatch = new CountDownLatch(1);
        receivedMessages.clear();

        // Make request to test endpoint
        String url = "http://localhost:" + port + "/test/health";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        // Verify HTTP response
        assertThat(response.getStatusCodeValue()).isEqualTo(200);

        // Wait for message to be processed
        boolean messageReceived = messageLatch.await(5, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();

        // Verify usage tracking message
        assertThat(receivedMessages).hasSize(1);
        UsageTrackingService.UsageLogMessage usageMessage = receivedMessages.get(0);

        assertThat(usageMessage.responseStatus()).isEqualTo(200);
        assertThat(usageMessage.endpoint()).isEqualTo("/test/health");
        assertThat(usageMessage.httpMethod()).isEqualTo("GET");
        assertThat(usageMessage.responseTimeMs()).isGreaterThan(0L);
        assertThat(usageMessage.timestamp()).isNotNull();
        assertThat(usageMessage.correlationId()).isNotNull();
    }

    @Test
    public void testRabbitMQConnection() {
        // Verify RabbitMQ is running and accessible
        assertThat(rabbitMQContainer.isRunning()).isTrue();

        // Test direct message sending
        String testMessage = "Test message for usage tracking";
        rabbitTemplate.convertAndSend("usage.logs", testMessage);

        // If we get here without exception, RabbitMQ connection is working
        assertThat(true).isTrue();
    }
}