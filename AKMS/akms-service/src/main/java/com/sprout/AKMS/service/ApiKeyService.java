package com.sprout.AKMS.service;

import com.sprout.AKMS.core.dto.ApiKey;
import com.sprout.AKMS.core.dto.GenerateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyService {
    // Key generation and management
    Map<String, Object> generateApiKey(GenerateKeyRequest request);
    ValidateKeyResponse validateApiKey(ValidateKeyRequest request);
    ApiKey revokeApiKey(UUID keyId);
    ApiKey activateApiKey(UUID keyId);

    // CRUD operations
    Optional<ApiKey> getApiKeyById(UUID id);
    List<ApiKey> getAllApiKeys();
    Page<ApiKey> getAllApiKeys(Pageable pageable);
    List<ApiKey> getApiKeysByCustomerId(UUID customerId);
    Page<ApiKey> getApiKeysByCustomerId(UUID customerId, Pageable pageable);
    List<ApiKey> getApiKeysByStatus(String status);

    // Key management
    ApiKey updateApiKey(UUID id, ApiKey apiKey);
    void deleteApiKey(UUID id);

    // Utility methods
    boolean isApiKeyValid(String rawApiKey);
    boolean isApiKeyExpired(UUID keyId);
    List<ApiKey> getExpiredKeys();
    void cleanupExpiredKeys();
}