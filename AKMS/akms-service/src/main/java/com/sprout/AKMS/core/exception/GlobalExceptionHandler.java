package com.sprout.AKMS.core.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AKMSException.CustomerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerNotFound(AKMSException.CustomerNotFoundException ex) {
        log.error("Customer not found: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Customer not found. Please check the customer ID and try again.");
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AKMSException.CustomerAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerAlreadyExists(AKMSException.CustomerAlreadyExistsException ex) {
        log.error("Customer already exists: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "A customer with this email already exists. Please use a different email address.");
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AKMSException.ApiKeyNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleApiKeyNotFound(AKMSException.ApiKeyNotFoundException ex) {
        log.error("API key not found: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "API key not found. Please check the key ID and try again.");
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AKMSException.ApiKeyExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleApiKeyExpired(AKMSException.ApiKeyExpiredException ex) {
        log.error("API key expired: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "This API key has expired and cannot be activated. Please generate a new key.");
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AKMSException.ApiKeyValidationException.class)
    public ResponseEntity<Map<String, Object>> handleApiKeyValidation(AKMSException.ApiKeyValidationException ex) {
        log.error("API key validation failed: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("status", false);
        response.put("message", "Invalid API key. Please check your key and try again.");
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        log.error("Validation error: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Please check your input data and try again.");
        response.put("errors", fieldErrors);
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception occurred: {}", ex.getMessage(), ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Something went wrong. Please try again later.");
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "An unexpected error occurred. Please contact support if this continues.");
        response.put("timestamp", LocalDateTime.now());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}