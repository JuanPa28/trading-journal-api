package com.tradingjournal.api.service;

import com.tradingjournal.api.config.RequestLoggingFilter;
import com.tradingjournal.api.dto.companion.CommerceDto;
import com.tradingjournal.api.dto.companion.PartidoDto;
import com.tradingjournal.api.dto.companion.PrediccionDto;
import com.tradingjournal.api.dto.companion.ProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

// Llama en vivo a las APIs de Mari y Pipe para traer sus entidades — nunca
// datos quemados ni copia de su base de datos, tal como pide la rúbrica.
// Trae una entidad ALEATORIA de la lista de cada compañero (no un id fijo
// pedido por quien llama) — así lo aclaró el profesor: "GET RANDOM". Cada
// nube compañera puede estar caída, lenta, o sin datos todavía sin que eso
// tumbe nuestra propia respuesta: cualquier fallo o lista vacía se atrapa y
// se devuelve null para ese campo en particular.
@Service
public class CompanionClient {

    private static final Logger log = LoggerFactory.getLogger(CompanionClient.class);

    private final RestClient mariClient;
    private final RestClient pipeClient;

    public CompanionClient(
            @Value("${companions.mari.base-url}") String mariBaseUrl,
            @Value("${companions.pipe.base-url}") String pipeBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.mariClient = RestClient.builder().baseUrl(mariBaseUrl).requestFactory(requestFactory).build();
        this.pipeClient = RestClient.builder().baseUrl(pipeBaseUrl).requestFactory(requestFactory).build();
    }

    public PartidoDto getPartido() {
        return safeGetRandom(mariClient, "/api/v1/partidos", PartidoDto[].class);
    }

    public PrediccionDto getPrediccion() {
        return safeGetRandom(mariClient, "/api/v1/predicciones", PrediccionDto[].class);
    }

    public CommerceDto getCommerce() {
        return safeGetRandom(pipeClient, "/api/v1/commerce", CommerceDto[].class);
    }

    public ProductDto getProduct() {
        return safeGetRandom(pipeClient, "/api/v1/products", ProductDto[].class);
    }

    private <T> T safeGetRandom(RestClient client, String uri, Class<T[]> arrayType) {
        try {
            String traceId = MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY);
            T[] items = client.get().uri(uri)
                    .header(RequestLoggingFilter.TRACE_ID_HEADER, traceId)
                    .retrieve().body(arrayType);
            if (items == null || items.length == 0) {
                return null;
            }
            return items[ThreadLocalRandom.current().nextInt(items.length)];
        } catch (Exception e) {
            log.warn("No se pudo obtener {} desde la API compañera: {}", uri, e.getMessage());
            return null;
        }
    }
}
