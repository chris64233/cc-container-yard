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
@Table(name = "pre_move_step")
public class PreMoveStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, length = 32)
    private String containerNo;

    @Column(nullable = false, length = 32)
    private String fromStackCode;

    @Column(nullable = false, length = 32)
    private String toStackCode;

    protected PreMoveStep() {
    }

    public PreMoveStep(Appointment appointment, int seq, String containerNo,
                       String fromStackCode, String toStackCode) {
        this.appointment = appointment;
        this.seq = seq;
        this.containerNo = containerNo;
        this.fromStackCode = fromStackCode;
        this.toStackCode = toStackCode;
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
}
