package com.tradingjournal.api.service;

import com.tradingjournal.api.dto.StrategyResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.model.Strategy;
import com.tradingjournal.api.repository.StrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StrategyService {

    private final StrategyRepository strategyRepository;

    public StrategyService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    public List<StrategyResponse> findAll() {
        return strategyRepository.findAll().stream().map(StrategyService::toResponse).toList();
    }

    public StrategyResponse findById(Long id) {
        return toResponse(strategyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + id)));
    }

    static StrategyResponse toResponse(Strategy strategy) {
        return new StrategyResponse(
                strategy.getId(),
                strategy.isHtfPdArray(),
                strategy.isIfvg(),
                strategy.isCisd(),
                strategy.isFollowedRules(),
                strategy.isContinuation(),
                strategy.isReversal(),
                strategy.isCorrectRisk(),
                strategy.getCreatedAt()
        );
    }
}
