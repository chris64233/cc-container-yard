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
    private long maxWeight;

    @Column(nullable = false)
    private int currentTiers;

    @Column(nullable = false)
    private long currentWeight;

    @Version
    private long version;

    protected YardStack() {
    }

    public YardStack(String code, int maxTiers, long maxWeight) {
        this.code = code;
        this.maxTiers = maxTiers;
        this.maxWeight = maxWeight;
    }

    public boolean canAccept(long containerWeight) {
        return currentTiers < maxTiers && currentWeight + containerWeight <= maxWeight;
    }

    public void addTop(long containerWeight) {
        if (!canAccept(containerWeight)) {
            throw new IllegalStateException("堆栈 " + code + " 容量不足");
        }
        currentTiers++;
        currentWeight += containerWeight;
    }

    public void removeTop(long containerWeight) {
        currentTiers--;
        currentWeight -= containerWeight;
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

    public long getMaxWeight() {
        return maxWeight;
    }

    public int getCurrentTiers() {
        return currentTiers;
    }

    public long getCurrentWeight() {
        return currentWeight;
    }

    public long getVersion() {
        return version;
    }
}
