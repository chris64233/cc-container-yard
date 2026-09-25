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
@Table(name = "plan_stack_snapshot")
public class PlanStackSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MovePlan plan;

    @Column(nullable = false, length = 32)
    private String stackCode;

    @Column(nullable = false)
    private long stackVersion;

    protected PlanStackSnapshot() {
    }

    public PlanStackSnapshot(String stackCode, long stackVersion) {
        this.stackCode = stackCode;
        this.stackVersion = stackVersion;
    }

    void attachTo(MovePlan plan) {
        this.plan = plan;
    }

    public Long getId() {
        return id;
    }

    public String getStackCode() {
        return stackCode;
    }

    public long getStackVersion() {
        return stackVersion;
    }
}
