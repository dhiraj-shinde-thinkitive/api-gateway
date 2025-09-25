package com.sprout.AKMS.core.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ValidateKeyResponse {
    private boolean valid;
    private String customerId;
    private List<String> permissions;
    private Integer rateLimit;
    private LocalDateTime expiryDate;
    private String reason;
}
