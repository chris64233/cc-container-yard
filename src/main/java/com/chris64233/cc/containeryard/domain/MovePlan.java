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
    @Column(nullable = false, length = 20)
    private PlanStatus status = PlanStatus.PENDING;

    /** 计划用途：MANUAL 手工计划 / OUTBOUND_APPOINTMENT 出场预约前置移箱。 */
    @Column(length = 40)
    private String purpose;

    /** 关联业务号（如出场预约号）。 */
    @Column(length = 64)
    private String refNo;

    /** 出场预约中最终要提走的目标箱号。 */
    @Column(name = "target_container_no", length = 32)
    private String targetContainerNo;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant executedAt;

    @Column(length = 500)
    private String resultMessage;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<MovePlanStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovePlanStackSnapshot> snapshots = new ArrayList<>();

    public MovePlan() {
    }

    public void addStep(int seq, String containerNo, String targetStackCode) {
        steps.add(new MovePlanStep(this, seq, containerNo, targetStackCode));
    }

    public void addSnapshot(String stackCode, long stackVersion) {
        snapshots.add(new MovePlanStackSnapshot(this, stackCode, stackVersion));
    }

    public void markExecuted(String message) {
        this.status = PlanStatus.EXECUTED;
        this.executedAt = Instant.now();
        this.resultMessage = message;
    }

    public void markStaleRejected(String message) {
        this.status = PlanStatus.STALE_REJECTED;
        this.resultMessage = message;
    }

    public void markCancelled(String message) {
        this.status = PlanStatus.CANCELLED;
        this.resultMessage = message;
    }

    public void tagOutboundAppointment(String appointmentNo, String targetContainerNo) {
        this.purpose = "OUTBOUND_APPOINTMENT";
        this.refNo = appointmentNo;
        this.targetContainerNo = targetContainerNo;
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

    public String getResultMessage() {
        return resultMessage;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getRefNo() {
        return refNo;
    }

    public String getTargetContainerNo() {
        return targetContainerNo;
    }

    public List<MovePlanStep> getSteps() {
        return steps;
    }

    public List<MovePlanStackSnapshot> getSnapshots() {
        return snapshots;
    }
}
