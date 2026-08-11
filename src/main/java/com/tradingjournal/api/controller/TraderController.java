package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TraderRequest;
import com.tradingjournal.api.dto.TraderResponse;
import com.tradingjournal.api.service.TraderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/traders")
public class TraderController {

    private final TraderService traderService;

    public TraderController(TraderService traderService) {
        this.traderService = traderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TraderResponse create(@Valid @RequestBody TraderRequest request) {
        return traderService.create(request);
    }

    @GetMapping
    public List<TraderResponse> findAll() {
        return traderService.findAll();
    }

    @GetMapping("/{id}")
    public TraderResponse findById(@PathVariable Long id) {
        return traderService.findById(id);
    }

    @PutMapping("/{id}")
    public TraderResponse update(@PathVariable Long id, @Valid @RequestBody TraderRequest request) {
        return traderService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        traderService.delete(id);
    }
}
