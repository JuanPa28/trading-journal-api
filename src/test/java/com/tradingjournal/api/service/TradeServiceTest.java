package com.tradingjournal.api.service;

import com.tradingjournal.api.dto.StrategyRequest;
import com.tradingjournal.api.dto.TradePatchRequest;
import com.tradingjournal.api.dto.TradeQueryRequest;
import com.tradingjournal.api.dto.TradeRequest;
import com.tradingjournal.api.dto.TradeResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.model.Direction;
import com.tradingjournal.api.model.Strategy;
import com.tradingjournal.api.model.Trade;
import com.tradingjournal.api.model.Trader;
import com.tradingjournal.api.repository.StrategyRepository;
import com.tradingjournal.api.repository.TradeRepository;
import com.tradingjournal.api.repository.TraderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private TraderRepository traderRepository;
    @Mock
    private StrategyRepository strategyRepository;

    private TradeService tradeService;

    private Trader trader;
    private StrategyRequest strategyRequest;
    private TradeRequest tradeRequest;

    @BeforeEach
    void setUp() {
        tradeService = new TradeService(tradeRepository, traderRepository, strategyRepository);

        trader = new Trader();
        trader.setId(1L);
        trader.setUsername("juanpa28");

        strategyRequest = new StrategyRequest(true, false, false, true, false, false, true);

        tradeRequest = new TradeRequest(
                1L, strategyRequest, "2967867693", "MNQU26", 2, Direction.SHORT,
                LocalDateTime.of(2026, 8, 10, 8, 40),
                LocalDateTime.of(2026, 8, 10, 8, 41),
                new BigDecimal("29770.25"), new BigDecimal("29726.75"),
                new BigDecimal("174.00"), new BigDecimal("1.00"), new BigDecimal("1.44"),
                "Buen setup"
        );
    }

    @Test
    void create_createsBrandNewStrategyForEveryTrade() {
        when(traderRepository.findById(1L)).thenReturn(Optional.of(trader));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(invocation -> {
            Strategy saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        TradeResponse response = tradeService.create(tradeRequest);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.traderId()).isEqualTo(1L);
        assertThat(response.strategy().id()).isEqualTo(10L);
        assertThat(response.strategy().htfPdArray()).isTrue();
        assertThat(response.contract()).isEqualTo("MNQU26");
        assertThat(response.pnl()).isEqualByComparingTo("174.00");
        assertThat(response.open()).isFalse();

        verify(strategyRepository, times(1)).save(any(Strategy.class));
        verify(tradeRepository, times(1)).save(any(Trade.class));
    }

    @Test
    void create_throwsResourceNotFound_whenTraderMissing() {
        when(traderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.create(tradeRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("1");

        verifyNoInteractions(strategyRepository);
        verifyNoInteractions(tradeRepository);
    }

    @Test
    void create_openTrade_hasNullExitTimeAndIsOpen() {
        TradeRequest openRequest = new TradeRequest(
                1L, strategyRequest, null, "MNQU26", 1, Direction.LONG,
                LocalDateTime.of(2026, 8, 10, 9, 0), null,
                new BigDecimal("100.00"), null, null, null, null, null
        );
        when(traderRepository.findById(1L)).thenReturn(Optional.of(trader));
        when(strategyRepository.save(any(Strategy.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TradeResponse response = tradeService.create(openRequest);

        assertThat(response.exitTime()).isNull();
        assertThat(response.open()).isTrue();
    }

    @Test
    void findById_throwsResourceNotFound_whenTradeMissing() {
        when(tradeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_reusesExistingStrategy_insteadOfCreatingNew() {
        Strategy existingStrategy = new Strategy();
        existingStrategy.setId(10L);
        Trade existingTrade = tradeWithId(1L, trader, existingStrategy);
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(existingTrade));
        when(traderRepository.findById(1L)).thenReturn(Optional.of(trader));

        TradeResponse response = tradeService.update(1L, tradeRequest);

        assertThat(response.strategy().id()).isEqualTo(10L);
        assertThat(response.strategy().htfPdArray()).isTrue();
        verify(strategyRepository, never()).save(any());
    }

    @Test
    void update_throwsResourceNotFound_whenTradeMissing() {
        when(tradeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tradeService.update(99L, tradeRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void patch_onlyOverwritesProvidedFields() {
        Trade existingTrade = tradeWithId(1L, trader, new Strategy());
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(existingTrade));
        TradePatchRequest patchRequest = new TradePatchRequest(
                null, null, null, null, null, null, null, null,
                new BigDecimal("250.00"), null, null, "Ajuste rápido de pnl"
        );

        TradeResponse response = tradeService.patch(1L, patchRequest);

        assertThat(response.pnl()).isEqualByComparingTo("250.00");
        assertThat(response.notes()).isEqualTo("Ajuste rápido de pnl");
        assertThat(response.contract()).isEqualTo("MNQU26");
    }

    @Test
    void patch_throwsResourceNotFound_whenTradeMissing() {
        when(tradeRepository.findById(99L)).thenReturn(Optional.empty());
        TradePatchRequest patchRequest = new TradePatchRequest(
                null, null, null, null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> tradeService.patch(99L, patchRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingTrade() {
        Trade existingTrade = tradeWithId(1L, trader, new Strategy());
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(existingTrade));

        tradeService.delete(1L);

        verify(tradeRepository).delete(existingTrade);
    }

    @Test
    void search_byTraderAndWonTrue_callsTraderAndPnlGreaterThan() {
        when(tradeRepository.findByTrader_IdAndPnlGreaterThan(eq(1L), eq(BigDecimal.ZERO))).thenReturn(List.of());

        tradeService.search(new TradeQueryRequest(1L, true));

        verify(tradeRepository).findByTrader_IdAndPnlGreaterThan(1L, BigDecimal.ZERO);
        verifyNoMoreInteractions(tradeRepository);
    }

    @Test
    void search_byTraderAndWonFalse_callsTraderAndPnlLessThanEqual() {
        when(tradeRepository.findByTrader_IdAndPnlLessThanEqual(eq(1L), eq(BigDecimal.ZERO))).thenReturn(List.of());

        tradeService.search(new TradeQueryRequest(1L, false));

        verify(tradeRepository).findByTrader_IdAndPnlLessThanEqual(1L, BigDecimal.ZERO);
    }

    @Test
    void search_byTraderOnly_callsFindByTraderId() {
        when(tradeRepository.findByTrader_Id(1L)).thenReturn(List.of());

        tradeService.search(new TradeQueryRequest(1L, null));

        verify(tradeRepository).findByTrader_Id(1L);
    }

    @Test
    void search_byWonOnly_callsFindByPnlGreaterThan() {
        when(tradeRepository.findByPnlGreaterThan(BigDecimal.ZERO)).thenReturn(List.of());

        tradeService.search(new TradeQueryRequest(null, true));

        verify(tradeRepository).findByPnlGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void search_byLostOnly_callsFindByPnlLessThanEqual() {
        when(tradeRepository.findByPnlLessThanEqual(BigDecimal.ZERO)).thenReturn(List.of());

        tradeService.search(new TradeQueryRequest(null, false));

        verify(tradeRepository).findByPnlLessThanEqual(BigDecimal.ZERO);
    }

    @Test
    void search_withNoFilters_returnsAllTrades() {
        Trade trade = tradeWithId(1L, trader, new Strategy());
        when(tradeRepository.findAll()).thenReturn(List.of(trade));

        List<TradeResponse> responses = tradeService.search(new TradeQueryRequest(null, null));

        assertThat(responses).hasSize(1);
        verify(tradeRepository).findAll();
    }

    private Trade tradeWithId(Long id, Trader trader, Strategy strategy) {
        Trade trade = new Trade();
        trade.setId(id);
        trade.setTrader(trader);
        trade.setStrategy(strategy);
        trade.setContract("MNQU26");
        trade.setSize(1);
        trade.setDirection(Direction.LONG);
        trade.setEntryTime(LocalDateTime.of(2026, 8, 10, 9, 0));
        trade.setEntryPrice(new BigDecimal("100.00"));
        return trade;
    }
}
