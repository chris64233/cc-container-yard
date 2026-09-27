package com.chris64233.cc.containeryard.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String appointmentNo;

    @Column(nullable = false, length = 32)
    private String containerNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private GateDirection direction;

    @Column(nullable = false, length = 32)
    private String vehicleNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "window_id", nullable = false)
    private GateWindow window;

    @Column(nullable = false, length = 32)
    private String targetStackCode;

    private Long containerWeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    @Column(nullable = false)
    private int planVersion;

    /**
     * 预约有效期间等于箱号，用于数据库唯一约束保证一箱一笔有效预约；
     * 预约到场或取消后置空，允许该箱再次预约。
     */
    @Column(length = 32, unique = true)
    private String activeContainerKey;

    @Column(length = 40)
    private String arrivalNo;

    @Column(length = 40)
    private String cancelNo;

    @Column(nullable = false)
    private boolean manualReview;

    @Column(length = 500)
    private String resultMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant arrivedAt;

    private Instant cancelledAt;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq")
    private List<PreMoveStep> preMoveSteps = new ArrayList<>();

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AppointmentStackSnapshot> snapshots = new ArrayList<>();

    protected Appointment() {
    }

    public Appointment(String appointmentNo, String containerNo, GateDirection direction,
                       String vehicleNo, GateWindow window, String targetStackCode, Long containerWeight) {
        this.appointmentNo = appointmentNo;
        this.containerNo = containerNo;
        this.direction = direction;
        this.vehicleNo = vehicleNo;
        this.window = window;
        this.targetStackCode = targetStackCode;
        this.containerWeight = containerWeight;
        this.activeContainerKey = containerNo;
    }

    public void clearPlan() {
        this.preMoveSteps.clear();
        this.snapshots.clear();
    }

    public void replacePlan(List<PreMoveStep> steps, List<AppointmentStackSnapshot> newSnapshots) {
        this.planVersion++;
        this.preMoveSteps.addAll(steps);
        this.snapshots.addAll(newSnapshots);
    }

    public void markArrived(String arrivalNo) {
        this.status = AppointmentStatus.ARRIVED;
        this.arrivalNo = arrivalNo;
        this.arrivedAt = Instant.now();
        this.activeContainerKey = null;
        this.manualReview = false;
    }

    public void markCancelled(String cancelNo) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelNo = cancelNo;
        this.cancelledAt = Instant.now();
        this.activeContainerKey = null;
    }

    public void markManualReview(String message) {
        this.manualReview = true;
        this.resultMessage = message;
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

    public GateDirection getDirection() {
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

    public Long getContainerWeight() {
        return containerWeight;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public int getPlanVersion() {
        return planVersion;
    }

    public String getArrivalNo() {
        return arrivalNo;
    }

    public String getCancelNo() {
        return cancelNo;
    }

    public boolean isManualReview() {
        return manualReview;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<PreMoveStep> getPreMoveSteps() {
        return preMoveSteps;
    }

    public List<AppointmentStackSnapshot> getSnapshots() {
        return snapshots;
    }
}
