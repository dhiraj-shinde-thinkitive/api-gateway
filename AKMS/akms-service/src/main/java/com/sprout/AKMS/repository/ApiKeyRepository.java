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

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {
    Optional<ApiKeyEntity> findByApiKeyHash(String apiKeyHash);

    List<ApiKeyEntity> findByCustomerId(UUID customerId);
    Page<ApiKeyEntity> findByCustomerId(UUID customerId, Pageable pageable);

    List<ApiKeyEntity> findByStatus(String status);
    Page<ApiKeyEntity> findByStatus(String status, Pageable pageable);

    List<ApiKeyEntity> findByCustomerIdAndStatus(UUID customerId, String status);

    @Query("SELECT a FROM ApiKeyEntity a WHERE a.expiryDate < :currentTime AND a.status != 'expired'")
    List<ApiKeyEntity> findExpiredKeys(@Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT a FROM ApiKeyEntity a WHERE a.customer.id = :customerId AND a.status = 'active'")
    List<ApiKeyEntity> findActiveKeysByCustomerId(@Param("customerId") UUID customerId);

    @Query("SELECT COUNT(a) FROM ApiKeyEntity a WHERE a.customer.id = :customerId AND a.status = 'active'")
    long countActiveKeysByCustomerId(@Param("customerId") UUID customerId);
}