package com.tradingjournal.api.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionClientTest {

    // Puerto 9 (discard): la conexión se rechaza al instante, así que cualquier
    // espera medida viene solo de la latencia inyectada.
    private static final String DEAD = "http://127.0.0.1:9";

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicBoolean failing = new AtomicBoolean(false);
    private volatile long serverDelayMs = 0;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // Un compañero falso: responde una lista vacía (200) o un 500, con una espera opcional.
    private String companion() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            try {
                if (serverDelayMs > 0) {
                    Thread.sleep(serverDelayMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = (failing.get() ? "{\"error\":\"caido\"}" : "[]").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(failing.get() ? 500 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static long millis(Runnable call) {
        long start = System.nanoTime();
        call.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static CompanionClient withBreaker(String url, int window, int minimumCalls, long slowCallMs, long openSeconds) {
        return new CompanionClient(url, url, 5000, 10000, 0, "all", 0, true, window, minimumCalls, 50, slowCallMs, openSeconds);
    }

    // ---- interruptor de latencia saliente (comportamiento de la ronda 0)

    @Test
    void withoutFaultTheCallFailsFastAndReturnsNull() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 0, "all");

        long elapsed = millis(() -> assertThat(client.getPartido()).isNull());

        assertThat(elapsed).isLessThan(1500);
    }

    @Test
    void injectedLatencyDelaysTheCallToTheTargetedCompanion() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 300, "mari");

        long toMari = millis(() -> client.getPartido());

        assertThat(toMari).isGreaterThanOrEqualTo(280);
    }

    @Test
    void injectedLatencyDoesNotTouchTheOtherCompanion() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 600, "mari");

        long toPipe = millis(() -> client.getCommerce());

        assertThat(toPipe).isLessThan(500);
    }

    @Test
    void targetAllDelaysEveryCompanion() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 300, "all");

        assertThat(millis(() -> client.getCommerce())).isGreaterThanOrEqualTo(280);
        assertThat(millis(() -> client.getPrediccion())).isGreaterThanOrEqualTo(280);
    }

    // ---- latencia por cabecera (por petición)

    @Test
    void headerLatencyDelaysOnlyTheTargetedCompanionOfThatRequest() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 0, "all");
        org.slf4j.MDC.put("outboundFaultLatencyMs", "400");
        org.slf4j.MDC.put("outboundFaultTarget", "mari");
        try {
            assertThat(millis(() -> client.getPartido())).isGreaterThanOrEqualTo(380);
            assertThat(millis(() -> client.getCommerce())).isLessThan(300);
        } finally {
            org.slf4j.MDC.clear();
        }
    }

    @Test
    void withoutTheHeaderThereIsNoExtraLatency() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 0, "all");

        assertThat(millis(() -> client.getPartido())).isLessThan(300);
    }

    @Test
    void theCallTimeoutAlsoBoundsTheHeaderLatency() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 0, "all",
                300, false, 10, 5, 50, 1000, 10);
        org.slf4j.MDC.put("outboundFaultLatencyMs", "5000");
        org.slf4j.MDC.put("outboundFaultTarget", "all");
        try {
            assertThat(millis(() -> client.getPartido())).isBetween(250L, 1500L);
        } finally {
            org.slf4j.MDC.clear();
        }
    }

    // ---- timeout por dependencia

    @Test
    void callTimeoutBoundsASlowDependencyAndDegradesToNull() {
        CompanionClient client = new CompanionClient(DEAD, DEAD, 5000, 10000, 3000, "all",
                300, false, 10, 5, 50, 1000, 10);

        long elapsed = millis(() -> assertThat(client.getPartido()).isNull());

        assertThat(elapsed).isBetween(250L, 1500L);
    }

    // ---- circuit breaker

    @Test
    void withoutTheCircuitBreakerEveryCallReachesTheFailingCompanion() throws IOException {
        failing.set(true);
        String url = companion();
        CompanionClient client = new CompanionClient(url, url, 5000, 10000, 0, "all");

        for (int i = 0; i < 7; i++) {
            client.getPartido();
        }

        assertThat(hits.get()).isEqualTo(7);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheCompanion() throws IOException {
        failing.set(true);
        String url = companion();
        CompanionClient client = withBreaker(url, 4, 4, 5000, 30);

        for (int i = 0; i < 4; i++) {
            client.getPartido();
        }
        int hitsWhenOpened = hits.get();
        long elapsed = millis(() -> {
            for (int i = 0; i < 5; i++) {
                assertThat(client.getPartido()).isNull();
            }
        });

        assertThat(hitsWhenOpened).isEqualTo(4);
        assertThat(hits.get()).as("con el circuito abierto ya no se llama al compañero").isEqualTo(4);
        assertThat(elapsed).isLessThan(500);
    }

    @Test
    void circuitTreatsSlowCallsAsFailures() throws IOException {
        serverDelayMs = 300;
        String url = companion();
        CompanionClient client = withBreaker(url, 4, 4, 100, 30);

        for (int i = 0; i < 4; i++) {
            client.getPartido();
        }
        client.getPartido();

        assertThat(hits.get()).as("tras 4 llamadas lentas el circuito se abre").isEqualTo(4);
    }

    @Test
    void circuitClosesAgainWhenTheCompanionRecovers() throws Exception {
        failing.set(true);
        String url = companion();
        CompanionClient client = withBreaker(url, 4, 4, 5000, 1);

        for (int i = 0; i < 4; i++) {
            client.getPartido();
        }
        assertThat(hits.get()).isEqualTo(4);

        failing.set(false);
        Thread.sleep(1300);
        client.getPartido();
        client.getPartido();
        client.getPartido();

        assertThat(hits.get()).as("pasado el reposo se prueba de nuevo y, si responde, vuelve a llamar").isGreaterThanOrEqualTo(7);
    }

    @Test
    void circuitsAreIndependentPerCompanion() throws IOException {
        failing.set(true);
        String failingUrl = companion();
        CompanionClient client = new CompanionClient(failingUrl, DEAD, 5000, 10000, 0, "all",
                0, true, 4, 4, 50, 5000, 30);

        for (int i = 0; i < 4; i++) {
            client.getPartido();
        }
        int hitsToMari = hits.get();
        long toPipe = millis(() -> assertThat(client.getCommerce()).isNull());

        assertThat(hitsToMari).isEqualTo(4);
        assertThat(toPipe).isLessThan(1500);
    }
}
