package com.tradingjournal.api.dto;

import java.time.LocalDateTime;

public record StrategyResponse(
        Long id,
        boolean htfPdArray,
        boolean ifvg,
        boolean cisd,
        boolean followedRules,
        boolean continuation,
        boolean reversal,
        boolean correctRisk,
        LocalDateTime createdAt
) {
}
