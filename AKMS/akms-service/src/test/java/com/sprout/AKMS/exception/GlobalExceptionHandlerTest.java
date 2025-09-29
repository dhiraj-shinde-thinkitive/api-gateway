package com.sprout.AKMS.exception;

import com.sprout.AKMS.core.exception.AKMSException;
import com.sprout.AKMS.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testCustomerNotFoundExceptionHandling() {
        // Given
        AKMSException.CustomerNotFoundException exception =
            new AKMSException.CustomerNotFoundException("123");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleCustomerNotFound(exception);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("Customer not found. Please check the customer ID and try again.", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void testApiKeyNotFoundExceptionHandling() {
        // Given
        AKMSException.ApiKeyNotFoundException exception =
            new AKMSException.ApiKeyNotFoundException("key-123");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleApiKeyNotFound(exception);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("API key not found. Please check the key ID and try again.", body.get("message"));
    }

    @Test
    void testApiKeyExpiredExceptionHandling() {
        // Given
        AKMSException.ApiKeyExpiredException exception =
            new AKMSException.ApiKeyExpiredException("key-123");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleApiKeyExpired(exception);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("This API key has expired and cannot be activated. Please generate a new key.", body.get("message"));
    }

    @Test
    void testCustomerAlreadyExistsExceptionHandling() {
        // Given
        AKMSException.CustomerAlreadyExistsException exception =
            new AKMSException.CustomerAlreadyExistsException("test@example.com");

        // When
        ResponseEntity<Map<String, Object>> response = handler.handleCustomerAlreadyExists(exception);

        // Then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("success"));
        assertEquals("A customer with this email already exists. Please use a different email address.", body.get("message"));
    }
}