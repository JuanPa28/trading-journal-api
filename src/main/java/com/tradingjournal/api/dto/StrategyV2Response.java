package com.tradingjournal.api.dto;

import com.tradingjournal.api.dto.companion.CompanionEntities;

// Respuesta de api/v2: la Strategy de siempre + al menos una entidad de cada
// uno de los otros dos compañeros del grupo, obtenida en tiempo real.
public record StrategyV2Response(
        StrategyResponse strategy,
        CompanionEntities companions
) {}
