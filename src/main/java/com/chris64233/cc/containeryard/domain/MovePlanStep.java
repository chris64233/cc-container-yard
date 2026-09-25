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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private MovePlan plan;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 32)
    private String containerNo;

    @Column(nullable = false, length = 32)
    private String targetStackCode;

    protected MovePlanStep() {
    }

    public MovePlanStep(MovePlan plan, int seq, String containerNo, String targetStackCode) {
        this.plan = plan;
        this.seq = seq;
        this.containerNo = containerNo;
        this.targetStackCode = targetStackCode;
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

    public String getTargetStackCode() {
        return targetStackCode;
    }
}
