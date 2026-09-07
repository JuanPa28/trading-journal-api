package com.tradingjournal.api.dto.companion;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.OffsetDateTime;

// Representa el Partido de la API de Mari (StatsNostra). @JsonAlias acepta
// el snake_case que ella devuelve al leer, pero nuestra propia respuesta
// sigue escribiendo camelCase, igual que el resto de esta API. Contrato
// provisional hasta que Mari publique su api/v2.
public record PartidoDto(
        Long id,
        String deporte,
        String liga,
        @JsonAlias("equipo_local") String equipoLocal,
        @JsonAlias("equipo_visitante") String equipoVisitante,
        @JsonAlias("fecha_hora") OffsetDateTime fechaHora,
        String estado,
        @JsonAlias("resultado_local") Integer resultadoLocal,
        @JsonAlias("resultado_visitante") Integer resultadoVisitante
) {}
