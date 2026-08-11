package com.tradingjournal.api.dto;

import com.tradingjournal.api.model.Direction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeRequest(
        @NotNull Long traderId,
        @Valid @NotNull StrategyRequest strategy,
        String externalId,
        @NotBlank String contract,
        @NotNull Integer size,
        @NotNull Direction direction,
        @NotNull LocalDateTime entryTime,
        LocalDateTime exitTime,
        @NotNull BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal pnl,
        BigDecimal commissions,
        BigDecimal fees,
        String notes
) {
}
