package com.tradingjournal.api.dto.companion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Product de la API de Pipe (ecommerce-api). Contrato provisional hasta que
// Pipe publique su api/v2.
public record ProductDto(
        String id,
        String shortName,
        String fullName,
        String description,
        BigDecimal price,
        Integer amountAvailable,
        OffsetDateTime createdAt,
        Integer commerceNit
) {}
