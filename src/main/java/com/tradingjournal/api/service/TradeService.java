package com.tradingjournal.api.service;

import com.tradingjournal.api.dto.StrategyRequest;
import com.tradingjournal.api.dto.TradePatchRequest;
import com.tradingjournal.api.dto.TradeQueryRequest;
import com.tradingjournal.api.dto.TradeRequest;
import com.tradingjournal.api.dto.TradeResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.model.Strategy;
import com.tradingjournal.api.model.Trade;
import com.tradingjournal.api.model.Trader;
import com.tradingjournal.api.repository.StrategyRepository;
import com.tradingjournal.api.repository.TradeRepository;
import com.tradingjournal.api.repository.TraderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class TradeService {

    private final TradeRepository tradeRepository;
    private final TraderRepository traderRepository;
    private final StrategyRepository strategyRepository;

    public TradeService(TradeRepository tradeRepository, TraderRepository traderRepository, StrategyRepository strategyRepository) {
        this.tradeRepository = tradeRepository;
        this.traderRepository = traderRepository;
        this.strategyRepository = strategyRepository;
    }

    public TradeResponse create(TradeRequest request) {
        Trader trader = traderRepository.findById(request.traderId())
                .orElseThrow(() -> new ResourceNotFoundException("Trader not found: " + request.traderId()));

        Strategy strategy = new Strategy();
        applyStrategy(strategy, request.strategy());
        strategy = strategyRepository.save(strategy);

        Trade trade = new Trade();
        trade.setTrader(trader);
        trade.setStrategy(strategy);
        applyTrade(trade, request);

        return toResponse(tradeRepository.save(trade));
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> findAll() {
        return tradeRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TradeResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    public TradeResponse update(Long id, TradeRequest request) {
        Trade trade = getOrThrow(id);

        Trader trader = traderRepository.findById(request.traderId())
                .orElseThrow(() -> new ResourceNotFoundException("Trader not found: " + request.traderId()));
        trade.setTrader(trader);

        applyStrategy(trade.getStrategy(), request.strategy());
        applyTrade(trade, request);

        return toResponse(trade);
    }

    public TradeResponse patch(Long id, TradePatchRequest request) {
        Trade trade = getOrThrow(id);
        if (request.externalId() != null) trade.setExternalId(request.externalId());
        if (request.contract() != null) trade.setContract(request.contract());
        if (request.size() != null) trade.setSize(request.size());
        if (request.direction() != null) trade.setDirection(request.direction());
        if (request.entryTime() != null) trade.setEntryTime(request.entryTime());
        if (request.exitTime() != null) trade.setExitTime(request.exitTime());
        if (request.entryPrice() != null) trade.setEntryPrice(request.entryPrice());
        if (request.exitPrice() != null) trade.setExitPrice(request.exitPrice());
        if (request.pnl() != null) trade.setPnl(request.pnl());
        if (request.commissions() != null) trade.setCommissions(request.commissions());
        if (request.fees() != null) trade.setFees(request.fees());
        if (request.notes() != null) trade.setNotes(request.notes());
        return toResponse(trade);
    }

    public void delete(Long id) {
        tradeRepository.delete(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> search(TradeQueryRequest query) {
        Long traderId = query.traderId();
        Boolean won = query.won();

        List<Trade> trades;
        if (traderId != null && won != null) {
            trades = won
                    ? tradeRepository.findByTrader_IdAndPnlGreaterThan(traderId, BigDecimal.ZERO)
                    : tradeRepository.findByTrader_IdAndPnlLessThanEqual(traderId, BigDecimal.ZERO);
        } else if (traderId != null) {
            trades = tradeRepository.findByTrader_Id(traderId);
        } else if (won != null) {
            trades = won
                    ? tradeRepository.findByPnlGreaterThan(BigDecimal.ZERO)
                    : tradeRepository.findByPnlLessThanEqual(BigDecimal.ZERO);
        } else {
            trades = tradeRepository.findAll();
        }
        return trades.stream().map(this::toResponse).toList();
    }

    private void applyStrategy(Strategy strategy, StrategyRequest request) {
        strategy.setHtfPdArray(request.htfPdArray());
        strategy.setIfvg(request.ifvg());
        strategy.setCisd(request.cisd());
        strategy.setFollowedRules(request.followedRules());
        strategy.setContinuation(request.continuation());
        strategy.setReversal(request.reversal());
        strategy.setCorrectRisk(request.correctRisk());
    }

    private void applyTrade(Trade trade, TradeRequest request) {
        trade.setExternalId(request.externalId());
        trade.setContract(request.contract());
        trade.setSize(request.size());
        trade.setDirection(request.direction());
        trade.setEntryTime(request.entryTime());
        trade.setExitTime(request.exitTime());
        trade.setEntryPrice(request.entryPrice());
        trade.setExitPrice(request.exitPrice());
        trade.setPnl(request.pnl());
        trade.setCommissions(request.commissions());
        trade.setFees(request.fees());
        trade.setNotes(request.notes());
    }

    private Trade getOrThrow(Long id) {
        return tradeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade not found: " + id));
    }

    private TradeResponse toResponse(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getTrader().getId(),
                StrategyService.toResponse(trade.getStrategy()),
                trade.getExternalId(),
                trade.getContract(),
                trade.getSize(),
                trade.getDirection(),
                trade.getEntryTime(),
                trade.getExitTime(),
                trade.getEntryPrice(),
                trade.getExitPrice(),
                trade.getPnl(),
                trade.getCommissions(),
                trade.getFees(),
                trade.getNotes(),
                trade.isOpen()
        );
    }
}
