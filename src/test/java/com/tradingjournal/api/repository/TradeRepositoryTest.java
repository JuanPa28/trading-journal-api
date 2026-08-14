package com.tradingjournal.api.repository;

import com.tradingjournal.api.model.Direction;
import com.tradingjournal.api.model.Strategy;
import com.tradingjournal.api.model.Trade;
import com.tradingjournal.api.model.Trader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TradeRepositoryTest {

    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private TraderRepository traderRepository;
    @Autowired
    private StrategyRepository strategyRepository;

    private Trader traderA;
    private Trader traderB;

    @BeforeEach
    void setUp() {
        traderA = traderRepository.save(trader("juanpa28"));
        traderB = traderRepository.save(trader("otherTrader"));

        saveTrade(traderA, new BigDecimal("174.00"));  // winning trade for traderA
        saveTrade(traderA, new BigDecimal("-34.50"));  // losing trade for traderA
        saveTrade(traderB, new BigDecimal("50.00"));   // winning trade for traderB
    }

    @Test
    void findByTrader_Id_returnsOnlyThatTradersTrades() {
        List<Trade> trades = tradeRepository.findByTrader_Id(traderA.getId());

        assertThat(trades).hasSize(2);
        assertThat(trades).allMatch(t -> t.getTrader().getId().equals(traderA.getId()));
    }

    @Test
    void findByPnlGreaterThan_returnsOnlyWinningTrades() {
        List<Trade> winners = tradeRepository.findByPnlGreaterThan(BigDecimal.ZERO);

        assertThat(winners).hasSize(2);
        assertThat(winners).allMatch(t -> t.getPnl().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void findByPnlLessThanEqual_returnsOnlyLosingTrades() {
        List<Trade> losers = tradeRepository.findByPnlLessThanEqual(BigDecimal.ZERO);

        assertThat(losers).hasSize(1);
        assertThat(losers.get(0).getPnl()).isEqualByComparingTo("-34.50");
    }

    @Test
    void findByTrader_IdAndPnlGreaterThan_combinesBothFilters() {
        List<Trade> result = tradeRepository.findByTrader_IdAndPnlGreaterThan(traderA.getId(), BigDecimal.ZERO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPnl()).isEqualByComparingTo("174.00");
    }

    @Test
    void findByTrader_IdAndPnlLessThanEqual_combinesBothFilters() {
        List<Trade> result = tradeRepository.findByTrader_IdAndPnlLessThanEqual(traderA.getId(), BigDecimal.ZERO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPnl()).isEqualByComparingTo("-34.50");
    }

    private void saveTrade(Trader trader, BigDecimal pnl) {
        Strategy strategy = strategyRepository.save(new Strategy());

        Trade trade = new Trade();
        trade.setTrader(trader);
        trade.setStrategy(strategy);
        trade.setContract("MNQU26");
        trade.setSize(1);
        trade.setDirection(Direction.LONG);
        trade.setEntryTime(LocalDateTime.of(2026, 8, 10, 9, 0));
        trade.setExitTime(LocalDateTime.of(2026, 8, 10, 9, 30));
        trade.setEntryPrice(new BigDecimal("100.00"));
        trade.setExitPrice(new BigDecimal("110.00"));
        trade.setPnl(pnl);
        tradeRepository.save(trade);
    }

    private Trader trader(String username) {
        Trader trader = new Trader();
        trader.setUsername(username);
        trader.setFullName("Full " + username);
        trader.setEmail(username + "@example.com");
        trader.setAvailableFunds(new BigDecimal("1000.00"));
        return trader;
    }
}
