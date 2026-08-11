package com.tradingjournal.api.repository;

import com.tradingjournal.api.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByTrader_Id(Long traderId);

    List<Trade> findByPnlGreaterThan(BigDecimal value);

    List<Trade> findByPnlLessThanEqual(BigDecimal value);

    List<Trade> findByTrader_IdAndPnlGreaterThan(Long traderId, BigDecimal value);

    List<Trade> findByTrader_IdAndPnlLessThanEqual(Long traderId, BigDecimal value);
}
