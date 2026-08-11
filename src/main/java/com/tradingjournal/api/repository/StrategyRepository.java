package com.tradingjournal.api.repository;

import com.tradingjournal.api.model.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {
}
