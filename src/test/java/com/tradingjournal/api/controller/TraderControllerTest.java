package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.TraderPatchRequest;
import com.tradingjournal.api.dto.TraderRequest;
import com.tradingjournal.api.dto.TraderResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.service.TraderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TraderController.class)
class TraderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TraderService traderService;

    @Test
    void create_returns201WithBody() throws Exception {
        TraderRequest request = new TraderRequest("Juan Pablo", "juanpa28", "juanpa@example.com", new BigDecimal("5000.00"));
        TraderResponse response = new TraderResponse(1L, "Juan Pablo", "juanpa28", "juanpa@example.com", new BigDecimal("5000.00"), LocalDateTime.now());
        when(traderService.create(any(TraderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/traders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("juanpa28"));
    }

    @Test
    void create_returns400_whenEmailInvalid() throws Exception {
        TraderRequest invalidRequest = new TraderRequest("", "x", "not-an-email", new BigDecimal("-5"));

        mockMvc.perform(post("/api/traders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.fullName").exists());
    }

    @Test
    void findAll_returnsListOfTraders() throws Exception {
        TraderResponse response = new TraderResponse(1L, "Juan Pablo", "juanpa28", "juanpa@example.com", new BigDecimal("5000.00"), LocalDateTime.now());
        when(traderService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/traders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("juanpa28"));
    }

    @Test
    void findById_returns200_whenTraderExists() throws Exception {
        TraderResponse response = new TraderResponse(1L, "Juan Pablo", "juanpa28", "juanpa@example.com", new BigDecimal("5000.00"), LocalDateTime.now());
        when(traderService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/traders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void findById_returns404_whenTraderMissing() throws Exception {
        when(traderService.findById(99L)).thenThrow(new ResourceNotFoundException("Trader not found: 99"));

        mockMvc.perform(get("/api/traders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void update_returns200WithUpdatedBody() throws Exception {
        TraderRequest request = new TraderRequest("New Name", "newuser", "new@example.com", new BigDecimal("1000.00"));
        TraderResponse response = new TraderResponse(1L, "New Name", "newuser", "new@example.com", new BigDecimal("1000.00"), LocalDateTime.now());
        when(traderService.update(eq(1L), any(TraderRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/traders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newuser"));
    }

    @Test
    void patch_returns200WithOnlyPatchedFieldChanged() throws Exception {
        TraderPatchRequest patchRequest = new TraderPatchRequest(null, null, null, new BigDecimal("7500.00"));
        TraderResponse response = new TraderResponse(1L, "Juan Pablo", "juanpa28", "juanpa@example.com", new BigDecimal("7500.00"), LocalDateTime.now());
        when(traderService.patch(eq(1L), any(TraderPatchRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/traders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableFunds").value(7500.00));
    }

    @Test
    void patch_returns404_whenTraderMissing() throws Exception {
        TraderPatchRequest patchRequest = new TraderPatchRequest(null, null, null, BigDecimal.TEN);
        when(traderService.patch(eq(99L), any(TraderPatchRequest.class)))
                .thenThrow(new ResourceNotFoundException("Trader not found: 99"));

        mockMvc.perform(patch("/api/traders/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/traders/1"))
                .andExpect(status().isNoContent());
    }
}
