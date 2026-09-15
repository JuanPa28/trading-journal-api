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

// Llama en vivo a las APIs de Mari y Pipe para traer sus entidades — nunca
// datos quemados ni copia de su base de datos, tal como pide la rúbrica.
// Cada nube compañera puede estar caída o lenta sin que eso tumbe nuestra
// propia respuesta: cualquier fallo se atrapa y se devuelve null para ese
// campo en particular.
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

    public PartidoDto getPartido(Long id) {
        return safeGet(mariClient, "/api/v1/partidos/" + id, PartidoDto.class);
    }

    public PrediccionDto getPrediccion(Long id) {
        return safeGet(mariClient, "/api/v1/predicciones/" + id, PrediccionDto.class);
    }

    public CommerceDto getCommerce(Integer nit) {
        return safeGet(pipeClient, "/api/v1/commerce/" + nit, CommerceDto.class);
    }

    public ProductDto getProduct(String id) {
        return safeGet(pipeClient, "/api/v1/products/" + id, ProductDto.class);
    }

    private <T> T safeGet(RestClient client, String uri, Class<T> type) {
        try {
            String traceId = MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY);
            return client.get().uri(uri)
                    .header(RequestLoggingFilter.TRACE_ID_HEADER, traceId)
                    .retrieve().body(type);
        } catch (Exception e) {
            log.warn("No se pudo obtener {} desde la API compañera: {}", uri, e.getMessage());
            return null;
        }
    }
}
