package com.sprout.AKMS.service.serviceImpl;

import com.sprout.AKMS.core.dto.ApiKey;
import com.sprout.AKMS.core.dto.GenerateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyRequest;
import com.sprout.AKMS.core.dto.ValidateKeyResponse;
import com.sprout.AKMS.core.entity.ApiKeyEntity;
import com.sprout.AKMS.core.entity.CustomerEntity;
import com.sprout.AKMS.core.exception.AKMSException;
import com.sprout.AKMS.repository.ApiKeyRepository;
import com.sprout.AKMS.repository.CustomerRepository;
import com.sprout.AKMS.service.ApiKeyService;
import com.sprout.AKMS.service.CacheInvalidationService;
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
    private final CacheInvalidationService cacheInvalidationService;

    @Override
    public Map<String, Object> generateApiKey(GenerateKeyRequest request) {
        log.info("KEY_GENERATION_START - Generating API key: customer={}, name={}, rateLimit={}", request.getCustomerId(), request.getName(), request.getRateLimit());

        long startTime = System.currentTimeMillis();

        // Validate customer exists
        try {
            CustomerEntity customer = customerRepository.findByUuid(UUID.fromString(request.getCustomerId()))
                    .orElseThrow(() -> new AKMSException.CustomerNotFoundException(request.getCustomerId()));

            // Generate raw key and hash it
            String rawKey = "ak_" + UUID.randomUUID().toString().replace("-", "");
            log.debug("RAW_KEY_GENERATED - Raw API key generated: length={}", rawKey.length());

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

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("KEY_GENERATION_SUCCESS - API key generated successfully: keyId={}, customer={}, totalTime={}ms",
                    savedEntity.getId(), customer.getId(), totalTime);

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

        } catch (Exception e) {
            log.error("KEY_GENERATION_ERROR - Failed to generate API key: customer={}, error={}",
                    request.getCustomerId(), e.getMessage(), e);
            if (e instanceof AKMSException) {
                throw e;
            }
            throw new AKMSException.ApiKeyGenerationException("Failed to generate API key", e);
        }
    }

    @Override
    public ValidateKeyResponse validateApiKey(ValidateKeyRequest request) {
        try {
            String maskedKey = request.getApiKey().substring(0, Math.min(8, request.getApiKey().length())) + "...";
            log.info("VALIDATION_START - Starting API key validation: {}", maskedKey);

            return apiKeyRepository.findAll().stream()
                    .filter(key -> "active".equals(key.getStatus()))
                    .filter(key -> key.getExpiryDate() == null || key.getExpiryDate().isAfter(LocalDateTime.now()))
                    .filter(key -> BCrypt.checkpw(request.getApiKey(), key.getApiKeyHash()))
                    .findFirst()
                    .map(key -> {
                        log.info("VALIDATION_SUCCESS - API key validation successful for customer: {}", key.getCustomer().getUuid());
                        return ValidateKeyResponse.builder()
                                .valid(true)
                                .customerId(key.getCustomer().getId().toString())
                                .apiKeyId(key.getId().toString())
                                .permissions(key.getPermissions() != null && !key.getPermissions().trim().isEmpty()
                                    ? Arrays.asList(key.getPermissions().split(","))
                                    : Arrays.asList())
                                .rateLimit(key.getRateLimit())
                                .expiryDate(key.getExpiryDate())
                                .build();
                    })
                    .orElseGet(() -> {
                        log.warn("VALIDATION_FAILED - API key validation failed");
                        return ValidateKeyResponse.builder()
                                .valid(false)
                                .reason("Invalid, expired, or revoked API key")
                                .build();
                    });

        } catch (Exception e) {
            log.error("VALIDATION_ERROR - Error validating API key: {}", e.getMessage(), e);
            return ValidateKeyResponse.builder()
                    .valid(false)
                    .reason("Unable to validate API key. Please try again.")
                    .build();
        }
    }

    @Override
    public ApiKey revokeApiKey(UUID keyId) {
        log.info("KEY_REVOKE_START - Revoking API key with ID: {}", keyId);

        try {
            ApiKeyEntity apiKeyEntity = apiKeyRepository.findByUuid(keyId).orElseThrow(() -> new AKMSException.ApiKeyNotFoundException(keyId.toString()));

            apiKeyEntity.setStatus("revoked");
            apiKeyEntity.setUpdatedAt(LocalDateTime.now());

            ApiKeyEntity savedEntity = apiKeyRepository.save(apiKeyEntity);
            log.info("KEY_REVOKE_SUCCESS - API key revoked successfully with ID: {}", keyId);

            // Invalidate cache to ensure revoked key is not served from cache
            cacheInvalidationService.invalidateApiKeyCache(savedEntity.getId());

            return mapToDto(savedEntity);

        } catch (Exception e) {
            log.error("KEY_REVOKE_ERROR - Failed to revoke API key: keyId={}, error={}", keyId, e.getMessage(), e);
            if (e instanceof AKMSException) {
                throw e;
            }
            throw new AKMSException.DatabaseOperationException("revoke API key", e);
        }
    }

    @Override
    public ApiKey activateApiKey(UUID keyId) {
        log.info("KEY_ACTIVATE_START - Activating API key with ID: {}", keyId);

        try {
            ApiKeyEntity apiKeyEntity = apiKeyRepository.findByUuid(keyId)
                    .orElseThrow(() -> new AKMSException.ApiKeyNotFoundException(keyId.toString()));

            // Check if key is not expired
            if (apiKeyEntity.getExpiryDate() != null && apiKeyEntity.getExpiryDate().isBefore(LocalDateTime.now())) {
                throw new AKMSException.ApiKeyExpiredException(keyId.toString());
            }

            apiKeyEntity.setStatus("active");
            apiKeyEntity.setUpdatedAt(LocalDateTime.now());

            ApiKeyEntity savedEntity = apiKeyRepository.save(apiKeyEntity);
            log.info("KEY_ACTIVATE_SUCCESS - API key activated successfully with ID: {}", keyId);

            // Invalidate cache to ensure activated key gets fresh validation
            cacheInvalidationService.invalidateApiKeyCache(savedEntity.getId());

            return mapToDto(savedEntity);

        } catch (Exception e) {
            log.error("KEY_ACTIVATE_ERROR - Failed to activate API key: keyId={}, error={}", keyId, e.getMessage(), e);
            if (e instanceof AKMSException) {
                throw e;
            }
            throw new AKMSException.DatabaseOperationException("activate API key", e);
        }
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
                .orElseThrow(() -> new AKMSException.CustomerNotFoundException(customerId.toString()));

        return apiKeyRepository.findAll().stream()
                .filter(key -> key.getCustomer().getUuid().equals(customerId))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApiKey> getApiKeysByCustomerId(UUID customerId, Pageable pageable) {
        log.info("Fetching API keys for customer: {} with pagination", customerId);

        if (!customerRepository.existsByUuid(customerId)) {
            throw new AKMSException.CustomerNotFoundException(customerId.toString());
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

        ApiKeyEntity existingEntity = apiKeyRepository.findByUuid(id)
                .orElseThrow(() -> new AKMSException.ApiKeyNotFoundException(id.toString()));

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

        try {
            // First get the entity to get the database ID for cache invalidation
            ApiKeyEntity apiKeyEntity = apiKeyRepository.findByUuid(id).orElseThrow(() -> new AKMSException.ApiKeyNotFoundException(id.toString()));

            Long databaseId = apiKeyEntity.getId();

            apiKeyRepository.deleteByUuid(id);
            log.info("API key deleted successfully with ID: {}", id);

            // Invalidate cache to ensure deleted key is not served from cache
            cacheInvalidationService.invalidateApiKeyCache(databaseId);

        } catch (AKMSException e) {
            throw e;
        } catch (Exception e) {
            log.error("DELETE_ERROR - Failed to delete API key: keyId={}, error={}", id, e.getMessage(), e);
            throw new AKMSException.DatabaseOperationException("delete API key", e);
        }
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
                .customerId(entity.getCustomer().getUuid().toString())
                .name(entity.getName())
                .permissions(entity.getPermissions() != null && !entity.getPermissions().trim().isEmpty()
                        ? Arrays.asList(entity.getPermissions().split(","))
                        : Arrays.asList())
                .rateLimit(entity.getRateLimit())
                .expiryDate(entity.getExpiryDate())
                .maskedKey(maskApiKey(entity.getId().toString())) // Create masked version
                .build();
    }

    private String maskApiKey(String keyId) {
        // Create a masked representation for display purposes
        if (keyId == null || keyId.length() <= 4) {
            return "ak_****" + keyId;
        }
        return "ak_****" + keyId.substring(keyId.length() - 4);
    }
}