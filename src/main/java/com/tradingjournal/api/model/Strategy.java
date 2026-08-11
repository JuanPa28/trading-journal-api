package com.tradingjournal.api.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "strategies")
public class Strategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "strategy")
    private List<Trade> trades = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Trade> getTrades() {
        return trades;
    }
}
