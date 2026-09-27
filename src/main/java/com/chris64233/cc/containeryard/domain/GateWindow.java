package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "gate_window")
public class GateWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int bookedCount;

    protected GateWindow() {
    }

    public GateWindow(Instant startAt, Instant endAt, int capacity) {
        if (!startAt.isBefore(endAt)) {
            throw new IllegalStateException("时间窗开始时间必须早于结束时间");
        }
        if (capacity < 1) {
            throw new IllegalStateException("时间窗容量至少为 1");
        }
        this.startAt = startAt;
        this.endAt = endAt;
        this.capacity = capacity;
    }

    public boolean hasCapacity() {
        return bookedCount < capacity;
    }

    public void reserve() {
        if (!hasCapacity()) {
            throw new IllegalStateException("闸口时间窗容量已满");
        }
        bookedCount++;
    }

    public void release() {
        if (bookedCount > 0) {
            bookedCount--;
        }
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(startAt) && !instant.isAfter(endAt);
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

    public int getBookedCount() {
        return bookedCount;
    }
}
