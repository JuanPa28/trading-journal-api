package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TradeQueryRequest;
import com.tradingjournal.api.dto.TradeRequest;
import com.tradingjournal.api.dto.TradeResponse;
import com.tradingjournal.api.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
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

    @PutMapping("/{id}")
    public TradeResponse update(@PathVariable Long id, @Valid @RequestBody TradeRequest request) {
        return tradeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tradeService.delete(id);
    }

    @PostMapping("/query")
    public List<TradeResponse> query(@RequestBody TradeQueryRequest request) {
        return tradeService.search(request);
    }
}
