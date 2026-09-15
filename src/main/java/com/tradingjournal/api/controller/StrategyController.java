package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.StrategyResponse;
import com.tradingjournal.api.service.StrategyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// v2 solo espeja v1 aquí: la agregación de entidades de compañeros va únicamente
// en Trade (ver TradeV2Controller), no en cada entidad.
@RestController
@RequestMapping({"/api/strategies", "/api/v1/strategies", "/api/v2/strategies"})
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    public List<StrategyResponse> findAll() {
        return strategyService.findAll();
    }

    @GetMapping("/{id}")
    public StrategyResponse findById(@PathVariable Long id) {
        return strategyService.findById(id);
    }
}
