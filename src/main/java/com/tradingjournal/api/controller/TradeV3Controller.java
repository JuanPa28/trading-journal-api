package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TradeRequest;
import com.tradingjournal.api.dto.TradeResponse;
import com.tradingjournal.api.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// api/v3: la versión nueva de los trades, que solo existe en el código canary.
// Devuelve la misma respuesta enriquecida (setupScore y setupGrade) que ya
// reciben las rutas de siempre cuando las atiende el canary; esta ruta permite
// probar la versión nueva de forma explícita, por ejemplo en la etapa de 0 % de tráfico.
@RestController
@RequestMapping("/api/v3/trades")
public class TradeV3Controller {

    private final TradeService tradeService;

    public TradeV3Controller(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradeResponse create(@Valid @RequestBody TradeRequest request) {
        return tradeService.create(request);
    }

    @GetMapping
    public List<TradeResponse> findAll() {
        return tradeService.findAll();
    }

    @GetMapping("/{id}")
    public TradeResponse findById(@PathVariable Long id) {
        return tradeService.findById(id);
    }
}
