package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * 进出场预约。
 *
 * <p>状态流转：PENDING → APPROVED → ARRIVED；PENDING/APPROVED → CANCELLED。
 * 批准失败时整笔事务回滚，预约保持 PENDING 可稍后重试。
 * 只有 PENDING/APPROVED 算「有效预约」，此时
 * {@code activeContainerNo} 等于箱号并受唯一约束保护，保证一个箱同一时间
 * 只能存在一笔有效预约；预约终结（ARRIVED/CANCELLED/REJECTED）时置空。
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_no", nullable = false, unique = true, length = 64)
    private String appointmentNo;

    @Column(name = "container_no", nullable = false, length = 32)
    private String containerNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppointmentDirection direction;

    @Column(name = "vehicle_no", nullable = false, length = 32)
    private String vehicleNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "window_id", nullable = false)
    private GateWindow window;

    /** 进场预约的目标堆栈；出场预约为空。 */
    @Column(name = "target_stack_code", length = 32)
    private String targetStackCode;

    @Column(name = "container_weight", nullable = false)
    private long containerWeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    /** 出场预约当前生效的前置移箱计划。 */
    @Column(name = "plan_id")
    private Long planId;

    /** 有效预约时等于箱号，终结后置空；配合唯一约束实现一箱一有效预约。 */
    @Column(name = "active_container_no", unique = true, length = 32)
    private String activeContainerNo;

    @Column(name = "arrival_no", unique = true, length = 64)
    private String arrivalNo;

    @Column(name = "cancel_no", unique = true, length = 64)
    private String cancelNo;

    private Instant arrivedAt;

    @Column(length = 500)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    protected Appointment() {
    }

    public Appointment(String appointmentNo, String containerNo, AppointmentDirection direction,
                       String vehicleNo, GateWindow window, String targetStackCode, long containerWeight) {
        this.appointmentNo = appointmentNo;
        this.containerNo = containerNo;
        this.direction = direction;
        this.vehicleNo = vehicleNo;
        this.window = window;
        this.targetStackCode = targetStackCode;
        this.containerWeight = containerWeight;
        this.activeContainerNo = containerNo;
    }

    public boolean isActive() {
        return status == AppointmentStatus.PENDING || status == AppointmentStatus.APPROVED;
    }

    public void markApproved(Long planId, String message) {
        this.status = AppointmentStatus.APPROVED;
        this.planId = planId;
        this.message = message;
    }

    public void markArrived(String arrivalNo, String message) {
        this.status = AppointmentStatus.ARRIVED;
        this.arrivalNo = arrivalNo;
        this.arrivedAt = Instant.now();
        this.activeContainerNo = null;
        this.message = message;
    }

    public void markCancelled(String cancelNo, String message) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelNo = cancelNo;
        this.activeContainerNo = null;
        this.message = message;
    }

    public void replacePlan(Long planId, String message) {
        this.planId = planId;
        this.message = message;
    }

    public void note(String message) {
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public String getContainerNo() {
        return containerNo;
    }

    public AppointmentDirection getDirection() {
        return direction;
    }

    public String getVehicleNo() {
        return vehicleNo;
    }

    public GateWindow getWindow() {
        return window;
    }

    public String getTargetStackCode() {
        return targetStackCode;
    }

    public long getContainerWeight() {
        return containerWeight;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getActiveContainerNo() {
        return activeContainerNo;
    }

    public String getArrivalNo() {
        return arrivalNo;
    }

    public String getCancelNo() {
        return cancelNo;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
