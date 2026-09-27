package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 闸口时间窗：[startAt, endAt) 内最多可处理 {@code capacity} 辆车。
 * {@code reserved} 为已批准预约占用的名额，{@code served} 为已完成到场的名额；
 * 释放预约只回退 reserved，车辆到场则把名额从 reserved 结转为 served。
 */
@Entity
@Table(name = "gate_window",
        uniqueConstraints = @UniqueConstraint(name = "uk_gate_window_range", columnNames = {"start_at", "end_at"}))
public class GateWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int reserved;

    @Column(nullable = false)
    private int served;

    @Version
    private long version;

    protected GateWindow() {
    }

    public GateWindow(Instant startAt, Instant endAt, int capacity) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startAt) && instant.isBefore(endAt);
    }

    public boolean hasFreeCapacity() {
        return reserved < capacity;
    }

    public void reserve() {
        if (!hasFreeCapacity()) {
            throw new IllegalStateException("闸口时段容量不足");
        }
        reserved++;
    }

    public void releaseReserved() {
        if (reserved <= 0) {
            throw new IllegalStateException("闸口时段没有可释放的预约名额");
        }
        reserved--;
    }

    /** 车辆到场：预约名额结转为已服务名额，释放一个 reserved。 */
    public void markServed() {
        if (reserved <= 0) {
            throw new IllegalStateException("闸口时段没有对应的预约名额");
        }
        reserved--;
        served++;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getReserved() {
        return reserved;
    }

    public int getServed() {
        return served;
    }

    public long getVersion() {
        return version;
    }
}
