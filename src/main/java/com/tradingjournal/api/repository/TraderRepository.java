package com.tradingjournal.api.repository;

import com.tradingjournal.api.model.Trader;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraderRepository extends JpaRepository<Trader, Long> {
}
