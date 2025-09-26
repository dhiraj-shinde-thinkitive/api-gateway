package com.sprout.AKMS.repository;

import com.sprout.AKMS.core.entity.ApiKeyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    Optional<ApiKeyEntity> findByApiKeyHash(String apiKeyHash);

    // Query by customer PK (numeric id)
    List<ApiKeyEntity> findByCustomerId(Long customerId);
    Page<ApiKeyEntity> findByCustomerId(Long customerId, Pageable pageable);

    // Query by customer UUID (business identifier)
    List<ApiKeyEntity> findByCustomerUuid(UUID customerUuid);
    Page<ApiKeyEntity> findByCustomerUuid(UUID customerUuid, Pageable pageable);

    List<ApiKeyEntity> findByStatus(String status);
    Page<ApiKeyEntity> findByStatus(String status, Pageable pageable);

    List<ApiKeyEntity> findByCustomerUuidAndStatus(UUID customerUuid, String status);

    @Query("SELECT a FROM ApiKeyEntity a WHERE a.expiryDate < :currentTime AND a.status != 'expired'")
    List<ApiKeyEntity> findExpiredKeys(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT a FROM ApiKeyEntity a WHERE a.customer.uuid = :customerUuid AND a.status = 'active'")
    List<ApiKeyEntity> findActiveKeysByCustomerUuid(@Param("customerUuid") UUID customerUuid);

    @Query("SELECT COUNT(a) FROM ApiKeyEntity a WHERE a.customer.uuid = :customerUuid AND a.status = 'active'")
    long countActiveKeysByCustomerUuid(@Param("customerUuid") UUID customerUuid);

    Optional<ApiKeyEntity> findByUuid(UUID uuid);

    boolean existsByUuid(UUID uuid);

    void deleteByUuid(UUID uuid);
}