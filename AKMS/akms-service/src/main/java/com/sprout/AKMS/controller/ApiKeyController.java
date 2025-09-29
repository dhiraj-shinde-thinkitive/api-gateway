package com.sprout.AKMS.controller;

import com.sprout.AKMS.core.dto.ApiKey;
import com.sprout.AKMS.core.dto.GenerateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyResponse;
import com.sprout.AKMS.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "API Key Management", description = "APIs for managing API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping("/test")
    @Operation(summary = "Test endpoint", description = "Simple test endpoint to verify API gateway authentication")
    public ResponseEntity<Map<String, String>> testEndpoint() {
        log.info("Test endpoint called successfully");
        return ResponseEntity.ok(Map.of(
                "message", "Hello from AKMS!",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "service", "API Key Management Service"
        ));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate new API key", description = "Generates a new API key for a customer")
    @ApiResponses(value = {@ApiResponse(responseCode = "201", description = "API key generated successfully"), @ApiResponse(responseCode = "400", description = "Invalid input data"), @ApiResponse(responseCode = "404", description = "Customer not found")})
    public ResponseEntity<Map<String, Object>> generateApiKey(@Valid @RequestBody GenerateKeyRequest request) {
        log.info("Request to generate API key for customer: {}", request.getCustomerId());
        Map<String, Object> response = apiKeyService.generateApiKey(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate API key", description = "Validates an API key and returns associated information")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Validation completed"), @ApiResponse(responseCode = "400", description = "Invalid request")})
    public ResponseEntity<ValidateKeyResponse> validateApiKey(@Valid @RequestBody ValidateKeyRequest request) {
        log.info("Request to validate API key");
        ValidateKeyResponse response = apiKeyService.validateApiKey(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "Revoke API key", description = "Revokes an API key by setting its status to revoked")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "API key revoked successfully"), @ApiResponse(responseCode = "404", description = "API key not found")})
    public ResponseEntity<ApiKey> revokeApiKey(@Parameter(description = "API key ID") @PathVariable UUID id) {
        log.info("Request to revoke API key: {}", id);
        ApiKey revokedKey = apiKeyService.revokeApiKey(id);
        return ResponseEntity.ok(revokedKey);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate API key", description = "Activates a revoked API key")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "API key activated successfully"), @ApiResponse(responseCode = "404", description = "API key not found"), @ApiResponse(responseCode = "400", description = "Cannot activate expired key")})
    public ResponseEntity<ApiKey> activateApiKey(@Parameter(description = "API key ID") @PathVariable UUID id) {
        log.info("Request to activate API key: {}", id);
        ApiKey activatedKey = apiKeyService.activateApiKey(id);
        return ResponseEntity.ok(activatedKey);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get API key by ID", description = "Retrieves an API key by its ID")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "API key found"), @ApiResponse(responseCode = "404", description = "API key not found")})
    public ResponseEntity<ApiKey> getApiKeyById(@Parameter(description = "API key ID") @PathVariable UUID id) {
        log.info("Request to get API key by ID: {}", id);
        return apiKeyService.getApiKeyById(id).map(apiKey -> ResponseEntity.ok(apiKey)).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all API keys", description = "Retrieves all API keys with optional pagination")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "API keys retrieved successfully")})
    public ResponseEntity<?> getAllApiKeys(@Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page, @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size, @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy, @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir, @Parameter(description = "Enable pagination") @RequestParam(defaultValue = "true") boolean paginated) {

        log.info("Request to get all API keys - page: {}, size: {}, sortBy: {}, sortDir: {}, paginated: {}", page, size, sortBy, sortDir, paginated);

        if (!paginated) {
            List<ApiKey> apiKeys = apiKeyService.getAllApiKeys();
            return ResponseEntity.ok(apiKeys);
        }

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ApiKey> apiKeys = apiKeyService.getAllApiKeys(pageable);

        return ResponseEntity.ok(apiKeys);
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get API keys by customer", description = "Retrieves all API keys for a specific customer")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "API keys retrieved successfully"), @ApiResponse(responseCode = "404", description = "Customer not found")})
    public ResponseEntity<?> getApiKeysByCustomer(@Parameter(description = "Customer ID") @PathVariable UUID customerId, @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page, @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size, @Parameter(description = "Sort by field") @RequestParam(defaultValue = "createdAt") String sortBy, @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir, @Parameter(description = "Enable pagination") @RequestParam(defaultValue = "true") boolean paginated) {

        log.info("Request to get API keys for customer: {}", customerId);

        try {
            if (!paginated) {
                List<ApiKey> apiKeys = apiKeyService.getApiKeysByCustomerId(customerId);
                return ResponseEntity.ok(apiKeys);
            }

            Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<ApiKey> apiKeys = apiKeyService.getApiKeysByCustomerId(customerId, pageable);

            return ResponseEntity.ok(apiKeys);
        } catch (RuntimeException e) {
            log.error("Error fetching API keys for customer: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get API keys by status", description = "Retrieves all API keys with a specific status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "API keys retrieved successfully")})
    public ResponseEntity<List<ApiKey>> getApiKeysByStatus(@Parameter(description = "API key status") @PathVariable String status) {
        log.info("Request to get API keys with status: {}", status);
        List<ApiKey> apiKeys = apiKeyService.getApiKeysByStatus(status);
        return ResponseEntity.ok(apiKeys);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update API key", description = "Updates an existing API key")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "API key updated successfully"), @ApiResponse(responseCode = "400", description = "Invalid input data"), @ApiResponse(responseCode = "404", description = "API key not found")})
    public ResponseEntity<ApiKey> updateApiKey(@Parameter(description = "API key ID") @PathVariable UUID id, @Valid @RequestBody ApiKey apiKey) {
        log.info("Request to update API key: {}", id);
        try {
            ApiKey updatedApiKey = apiKeyService.updateApiKey(id, apiKey);
            return ResponseEntity.ok(updatedApiKey);
        } catch (RuntimeException e) {
            log.error("Error updating API key: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete API key", description = "Permanently deletes an API key")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "API key deleted successfully"), @ApiResponse(responseCode = "404", description = "API key not found")})
    public ResponseEntity<Void> deleteApiKey(@Parameter(description = "API key ID") @PathVariable UUID id) {
        log.info("Request to delete API key: {}", id);
        try {
            apiKeyService.deleteApiKey(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Error deleting API key: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/expired")
    @Operation(summary = "Get expired API keys", description = "Retrieves all expired API keys")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Expired API keys retrieved successfully")})
    public ResponseEntity<List<ApiKey>> getExpiredKeys() {
        log.info("Request to get expired API keys");
        List<ApiKey> expiredKeys = apiKeyService.getExpiredKeys();
        return ResponseEntity.ok(expiredKeys);
    }

    @PostMapping("/cleanup-expired")
    @Operation(summary = "Cleanup expired keys", description = "Marks all expired API keys as expired status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Expired keys cleanup completed")})
    public ResponseEntity<Map<String, String>> cleanupExpiredKeys() {
        log.info("Request to cleanup expired API keys");
        apiKeyService.cleanupExpiredKeys();
        return ResponseEntity.ok(Map.of("message", "Expired keys cleanup completed successfully"));
    }

    @GetMapping("/{rawKey}/validate-raw")
    @Operation(summary = "Validate raw API key", description = "Validates a raw API key directly")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Validation completed")})
    public ResponseEntity<Map<String, Boolean>> validateRawApiKey(@Parameter(description = "Raw API key") @PathVariable String rawKey) {
        log.info("Request to validate raw API key");
        boolean isValid = apiKeyService.isApiKeyValid(rawKey);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }
}