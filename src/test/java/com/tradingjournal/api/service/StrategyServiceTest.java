package com.tradingjournal.api.service;

import com.tradingjournal.api.dto.StrategyResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.model.Strategy;
import com.tradingjournal.api.repository.StrategyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    @Mock
    private StrategyRepository strategyRepository;

    private StrategyService strategyService;

    @Test
    void findAll_mapsEveryStrategyToResponse() {
        strategyService = new StrategyService(strategyRepository);
        Strategy strategy1 = strategyWithId(1L, true, false);
        Strategy strategy2 = strategyWithId(2L, false, true);
        when(strategyRepository.findAll()).thenReturn(List.of(strategy1, strategy2));

        List<StrategyResponse> responses = strategyService.findAll();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).htfPdArray()).isTrue();
        assertThat(responses.get(1).ifvg()).isTrue();
    }

    @Test
    void findById_returnsResponse_whenStrategyExists() {
        strategyService = new StrategyService(strategyRepository);
        Strategy strategy = strategyWithId(1L, true, true);
        when(strategyRepository.findById(1L)).thenReturn(Optional.of(strategy));

        StrategyResponse response = strategyService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.htfPdArray()).isTrue();
        assertThat(response.ifvg()).isTrue();
    }

    @Test
    void findById_throwsResourceNotFound_whenStrategyMissing() {
        strategyService = new StrategyService(strategyRepository);
        when(strategyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> strategyService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void toResponse_mapsAllSevenConfluenceBooleans() {
        Strategy strategy = new Strategy();
        strategy.setId(5L);
        strategy.setHtfPdArray(true);
        strategy.setIfvg(true);
        strategy.setCisd(false);
        strategy.setFollowedRules(true);
        strategy.setContinuation(false);
        strategy.setReversal(true);
        strategy.setCorrectRisk(false);

        StrategyResponse response = StrategyService.toResponse(strategy);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.htfPdArray()).isTrue();
        assertThat(response.ifvg()).isTrue();
        assertThat(response.cisd()).isFalse();
        assertThat(response.followedRules()).isTrue();
        assertThat(response.continuation()).isFalse();
        assertThat(response.reversal()).isTrue();
        assertThat(response.correctRisk()).isFalse();
    }

    private Strategy strategyWithId(Long id, boolean htfPdArray, boolean ifvg) {
        Strategy strategy = new Strategy();
        strategy.setId(id);
        strategy.setHtfPdArray(htfPdArray);
        strategy.setIfvg(ifvg);
        strategy.setCisd(false);
        strategy.setFollowedRules(true);
        strategy.setContinuation(false);
        strategy.setReversal(false);
        strategy.setCorrectRisk(true);
        return strategy;
    }
}
