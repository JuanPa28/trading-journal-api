package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TradeV2Response;
import com.tradingjournal.api.dto.companion.CompanionEntities;
import com.tradingjournal.api.service.CompanionClient;
import com.tradingjournal.api.service.TradeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Entrega 2: no reemplaza /api/trades, lo complementa. Trae en vivo una
// entidad de Mari (Partido/Predicción) y una de Pipe (Commerce/Product) vía
// HTTP real contra sus APIs desplegadas en otra nube — nunca datos
// quemados ni copia de su base de datos.
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
}
