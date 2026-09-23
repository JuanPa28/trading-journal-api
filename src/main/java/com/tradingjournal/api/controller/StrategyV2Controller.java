package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.StrategyResponse;
import com.tradingjournal.api.dto.StrategyV2Response;
import com.tradingjournal.api.dto.companion.CompanionEntities;
import com.tradingjournal.api.service.CompanionClient;
import com.tradingjournal.api.service.StrategyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Espeja StrategyController completo (mismo StrategyService) y le agrega el
// GetById enriquecido con una entidad de Mari y una de Pipe, en tiempo real
// (mismo patrón que TradeV2Controller/TraderV2Controller).
@RestController
@RequestMapping("/api/v2/strategies")
public class StrategyV2Controller {

    private final StrategyService strategyService;
    private final CompanionClient companionClient;

    public StrategyV2Controller(StrategyService strategyService, CompanionClient companionClient) {
        this.strategyService = strategyService;
        this.companionClient = companionClient;
    }

    @GetMapping
    public List<StrategyResponse> findAll() {
        return strategyService.findAll();
    }

    @GetMapping("/{id}")
    public StrategyV2Response findById(@PathVariable Long id) {
        CompanionEntities companions = new CompanionEntities(
                companionClient.getPartido(),
                companionClient.getPrediccion(),
                companionClient.getCommerce(),
                companionClient.getProduct()
        );
        return new StrategyV2Response(strategyService.findById(id), companions);
    }
}
