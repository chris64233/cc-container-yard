package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "move_plan")
public class MovePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlanStatus status = PlanStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant executedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<MovePlanStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanStackSnapshot> snapshots = new ArrayList<>();

    protected MovePlan() {
    }

    public MovePlan(List<MovePlanStep> steps, List<PlanStackSnapshot> snapshots) {
        for (MovePlanStep step : steps) {
            step.attachTo(this);
            this.steps.add(step);
        }
        for (PlanStackSnapshot snapshot : snapshots) {
            snapshot.attachTo(this);
            this.snapshots.add(snapshot);
        }
    }

    public void markExecuted() {
        this.status = PlanStatus.EXECUTED;
        this.executedAt = Instant.now();
    }

    public void markStale() {
        this.status = PlanStatus.STALE;
    }

    public Long getId() {
        return id;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public List<MovePlanStep> getSteps() {
        return steps;
    }

    public List<PlanStackSnapshot> getSnapshots() {
        return snapshots;
    }
}
