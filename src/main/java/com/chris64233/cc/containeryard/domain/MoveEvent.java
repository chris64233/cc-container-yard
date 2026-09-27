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

    @Column(updatable = false)
    private Long planId;

    /** 业务来源号：手工/前置移箱为预约号，闸口落箱提箱为到场业务号。 */
    @Column(name = "ref_no", updatable = false, length = 64)
    private String refNo;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Column(nullable = false, updatable = false, length = 32)
    private String containerNo;

    @Column(name = "from_stack_code", updatable = false, length = 32)
    private String fromStackCode;

    @Column(name = "to_stack_code", updatable = false, length = 32)
    private String toStackCode;

    @Column(nullable = false, updatable = false)
    private int fromTier;

    @Column(nullable = false, updatable = false)
    private int toTier;

    @Column(nullable = false, updatable = false)
    private long containerWeight;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    protected MoveEvent() {
    }

    public MoveEvent(Long planId, int seq, String containerNo,
                     String fromStackCode, String toStackCode,
                     int fromTier, int toTier, long containerWeight) {
        this(planId, null, seq, containerNo, fromStackCode, toStackCode,
                fromTier, toTier, containerWeight);
    }

    public MoveEvent(Long planId, String refNo, int seq, String containerNo,
                     String fromStackCode, String toStackCode,
                     int fromTier, int toTier, long containerWeight) {
        this.planId = planId;
        this.refNo = refNo;
        this.seq = seq;
        this.containerNo = containerNo;
        this.fromStackCode = fromStackCode;
        this.toStackCode = toStackCode;
        this.fromTier = fromTier;
        this.toTier = toTier;
        this.containerWeight = containerWeight;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getRefNo() {
        return refNo;
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

    public long getContainerWeight() {
        return containerWeight;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
