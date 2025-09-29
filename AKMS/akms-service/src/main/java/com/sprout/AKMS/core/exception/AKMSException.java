package com.sprout.AKMS.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception class for AKMS service
 * Handles both customer and API key related exceptions
 */
public class AKMSException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public AKMSException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public AKMSException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCodeValue() {
        return errorCode.getCode();
    }

    // Customer related exceptions
    public static class CustomerNotFoundException extends AKMSException {
        public CustomerNotFoundException(String customerId) {
            super(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found with ID: " + customerId);
        }
    }

    public static class CustomerAlreadyExistsException extends AKMSException {
        public CustomerAlreadyExistsException(String email) {
            super(ErrorCode.CUSTOMER_ALREADY_EXISTS, "Customer with email " + email + " already exists");
        }
    }

    // API Key related exceptions
    public static class ApiKeyNotFoundException extends AKMSException {
        public ApiKeyNotFoundException(String keyId) {
            super(ErrorCode.API_KEY_NOT_FOUND, "API key not found with ID: " + keyId);
        }
    }

    public static class ApiKeyExpiredException extends AKMSException {
        public ApiKeyExpiredException(String keyId) {
            super(ErrorCode.API_KEY_EXPIRED, "Cannot activate expired API key: " + keyId);
        }
    }

    public static class ApiKeyValidationException extends AKMSException {
        public ApiKeyValidationException(String message) {
            super(ErrorCode.API_KEY_VALIDATION_FAILED, message);
        }
    }

    public static class ApiKeyGenerationException extends AKMSException {
        public ApiKeyGenerationException(String message, Throwable cause) {
            super(ErrorCode.API_KEY_GENERATION_FAILED, message, cause);
        }
    }

    // Database related exceptions
    public static class DatabaseOperationException extends AKMSException {
        public DatabaseOperationException(String operation, Throwable cause) {
            super(ErrorCode.DATABASE_OPERATION_FAILED, "Database operation failed: " + operation, cause);
        }
    }

    // Validation exceptions
    public static class InvalidInputException extends AKMSException {
        public InvalidInputException(String message) {
            super(ErrorCode.INVALID_INPUT, message);
        }
    }

    // Error codes enum
    public enum ErrorCode {
        // Customer errors
        CUSTOMER_NOT_FOUND("AKMS_C001", HttpStatus.NOT_FOUND),
        CUSTOMER_ALREADY_EXISTS("AKMS_C002", HttpStatus.CONFLICT),

        // API Key errors
        API_KEY_NOT_FOUND("AKMS_K001", HttpStatus.NOT_FOUND),
        API_KEY_EXPIRED("AKMS_K002", HttpStatus.BAD_REQUEST),
        API_KEY_VALIDATION_FAILED("AKMS_K003", HttpStatus.UNAUTHORIZED),
        API_KEY_GENERATION_FAILED("AKMS_K004", HttpStatus.INTERNAL_SERVER_ERROR),

        // General errors
        DATABASE_OPERATION_FAILED("AKMS_DB001", HttpStatus.INTERNAL_SERVER_ERROR),
        INVALID_INPUT("AKMS_V001", HttpStatus.BAD_REQUEST),
        INTERNAL_SERVER_ERROR("AKMS_SYS001", HttpStatus.INTERNAL_SERVER_ERROR);

        private final String code;
        private final HttpStatus httpStatus;

        ErrorCode(String code, HttpStatus httpStatus) {
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String getCode() {
            return code;
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }
    }
}