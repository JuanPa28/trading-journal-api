package com.tradingjournal.api.controller;

import com.tradingjournal.api.service.AttachmentStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttachmentController.class)
class AttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttachmentStorageService storageService;

    @Test
    void store_savesBodyAndReturnsGeneratedId() throws Exception {
        when(storageService.store("{\"trade\":1}")).thenReturn("abc-123.json");

        mockMvc.perform(post("/api/v2/attachments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abc-123.json"));
    }

    @Test
    void retrieve_returnsStoredContent() throws Exception {
        when(storageService.retrieve("abc-123.json")).thenReturn("{\"trade\":1}");

        mockMvc.perform(get("/api/v2/attachments/abc-123.json"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"trade\":1}"));
    }

    @Test
    void retrieve_returns404_whenAttachmentDoesNotExist() throws Exception {
        when(storageService.retrieve("missing.json")).thenReturn(null);

        mockMvc.perform(get("/api/v2/attachments/missing.json"))
                .andExpect(status().isNotFound());
    }
}
