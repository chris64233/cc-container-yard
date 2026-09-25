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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "move_plan_stack_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "uk_snapshot_plan_stack", columnNames = {"plan_id", "stack_code"}))
public class MovePlanStackSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private MovePlan plan;

    @Column(name = "stack_code", nullable = false, length = 32)
    private String stackCode;

    @Column(nullable = false)
    private long stackVersion;

    protected MovePlanStackSnapshot() {
    }

    public MovePlanStackSnapshot(MovePlan plan, String stackCode, long stackVersion) {
        this.plan = plan;
        this.stackCode = stackCode;
        this.stackVersion = stackVersion;
    }

    public String getStackCode() {
        return stackCode;
    }

    public long getStackVersion() {
        return stackVersion;
    }
}
