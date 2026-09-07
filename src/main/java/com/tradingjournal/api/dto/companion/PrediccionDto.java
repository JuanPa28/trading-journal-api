package com.tradingjournal.api.dto.companion;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.OffsetDateTime;

// Predicción de la API de Mari (StatsNostra). @JsonAlias acepta el
// snake_case que ella devuelve al leer, pero nuestra propia respuesta sigue
// escribiendo camelCase. Contrato provisional hasta que Mari publique su
// api/v2.
public record PrediccionDto(
        Long id,
        @JsonAlias("apostador_id") Long apostadorId,
        @JsonAlias("partido_id") Long partidoId,
        @JsonAlias("goles_local_pred") Integer golesLocalPred,
        @JsonAlias("goles_visitante_pred") Integer golesVisitantePred,
        @JsonAlias("fecha_prediccion") OffsetDateTime fechaPrediccion,
        Boolean acertada,
        Integer puntos
) {}
