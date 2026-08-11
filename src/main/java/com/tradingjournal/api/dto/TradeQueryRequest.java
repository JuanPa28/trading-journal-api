package com.tradingjournal.api.dto;

public record TradeQueryRequest(
        Long traderId,
        Boolean won
) {
}
