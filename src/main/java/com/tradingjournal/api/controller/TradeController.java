package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TradePatchRequest;
import com.tradingjournal.api.dto.TradeQueryRequest;
import com.tradingjournal.api.dto.TradeRequest;
import com.tradingjournal.api.dto.TradeResponse;
import com.tradingjournal.api.service.TradeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PatchMapping("/{id}")
    public TradeResponse patch(@PathVariable Long id, @Valid @RequestBody TradePatchRequest request) {
        return tradeService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tradeService.delete(id);
    }

    // No RequestMethod.QUERY exists in Spring 7.0.8 yet, so method is left unrestricted
    // here and checked manually against the raw HTTP method line.
    @RequestMapping("/query")
    public ResponseEntity<?> query(HttpServletRequest servletRequest, @RequestBody TradeQueryRequest request) {
        if (!"QUERY".equalsIgnoreCase(servletRequest.getMethod())) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        return ResponseEntity.ok(tradeService.search(request));
    }
}
