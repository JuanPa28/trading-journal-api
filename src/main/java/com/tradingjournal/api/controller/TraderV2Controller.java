package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TraderPatchRequest;
import com.tradingjournal.api.dto.TraderRequest;
import com.tradingjournal.api.dto.TraderResponse;
import com.tradingjournal.api.dto.TraderV2Response;
import com.tradingjournal.api.dto.companion.CompanionEntities;
import com.tradingjournal.api.service.CompanionClient;
import com.tradingjournal.api.service.TraderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Espeja TraderController completo (mismo TraderService) y le agrega el
// GetById enriquecido con una entidad de Mari y una de Pipe, en tiempo real
// (mismo patrón que TradeV2Controller/StrategyV2Controller).
@RestController
@RequestMapping("/api/v2/traders")
public class TraderV2Controller {

    private final TraderService traderService;
    private final CompanionClient companionClient;

    public TraderV2Controller(TraderService traderService, CompanionClient companionClient) {
        this.traderService = traderService;
        this.companionClient = companionClient;
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
    public TraderV2Response findById(@PathVariable Long id) {
        CompanionEntities companions = new CompanionEntities(
                companionClient.getPartido(),
                companionClient.getPrediccion(),
                companionClient.getCommerce(),
                companionClient.getProduct()
        );
        return new TraderV2Response(traderService.findById(id), companions);
    }

    @PutMapping("/{id}")
    public TraderResponse update(@PathVariable Long id, @Valid @RequestBody TraderRequest request) {
        return traderService.update(id, request);
    }

    @PatchMapping("/{id}")
    public TraderResponse patch(@PathVariable Long id, @Valid @RequestBody TraderPatchRequest request) {
        return traderService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        traderService.delete(id);
    }
}
