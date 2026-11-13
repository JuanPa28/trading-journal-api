package com.tradingjournal.api.service;

import com.tradingjournal.api.config.OutboundFaultHeaderFilter;
import com.tradingjournal.api.config.RequestLoggingFilter;
import com.tradingjournal.api.dto.companion.CommerceDto;
import com.tradingjournal.api.dto.companion.PartidoDto;
import com.tradingjournal.api.dto.companion.PrediccionDto;
import com.tradingjournal.api.dto.companion.ProductDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

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
    private final long faultLatencyMs;
    private final String faultTarget;
    private final long callTimeoutMs;
    private final Map<String, CircuitBreaker> breakers;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // Los tiempos de espera salen de la configuración, y por defecto valen lo de
    // siempre (5 s para conectar y 10 s para leer). Así la ronda 0 de un
    // experimento de caos mide el comportamiento actual y la mejora se activa
    // cambiando una variable, sin tocar el código.
    //
    // La latencia saliente es un interruptor de fallo: apagado por defecto
    // (OUTBOUND_FAULT_LATENCY_MS=0). Añade esa espera antes de la llamada hacia
    // el compañero indicado (mari, pipe o all) para simular una dependencia lenta
    // en otra nube.
    //
    // Dos patrones de resiliencia, AMBOS APAGADOS por defecto (el comportamiento
    // sin proteger es el de la ronda 0):
    //  - Timeout por dependencia (COMPANIONS_CALL_TIMEOUT_MS > 0): tope de tiempo
    //    para TODA la llamada hacia un compañero. Si se pasa, esa entidad queda en
    //    null y la petición sigue. Es distinto del tiempo de lectura del socket:
    //    cubre también la espera inyectada.
    //  - Circuit breaker por compañero (RESILIENCE_CB_ENABLED=true): si la mitad de
    //    las últimas llamadas fallan o son lentas, el circuito se abre y se responde
    //    null al instante, sin esperar al compañero, hasta que pase el tiempo de
    //    reposo y una llamada de prueba salga bien.
    @Autowired
    public CompanionClient(
            @Value("${companions.mari.base-url}") String mariBaseUrl,
            @Value("${companions.pipe.base-url}") String pipeBaseUrl,
            @Value("${companions.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${companions.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${companions.fault.latency-ms:0}") long faultLatencyMs,
            @Value("${companions.fault.target:all}") String faultTarget,
            @Value("${companions.resilience.call-timeout-ms:0}") long callTimeoutMs,
            @Value("${companions.resilience.circuit-breaker.enabled:false}") boolean circuitBreakerEnabled,
            @Value("${companions.resilience.circuit-breaker.window-size:10}") int windowSize,
            @Value("${companions.resilience.circuit-breaker.minimum-calls:5}") int minimumCalls,
            @Value("${companions.resilience.circuit-breaker.failure-rate-percent:50}") float failureRatePercent,
            @Value("${companions.resilience.circuit-breaker.slow-call-ms:1000}") long slowCallMs,
            @Value("${companions.resilience.circuit-breaker.open-seconds:10}") long openSeconds) {
        this.faultLatencyMs = faultLatencyMs;
        this.faultTarget = faultTarget;
        this.callTimeoutMs = callTimeoutMs;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.mariClient = RestClient.builder().baseUrl(mariBaseUrl).requestFactory(requestFactory).build();
        this.pipeClient = RestClient.builder().baseUrl(pipeBaseUrl).requestFactory(requestFactory).build();
        this.breakers = circuitBreakerEnabled
                ? Map.of("mari", newBreaker("mari", windowSize, minimumCalls, failureRatePercent, slowCallMs, openSeconds),
                        "pipe", newBreaker("pipe", windowSize, minimumCalls, failureRatePercent, slowCallMs, openSeconds))
                : Map.of();
    }

    // Constructor sin patrones de resiliencia: el comportamiento original.
    public CompanionClient(String mariBaseUrl, String pipeBaseUrl, long connectTimeoutMs, long readTimeoutMs,
                           long faultLatencyMs, String faultTarget) {
        this(mariBaseUrl, pipeBaseUrl, connectTimeoutMs, readTimeoutMs, faultLatencyMs, faultTarget,
                0, false, 10, 5, 50, 1000, 10);
    }

    private static CircuitBreaker newBreaker(String name, int windowSize, int minimumCalls, float failureRatePercent,
                                             long slowCallMs, long openSeconds) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(windowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRatePercent)
                .slowCallDurationThreshold(Duration.ofMillis(slowCallMs))
                .slowCallRateThreshold(failureRatePercent)
                .waitDurationInOpenState(Duration.ofSeconds(openSeconds))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        CircuitBreaker breaker = CircuitBreaker.of("companion-" + name, config);
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("Circuit breaker {}: {}", breaker.getName(), event.getStateTransition()));
        return breaker;
    }

    public PartidoDto getPartido() {
        return safeGetRandom(mariClient, "mari", "/api/v1/partidos", PartidoDto[].class);
    }

    public PrediccionDto getPrediccion() {
        return safeGetRandom(mariClient, "mari", "/api/v1/predicciones", PrediccionDto[].class);
    }

    public CommerceDto getCommerce() {
        return safeGetRandom(pipeClient, "pipe", "/api/v1/commerce", CommerceDto[].class);
    }

    public ProductDto getProduct() {
        return safeGetRandom(pipeClient, "pipe", "/api/v1/products", ProductDto[].class);
    }

    // Latencia saliente: la de la variable de entorno (para todo el tráfico) o la de la
    // cabecera de la petición (solo para esa petición); se aplica la mayor de las dos.
    private void injectLatency(String target, long headerLatencyMs, String headerTarget) throws InterruptedException {
        long delay = 0;
        if (faultLatencyMs > 0 && ("all".equalsIgnoreCase(faultTarget) || target.equalsIgnoreCase(faultTarget))) {
            delay = faultLatencyMs;
        }
        if (headerLatencyMs > 0 && ("all".equalsIgnoreCase(headerTarget) || target.equalsIgnoreCase(headerTarget))) {
            delay = Math.max(delay, headerLatencyMs);
        }
        if (delay > 0) {
            Thread.sleep(delay);
        }
    }

    // Una llamada completa a un compañero. Lanza excepción si falla, para que el
    // circuit breaker la cuente; una lista vacía NO es un fallo.
    private <T> T fetch(RestClient client, String target, String uri, Class<T[]> arrayType, String traceId,
                        long headerLatencyMs, String headerTarget) throws InterruptedException {
        injectLatency(target, headerLatencyMs, headerTarget);
        T[] items = client.get().uri(uri)
                .header(RequestLoggingFilter.TRACE_ID_HEADER, traceId)
                .retrieve().body(arrayType);
        if (items == null || items.length == 0) {
            return null;
        }
        return items[ThreadLocalRandom.current().nextInt(items.length)];
    }

    // Aplica el tope de tiempo de toda la llamada, si está activo.
    private <T> T withTimeout(Supplier<T> call) {
        if (callTimeoutMs <= 0) {
            return call.get();
        }
        Future<T> future = executor.submit(call::get);
        try {
            return future.get(callTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("la llamada superó el tiempo máximo de " + callTimeoutMs + " ms");
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private <T> T safeGetRandom(RestClient client, String target, String uri, Class<T[]> arrayType) {
        // El trace id se toma aquí porque el MDC es local al hilo y la llamada puede correr en otro.
        String traceId = MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY);
        long headerLatencyMs = parseLong(MDC.get(OutboundFaultHeaderFilter.LATENCY_MDC_KEY));
        String headerTarget = MDC.get(OutboundFaultHeaderFilter.TARGET_MDC_KEY);
        Supplier<T> call = () -> withTimeout(() -> {
            try {
                return fetch(client, target, uri, arrayType, traceId, headerLatencyMs, headerTarget);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
        try {
            CircuitBreaker breaker = breakers.get(target);
            return breaker != null ? breaker.executeSupplier(call) : call.get();
        } catch (CallNotPermittedException e) {
            log.warn("Circuito abierto hacia {}: se responde sin esperar ({})", target, uri);
            return null;
        } catch (Exception e) {
            log.warn("No se pudo obtener {} desde la API compañera: {}", uri, e.getMessage());
            return null;
        }
    }
}
