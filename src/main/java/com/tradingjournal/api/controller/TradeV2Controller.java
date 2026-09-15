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
// Los IDs de las entidades compañeras son parámetros porque, a diferencia
// del Trade, no existe una relación de negocio real entre los 3 dominios
// (trading, apuestas deportivas, ecommerce) — se seleccionan explícitamente,
// igual que en el ejemplo de la rúbrica (Adoptante/Mascota/Grupo).
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
    public TradeV2Response findById(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long partidoId,
            @RequestParam(defaultValue = "1") Long prediccionId,
            @RequestParam(defaultValue = "1") Integer commerceNit,
            @RequestParam(defaultValue = "1") String productId) {
        CompanionEntities companions = new CompanionEntities(
                companionClient.getPartido(partidoId),
                companionClient.getPrediccion(prediccionId),
                companionClient.getCommerce(commerceNit),
                companionClient.getProduct(productId)
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
