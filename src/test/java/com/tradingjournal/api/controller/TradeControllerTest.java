package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.*;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.model.Direction;
import com.tradingjournal.api.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradeController.class)
class TradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TradeService tradeService;

    private final StrategyRequest strategyRequest = new StrategyRequest(true, false, false, true, false, false, true);
    private final StrategyResponse strategyResponse = new StrategyResponse(10L, true, false, false, true, false, false, true, LocalDateTime.now());

    @Test
    void create_returns201WithBody() throws Exception {
        TradeRequest request = tradeRequest();
        TradeResponse response = tradeResponse(100L, false, new BigDecimal("174.00"));
        when(tradeService.create(any(TradeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.strategy.id").value(10));
    }

    @Test
    void create_returns400_whenContractBlank() throws Exception {
        TradeRequest invalidRequest = new TradeRequest(
                1L, strategyRequest, null, "", null, null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findAll_returnsListOfTrades() throws Exception {
        when(tradeService.findAll()).thenReturn(List.of(tradeResponse(1L, false, new BigDecimal("50.00"))));

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void findById_returns404_whenTradeMissing() throws Exception {
        when(tradeService.findById(99L)).thenThrow(new ResourceNotFoundException("Trade not found: 99"));

        mockMvc.perform(get("/api/trades/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/trades/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void query_withLiteralHttpQueryMethod_returns200WithFilteredResults() throws Exception {
        TradeQueryRequest queryRequest = new TradeQueryRequest(1L, true);
        when(tradeService.search(any(TradeQueryRequest.class))).thenReturn(List.of(tradeResponse(1L, false, new BigDecimal("174.00"))));

        mockMvc.perform(request(HttpMethod.valueOf("QUERY"), "/api/trades/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pnl").value(174.00));
    }

    @Test
    void query_withGetInstead_returns405() throws Exception {
        mockMvc.perform(get("/api/trades/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    private TradeRequest tradeRequest() {
        return new TradeRequest(
                1L, strategyRequest, "2967867693", "MNQU26", 2, Direction.SHORT,
                LocalDateTime.of(2026, 8, 10, 8, 40),
                LocalDateTime.of(2026, 8, 10, 8, 41),
                new BigDecimal("29770.25"), new BigDecimal("29726.75"),
                new BigDecimal("174.00"), new BigDecimal("1.00"), new BigDecimal("1.44"),
                "Buen setup"
        );
    }

    private TradeResponse tradeResponse(Long id, boolean open, BigDecimal pnl) {
        return new TradeResponse(
                id, 1L, strategyResponse, "2967867693", "MNQU26", 2, Direction.SHORT,
                LocalDateTime.of(2026, 8, 10, 8, 40),
                open ? null : LocalDateTime.of(2026, 8, 10, 8, 41),
                new BigDecimal("29770.25"), new BigDecimal("29726.75"),
                pnl, new BigDecimal("1.00"), new BigDecimal("1.44"),
                "Buen setup", open
        );
    }
}
