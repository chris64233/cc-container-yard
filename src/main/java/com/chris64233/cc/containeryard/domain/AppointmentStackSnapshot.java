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
@Table(name = "appointment_stack_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "uk_appt_snapshot_stack", columnNames = {"appointment_id", "stack_code"}))
public class AppointmentStackSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "stack_code", nullable = false, length = 32)
    private String stackCode;

    @Column(nullable = false)
    private long stackVersion;

    protected AppointmentStackSnapshot() {
    }

    public AppointmentStackSnapshot(Appointment appointment, String stackCode, long stackVersion) {
        this.appointment = appointment;
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
