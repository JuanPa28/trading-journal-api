package com.tradingjournal.api.dto;

import jakarta.validation.constraints.Email;

import java.math.BigDecimal;

public record TraderPatchRequest(
        String fullName,
        String username,
        @Email String email,
        BigDecimal availableFunds
) {
}
