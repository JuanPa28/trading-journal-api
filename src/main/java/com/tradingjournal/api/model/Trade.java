package com.tradingjournal.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trader_id", nullable = false)
    private Trader trader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "strategy_id")
    private Strategy strategy;

    @Column(name = "external_id")
    private String externalId;

    @NotBlank
    @Column(nullable = false)
    private String contract;

    @NotNull
    @Column(nullable = false)
    private Integer size;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @NotNull
    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @NotNull
    @Column(name = "entry_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal entryPrice;

    @Column(name = "exit_price", precision = 19, scale = 8)
    private BigDecimal exitPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal pnl;

    @Column(precision = 19, scale = 2)
    private BigDecimal commissions;

    @Column(precision = 19, scale = 2)
    private BigDecimal fees;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "htf_pd_array", nullable = false)
    private boolean htfPdArray;

    @Column(nullable = false)
    private boolean ifvg;

    @Column(nullable = false)
    private boolean cisd;

    @Column(name = "followed_rules", nullable = false)
    private boolean followedRules;

    @Column(nullable = false)
    private boolean continuation;

    @Column(nullable = false)
    private boolean reversal;

    @Column(name = "correct_risk", nullable = false)
    private boolean correctRisk;

    @Transient
    public boolean isOpen() {
        return exitTime == null;
    }

    @Transient
    public Duration getDuration() {
        return exitTime != null ? Duration.between(entryTime, exitTime) : null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Trader getTrader() {
        return trader;
    }

    public void setTrader(Trader trader) {
        this.trader = trader;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getContract() {
        return contract;
    }

    public void setContract(String contract) {
        this.contract = contract;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public LocalDateTime getExitTime() {
        return exitTime;
    }

    public void setExitTime(LocalDateTime exitTime) {
        this.exitTime = exitTime;
    }

    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    public BigDecimal getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public BigDecimal getCommissions() {
        return commissions;
    }

    public void setCommissions(BigDecimal commissions) {
        this.commissions = commissions;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public void setFees(BigDecimal fees) {
        this.fees = fees;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isHtfPdArray() {
        return htfPdArray;
    }

    public void setHtfPdArray(boolean htfPdArray) {
        this.htfPdArray = htfPdArray;
    }

    public boolean isIfvg() {
        return ifvg;
    }

    public void setIfvg(boolean ifvg) {
        this.ifvg = ifvg;
    }

    public boolean isCisd() {
        return cisd;
    }

    public void setCisd(boolean cisd) {
        this.cisd = cisd;
    }

    public boolean isFollowedRules() {
        return followedRules;
    }

    public void setFollowedRules(boolean followedRules) {
        this.followedRules = followedRules;
    }

    public boolean isContinuation() {
        return continuation;
    }

    public void setContinuation(boolean continuation) {
        this.continuation = continuation;
    }

    public boolean isReversal() {
        return reversal;
    }

    public void setReversal(boolean reversal) {
        this.reversal = reversal;
    }

    public boolean isCorrectRisk() {
        return correctRisk;
    }

    public void setCorrectRisk(boolean correctRisk) {
        this.correctRisk = correctRisk;
    }
}
