package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "move_event")
public class MoveEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long planId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Column(nullable = false, updatable = false, length = 32)
    private String containerNo;

    @Column(nullable = false, updatable = false, length = 32)
    private String fromStackCode;

    @Column(nullable = false, updatable = false, length = 32)
    private String toStackCode;

    @Column(nullable = false, updatable = false)
    private int fromTier;

    @Column(nullable = false, updatable = false)
    private int toTier;

    @Column(nullable = false, updatable = false)
    private Instant executedAt = Instant.now();

    protected MoveEvent() {
    }

    public MoveEvent(Long planId, int seq, String containerNo,
                     String fromStackCode, String toStackCode, int fromTier, int toTier) {
        this.planId = planId;
        this.seq = seq;
        this.containerNo = containerNo;
        this.fromStackCode = fromStackCode;
        this.toStackCode = toStackCode;
        this.fromTier = fromTier;
        this.toTier = toTier;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public int getSeq() {
        return seq;
    }

    public String getContainerNo() {
        return containerNo;
    }

    public String getFromStackCode() {
        return fromStackCode;
    }

    public String getToStackCode() {
        return toStackCode;
    }

    public int getFromTier() {
        return fromTier;
    }

    public int getToTier() {
        return toTier;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
