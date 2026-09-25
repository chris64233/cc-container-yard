package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "yard_stack")
public class YardStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false)
    private int maxTiers;

    @Column(nullable = false)
    private long maxTotalWeight;

    @Column(nullable = false)
    private long currentTotalWeight;

    @Version
    private long version;

    protected YardStack() {
    }

    public YardStack(String code, int maxTiers, long maxTotalWeight) {
        this.code = code;
        this.maxTiers = maxTiers;
        this.maxTotalWeight = maxTotalWeight;
        this.currentTotalWeight = 0;
    }

    public boolean canAccept(long weight) {
        return currentTotalWeight + weight <= maxTotalWeight;
    }

    public void accept(long weight) {
        if (!canAccept(weight)) {
            throw new IllegalStateException("堆栈 " + code + " 超过最大总重量");
        }
        currentTotalWeight += weight;
    }

    public void release(long weight) {
        currentTotalWeight -= weight;
        if (currentTotalWeight < 0) {
            throw new IllegalStateException("堆栈 " + code + " 总重量不能为负");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getMaxTiers() {
        return maxTiers;
    }

    public long getMaxTotalWeight() {
        return maxTotalWeight;
    }

    public long getCurrentTotalWeight() {
        return currentTotalWeight;
    }

    public long getVersion() {
        return version;
    }
}
