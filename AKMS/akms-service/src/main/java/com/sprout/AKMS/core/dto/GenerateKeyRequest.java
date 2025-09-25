package com.sprout.AKMS.core.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class GenerateKeyRequest {
    private String customerId;
    private String name;
    private List<String> permissions;
    private Integer rateLimit;
    private LocalDateTime expiryDate;
}
