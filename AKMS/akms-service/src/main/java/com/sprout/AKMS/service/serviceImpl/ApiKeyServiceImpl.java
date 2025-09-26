package com.sprout.AKMS.service.serviceImpl;

import com.sprout.AKMS.core.dto.ApiKey;
import com.sprout.AKMS.core.dto.GenerateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyResponse;
import com.sprout.AKMS.core.entity.ApiKeyEntity;
import com.sprout.AKMS.core.entity.CustomerEntity;
import com.sprout.AKMS.repository.ApiKeyRepository;
import com.sprout.AKMS.repository.CustomerRepository;
import com.sprout.AKMS.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final CustomerRepository customerRepository;

    @Override
    public Map<String, Object> generateApiKey(GenerateKeyRequest request) {
        log.info("Generating API key for customer: {}", request.getCustomerId());

        CustomerEntity customer = customerRepository.findByUuid(UUID.fromString(request.getCustomerId())).orElseThrow(() -> new RuntimeException("Customer not found with ID: " + request.getCustomerId()));

        // Generate raw key and hash it
        String rawKey = "ak_" + UUID.randomUUID().toString().replace("-", "");
        String hashedKey = BCrypt.hashpw(rawKey, BCrypt.gensalt());

        ApiKeyEntity apiKeyEntity = ApiKeyEntity.builder()
                .customer(customer)
                .apiKeyHash(hashedKey)
                .name(request.getName())
                .permissions(String.join(",", request.getPermissions()))
                .rateLimit(request.getRateLimit())
                .expiryDate(request.getExpiryDate())
                .status("active")
                .build();

        ApiKeyEntity savedEntity = apiKeyRepository.save(apiKeyEntity);
        log.info("API key generated successfully with ID: {}", savedEntity.getId());

        // Return response with raw key (shown only once)
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedEntity.getId().toString());
        response.put("apiKey", rawKey); // Raw key shown only once
        response.put("customerId", customer.getId().toString());
        response.put("name", savedEntity.getName());
        response.put("status", savedEntity.getStatus());
        response.put("rateLimit", savedEntity.getRateLimit());
        response.put("expiryDate", savedEntity.getExpiryDate());
        response.put("createdAt", savedEntity.getCreatedAt());

        return response;
    }

    @Override
    public ValidateKeyResponse validateApiKey(ValidateKeyRequest request) {
        log.info("Validating API key");

        return apiKeyRepository.findAll().stream()
                .filter(key -> "active".equals(key.getStatus()))
                .filter(key -> key.getExpiryDate() == null || key.getExpiryDate().isAfter(LocalDateTime.now()))
                .filter(key -> BCrypt.checkpw(request.getApiKey(), key.getApiKeyHash()))
                .findFirst()
                .map(key -> {
                    log.info("API key validation successful for customer: {}", key.getCustomer().getId());
                    return ValidateKeyResponse.builder()
                            .valid(true)
                            .customerId(key.getCustomer().getId().toString())
                            .permissions(Arrays.asList(key.getPermissions().split(",")))
                            .rateLimit(key.getRateLimit())
                            .expiryDate(key.getExpiryDate())
                            .build();
                })
                .orElse(ValidateKeyResponse.builder()
                        .valid(false)
                        .reason("Invalid, expired, or revoked API key")
                        .build());
    }

    @Override
    public ApiKey revokeApiKey(UUID keyId) {
        log.info("Revoking API key with ID: {}", keyId);

        ApiKeyEntity apiKeyEntity = apiKeyRepository.findByUuid(keyId).orElseThrow(() -> new RuntimeException("API key not found with ID: " + keyId));

        apiKeyEntity.setStatus("revoked");
        apiKeyEntity.setUpdatedAt(LocalDateTime.now());

        ApiKeyEntity savedEntity = apiKeyRepository.save(apiKeyEntity);
        log.info("API key revoked successfully with ID: {}", keyId);

        return mapToDto(savedEntity);
    }

    @Override
    public ApiKey activateApiKey(UUID keyId) {
        log.info("Activating API key with ID: {}", keyId);

        ApiKeyEntity apiKeyEntity = apiKeyRepository.findByUuid(keyId).orElseThrow(() -> new RuntimeException("API key not found with ID: " + keyId));

        // Check if key is not expired
        if (apiKeyEntity.getExpiryDate() != null && apiKeyEntity.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot activate expired API key");
        }

        apiKeyEntity.setStatus("active");
        apiKeyEntity.setUpdatedAt(LocalDateTime.now());

        ApiKeyEntity savedEntity = apiKeyRepository.save(apiKeyEntity);
        log.info("API key activated successfully with ID: {}", keyId);

        return mapToDto(savedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiKey> getApiKeyById(UUID id) {
        log.info("Fetching API key by ID: {}", id);
        return apiKeyRepository.findByUuid(id)
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> getAllApiKeys() {
        log.info("Fetching all API keys");
        return apiKeyRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApiKey> getAllApiKeys(Pageable pageable) {
        log.info("Fetching API keys with pagination: page={}, size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return apiKeyRepository.findAll(pageable)
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> getApiKeysByCustomerId(UUID customerId) {
        log.info("Fetching API keys for customer: {}", customerId);

        CustomerEntity customer = customerRepository.findByUuid(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found with UUID: " + customerId));

        return apiKeyRepository.findAll().stream()
                .filter(key -> key.getCustomer().getId().equals(customerId))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApiKey> getApiKeysByCustomerId(UUID customerId, Pageable pageable) {
        log.info("Fetching API keys for customer: {} with pagination", customerId);

        if (!customerRepository.existsByUuid(customerId)) {
            throw new RuntimeException("Customer not found with ID: " + customerId);
        }

        return apiKeyRepository.findAll(pageable)
                .map(this::mapToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> getApiKeysByStatus(String status) {
        log.info("Fetching API keys with status: {}", status);
        return apiKeyRepository.findAll().stream()
                .filter(key -> status.equals(key.getStatus()))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public ApiKey updateApiKey(UUID id, ApiKey apiKey) {
        log.info("Updating API key with ID: {}", id);

        ApiKeyEntity existingEntity = apiKeyRepository.findByUuid(id).orElseThrow(() -> new RuntimeException("API key not found with ID: " + id));

        // Update allowed fields (not the hash or customer)
        existingEntity.setName(apiKey.getName());
        existingEntity.setPermissions(String.join(",", apiKey.getPermissions()));
        existingEntity.setRateLimit(apiKey.getRateLimit());
        existingEntity.setExpiryDate(apiKey.getExpiryDate());
        existingEntity.setUpdatedAt(LocalDateTime.now());

        ApiKeyEntity updatedEntity = apiKeyRepository.save(existingEntity);
        log.info("API key updated successfully with ID: {}", id);

        return mapToDto(updatedEntity);
    }

    @Override
    public void deleteApiKey(UUID id) {
        log.info("Deleting API key with ID: {}", id);

        if (!apiKeyRepository.existsByUuid(id)) {
            throw new RuntimeException("API key not found with ID: " + id);
        }

        apiKeyRepository.deleteByUuid(id);
        log.info("API key deleted successfully with ID: {}", id);
    }

    @Override
    public boolean isApiKeyValid(String rawApiKey) {
        return apiKeyRepository.findAll().stream()
                .anyMatch(key -> "active".equals(key.getStatus()) &&
                        (key.getExpiryDate() == null || key.getExpiryDate().isAfter(LocalDateTime.now())) &&
                        BCrypt.checkpw(rawApiKey, key.getApiKeyHash()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isApiKeyExpired(UUID keyId) {
        return apiKeyRepository.findByUuid(keyId)
                .map(key -> key.getExpiryDate() != null && key.getExpiryDate().isBefore(LocalDateTime.now()))
                .orElse(true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKey> getExpiredKeys() {
        log.info("Fetching expired API keys");
        LocalDateTime now = LocalDateTime.now();
        return apiKeyRepository.findAll().stream()
                .filter(key -> key.getExpiryDate() != null && key.getExpiryDate().isBefore(now))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public void cleanupExpiredKeys() {
        log.info("Cleaning up expired API keys");
        LocalDateTime now = LocalDateTime.now();
        List<ApiKeyEntity> expiredKeys = apiKeyRepository.findAll().stream()
                .filter(key -> key.getExpiryDate() != null && key.getExpiryDate().isBefore(now))
                .filter(key -> !"expired".equals(key.getStatus()))
                .collect(Collectors.toList());

        expiredKeys.forEach(key -> {
            key.setStatus("expired");
            key.setUpdatedAt(now);
        });

        apiKeyRepository.saveAll(expiredKeys);
        log.info("Marked {} keys as expired", expiredKeys.size());
    }

    private ApiKey mapToDto(ApiKeyEntity entity) {
        return ApiKey.builder()
                .uuid(entity.getUuid())
                .customerId(entity.getCustomer().getId().toString())
                .name(entity.getName())
                .permissions(Arrays.asList(entity.getPermissions().split(",")))
                .rateLimit(entity.getRateLimit())
                .expiryDate(entity.getExpiryDate())
                .maskedKey(maskApiKey(entity.getId().toString())) // Create masked version
                .build();
    }

    private String maskApiKey(String keyId) {
        // Create a masked representation for display purposes
        return "ak_****" + keyId.substring(keyId.length() - 4);
    }
}