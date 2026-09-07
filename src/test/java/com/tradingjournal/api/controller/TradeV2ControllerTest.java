package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.StrategyResponse;
import com.tradingjournal.api.dto.TradeResponse;
import com.tradingjournal.api.dto.companion.CommerceDto;
import com.tradingjournal.api.dto.companion.PartidoDto;
import com.tradingjournal.api.dto.companion.PrediccionDto;
import com.tradingjournal.api.dto.companion.ProductDto;
import com.tradingjournal.api.model.Direction;
import com.tradingjournal.api.service.CompanionClient;
import com.tradingjournal.api.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TradeV2Controller.class)
class TradeV2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TradeService tradeService;

    @MockitoBean
    private CompanionClient companionClient;

    private final StrategyResponse strategyResponse =
            new StrategyResponse(10L, true, false, false, true, false, false, true, LocalDateTime.now());

    private final TradeResponse tradeResponse = new TradeResponse(
            1L, 1L, strategyResponse, "ext-1", "MNQU26", 2, Direction.LONG,
            LocalDateTime.now(), null, new BigDecimal("19500.25"), null, null, null, null, null, true);

    @Test
    void findById_mergesTradeWithBothCompanionsEntities() throws Exception {
        when(tradeService.findById(1L)).thenReturn(tradeResponse);
        when(companionClient.getPartido(anyLong())).thenReturn(
                new PartidoDto(1L, "Futbol", "Liga Test", "Equipo A", "Equipo B", null, "PROGRAMADO", null, null));
        when(companionClient.getPrediccion(anyLong())).thenReturn(
                new PrediccionDto(1L, 1L, 1L, 2, 1, null, true, 3));
        when(companionClient.getCommerce(any())).thenReturn(
                new CommerceDto(1, "Tienda S.A.S", "Tienda", "Calle 1", "3000000000", "tienda@example.com", null));
        when(companionClient.getProduct(any())).thenReturn(
                new ProductDto("p1", "Prod", "Producto completo", "desc", null, 10, null, 1));

        mockMvc.perform(get("/api/v2/trades/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade.id").value(1))
                .andExpect(jsonPath("$.companions.partido.deporte").value("Futbol"))
                .andExpect(jsonPath("$.companions.prediccion.puntos").value(3))
                .andExpect(jsonPath("$.companions.commerce.legalName").value("Tienda S.A.S"))
                .andExpect(jsonPath("$.companions.product.id").value("p1"));
    }

    @Test
    void findById_returnsNullCompanions_whenCompanionApisAreUnreachable() throws Exception {
        when(tradeService.findById(1L)).thenReturn(tradeResponse);
        when(companionClient.getPartido(anyLong())).thenReturn(null);
        when(companionClient.getPrediccion(anyLong())).thenReturn(null);
        when(companionClient.getCommerce(any())).thenReturn(null);
        when(companionClient.getProduct(any())).thenReturn(null);

        mockMvc.perform(get("/api/v2/trades/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trade.id").value(1))
                .andExpect(jsonPath("$.companions.partido").doesNotExist())
                .andExpect(jsonPath("$.companions.commerce").doesNotExist());
    }
}
