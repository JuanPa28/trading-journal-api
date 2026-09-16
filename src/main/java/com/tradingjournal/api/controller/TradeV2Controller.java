package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TradePatchRequest;
import com.tradingjournal.api.dto.TradeQueryRequest;
import com.tradingjournal.api.dto.TradeRequest;
import com.tradingjournal.api.dto.TradeResponse;
import com.tradingjournal.api.dto.TradeV2Response;
import com.tradingjournal.api.dto.companion.CompanionEntities;
import com.tradingjournal.api.service.CompanionClient;
import com.tradingjournal.api.service.TradeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Espeja TradeController completo (misma lógica, mismo TradeService) y le
// agrega la única diferencia que pide la rúbrica: el GetById trae además una
// entidad de Mari (Partido/Predicción) y una de Pipe (Commerce/Product) vía
// HTTP real contra sus APIs desplegadas en otra nube — nunca datos quemados
// ni copia de su base de datos. GetAll y el resto de operaciones no llevan
// agregación, tal como aclaró el profesor.
//
// Cuál registro exacto trae de cada compañero se elige solo (al azar, entre
// lo que haya en su lista) — no se le pide un id a quien llama. El profesor
// lo llamó "GET RANDOM": no existe una relación de negocio real entre los 3
// dominios (trading, apuestas deportivas, ecommerce), así que cualquier
// registro real de cada uno sirve, mientras no sea un dato quemado.
@RestController
@RequestMapping("/api/v2/trades")
public class TradeV2Controller {

    private final TradeService tradeService;
    private final CompanionClient companionClient;

    public TradeV2Controller(TradeService tradeService, CompanionClient companionClient) {
        this.tradeService = tradeService;
        this.companionClient = companionClient;
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
    public TradeV2Response findById(@PathVariable Long id) {
        CompanionEntities companions = new CompanionEntities(
                companionClient.getPartido(),
                companionClient.getPrediccion(),
                companionClient.getCommerce(),
                companionClient.getProduct()
        );
        return new TradeV2Response(tradeService.findById(id), companions);
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

    @RequestMapping("/query")
    public ResponseEntity<?> query(HttpServletRequest servletRequest, @RequestBody TradeQueryRequest request) {
        if (!"QUERY".equalsIgnoreCase(servletRequest.getMethod())) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        return ResponseEntity.ok(tradeService.search(request));
    }
}
