package com.tradingjournal.api.controller;

import com.tradingjournal.api.dto.AttachmentResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.service.AttachmentStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

// El componente de Object Storage del grupo: guarda como adjunto el JSON que
// el orquestador vaya acumulando durante el flujo entre las 3 nubes (o
// cualquier otro archivo generado en el camino), y lo devuelve luego por su
// id. Vive acá porque a Juan Pablo (OCI) le tocó este componente para todo
// el grupo — cualquiera de las 3 APIs puede llamarlo.
@RestController
@RequestMapping("/api/v2/attachments")
public class AttachmentController {

    private final AttachmentStorageService storageService;

    public AttachmentController(AttachmentStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse store(@RequestBody String content) {
        return new AttachmentResponse(storageService.store(content));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public String retrieve(@PathVariable String id) {
        String content = storageService.retrieve(id);
        if (content == null) {
            throw new ResourceNotFoundException("Attachment not found: " + id);
        }
        return content;
    }
}
