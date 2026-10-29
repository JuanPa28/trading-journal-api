package com.tradingjournal.api.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyTest {

    @Test
    void countsTheMetConfluences() {
        Strategy strategy = new Strategy();
        assertThat(strategy.confluenceCount()).isZero();

        strategy.setHtfPdArray(true);
        strategy.setIfvg(true);
        strategy.setCorrectRisk(true);
        assertThat(strategy.confluenceCount()).isEqualTo(3);

        strategy.setCisd(true);
        strategy.setFollowedRules(true);
        strategy.setContinuation(true);
        strategy.setReversal(true);
        assertThat(strategy.confluenceCount()).isEqualTo(7);
    }
}
