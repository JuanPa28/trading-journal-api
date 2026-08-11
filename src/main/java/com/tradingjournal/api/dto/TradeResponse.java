package com.tradingjournal.api.dto;

import com.tradingjournal.api.model.Direction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        Long traderId,
        StrategyResponse strategy,
        String externalId,
        String contract,
        Integer size,
        Direction direction,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        BigDecimal pnl,
        BigDecimal commissions,
        BigDecimal fees,
        String notes,
        boolean open
) {
}
