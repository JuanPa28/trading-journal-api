package com.tradingjournal.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TraderResponse(
        Long id,
        String fullName,
        String username,
        String email,
        BigDecimal availableFunds,
        LocalDateTime createdAt
) {
}
