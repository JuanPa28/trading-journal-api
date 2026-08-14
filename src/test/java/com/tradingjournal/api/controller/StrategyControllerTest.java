package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.StrategyResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.service.StrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
class StrategyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StrategyService strategyService;

    @Test
    void findAll_returnsListOfStrategies() throws Exception {
        StrategyResponse response = new StrategyResponse(1L, true, false, false, true, false, false, true, LocalDateTime.now());
        when(strategyService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].htfPdArray").value(true));
    }

    @Test
    void findById_returns200_whenStrategyExists() throws Exception {
        StrategyResponse response = new StrategyResponse(1L, true, false, false, true, false, false, true, LocalDateTime.now());
        when(strategyService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/strategies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findById_returns404_whenStrategyMissing() throws Exception {
        when(strategyService.findById(99L)).thenThrow(new ResourceNotFoundException("Strategy not found: 99"));

        mockMvc.perform(get("/api/strategies/99"))
                .andExpect(status().isNotFound());
    }
}
