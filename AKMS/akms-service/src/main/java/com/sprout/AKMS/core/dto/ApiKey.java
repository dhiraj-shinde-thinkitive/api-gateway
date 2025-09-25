package com.sprout.AKMS.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {
    private UUID id;
    private String customerId;
    private String name;
    private List<String> permissions;
    private Integer rateLimit;
    private LocalDateTime expiryDate;
    // For response purposes - never expose the actual hash
    private String maskedKey; // e.g., "ak_****1234"
}
