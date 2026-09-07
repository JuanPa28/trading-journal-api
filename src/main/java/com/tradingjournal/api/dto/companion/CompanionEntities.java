package com.tradingjournal.api.dto.companion;

// Agrupa lo que trae cada compañero para un trade enriquecido de api/v2. Un
// campo en null significa que esa API compañera no respondió (ver
// CompanionClient) — no bloquea el resto de la respuesta.
public record CompanionEntities(
        PartidoDto partido,
        PrediccionDto prediccion,
        CommerceDto commerce,
        ProductDto product
) {}
