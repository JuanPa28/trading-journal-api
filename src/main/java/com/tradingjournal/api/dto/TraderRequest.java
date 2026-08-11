package com.tradingjournal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record TraderRequest(
        @NotBlank String fullName,
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotNull @PositiveOrZero BigDecimal availableFunds
) {
}
