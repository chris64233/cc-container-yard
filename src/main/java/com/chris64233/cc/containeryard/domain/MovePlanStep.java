package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "move_plan_step")
public class MovePlanStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MovePlan plan;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 32)
    private String containerNo;

    @Column(nullable = false, length = 32)
    private String fromStackCode;

    @Column(nullable = false, length = 32)
    private String toStackCode;

    @Column(nullable = false)
    private int toTier;

    protected MovePlanStep() {
    }

    public MovePlanStep(int seq, String containerNo, String fromStackCode, String toStackCode, int toTier) {
        this.seq = seq;
        this.containerNo = containerNo;
        this.fromStackCode = fromStackCode;
        this.toStackCode = toStackCode;
        this.toTier = toTier;
    }

    void attachTo(MovePlan plan) {
        this.plan = plan;
    }

    public Long getId() {
        return id;
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

    public int getToTier() {
        return toTier;
    }
}
