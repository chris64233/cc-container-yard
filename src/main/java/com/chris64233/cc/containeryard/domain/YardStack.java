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

    /** 已被进场预约预留、尚未实际落位的层数。 */
    @Column(nullable = false)
    private int reservedTiers;

    /** 已被进场预约预留、尚未实际落位的重量。 */
    @Column(nullable = false)
    private long reservedWeight;

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

    /** 是否还能再预留一个进场堆位（同时满足高度与重量限制）。 */
    public boolean canReserve(long containerWeight) {
        return currentTiers + reservedTiers < maxTiers
                && currentWeight + reservedWeight + containerWeight <= maxWeight;
    }

    public void reserve(long containerWeight) {
        if (!canReserve(containerWeight)) {
            throw new IllegalStateException("堆栈 " + code + " 可预留高度或重量不足");
        }
        reservedTiers++;
        reservedWeight += containerWeight;
    }

    public void releaseReservation(long containerWeight) {
        if (reservedTiers <= 0 || reservedWeight < containerWeight) {
            throw new IllegalStateException("堆栈 " + code + " 没有可释放的堆位预留");
        }
        reservedTiers--;
        reservedWeight -= containerWeight;
    }

    /** 进场预约实际落位：预留转为实际占用。 */
    public void consumeReserved(long containerWeight) {
        if (reservedTiers <= 0 || reservedWeight < containerWeight) {
            throw new IllegalStateException("堆栈 " + code + " 没有对应的堆位预留");
        }
        reservedTiers--;
        reservedWeight -= containerWeight;
        addTop(containerWeight);
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

    public int getReservedTiers() {
        return reservedTiers;
    }

    public long getReservedWeight() {
        return reservedWeight;
    }

    public long getVersion() {
        return version;
    }
}
