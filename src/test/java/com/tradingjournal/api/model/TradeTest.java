package com.tradingjournal.api.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TradeTest {

    @Test
    void isOpen_returnsTrue_whenExitTimeIsNull() {
        Trade trade = new Trade();
        trade.setEntryTime(LocalDateTime.of(2026, 8, 10, 9, 0));
        trade.setExitTime(null);

        assertThat(trade.isOpen()).isTrue();
    }

    @Test
    void isOpen_returnsFalse_whenExitTimeIsSet() {
        Trade trade = new Trade();
        trade.setEntryTime(LocalDateTime.of(2026, 8, 10, 9, 0));
        trade.setExitTime(LocalDateTime.of(2026, 8, 10, 9, 30));

        assertThat(trade.isOpen()).isFalse();
    }

    @Test
    void getDuration_returnsNull_whenTradeIsOpen() {
        Trade trade = new Trade();
        trade.setEntryTime(LocalDateTime.of(2026, 8, 10, 9, 0));
        trade.setExitTime(null);

        assertThat(trade.getDuration()).isNull();
    }

    @Test
    void getDuration_computesElapsedTime_whenTradeIsClosed() {
        Trade trade = new Trade();
        trade.setEntryTime(LocalDateTime.of(2026, 8, 10, 9, 0));
        trade.setExitTime(LocalDateTime.of(2026, 8, 10, 9, 30));

        assertThat(trade.getDuration()).isEqualTo(Duration.ofMinutes(30));
    }
}
