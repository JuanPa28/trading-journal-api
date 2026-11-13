package com.tradingjournal.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionClientTest {

    // Puerto 9 (discard): la conexión se rechaza al instante, así que cualquier
    // espera medida viene solo de la latencia inyectada.
    private static final String DEAD = "http://127.0.0.1:9";

    private static long millis(Runnable call) {
        long start = System.nanoTime();
        call.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

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
}
