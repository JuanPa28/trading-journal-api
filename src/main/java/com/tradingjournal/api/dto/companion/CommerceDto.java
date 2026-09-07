package com.tradingjournal.api.dto.companion;

import java.time.OffsetDateTime;

// Commerce de la API de Pipe (ecommerce-api). Sus campos ya vienen en
// camelCase, sin necesidad de @JsonProperty. Contrato provisional hasta que
// Pipe publique su api/v2.
public record CommerceDto(
        Integer nit,
        String legalName,
        String shortName,
        String address,
        String contactNumber,
        String email,
        OffsetDateTime createdAt
) {}
