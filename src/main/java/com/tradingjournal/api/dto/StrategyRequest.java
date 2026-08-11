package com.tradingjournal.api.dto;

public record StrategyRequest(
        boolean htfPdArray,
        boolean ifvg,
        boolean cisd,
        boolean followedRules,
        boolean continuation,
        boolean reversal,
        boolean correctRisk
) {
}
