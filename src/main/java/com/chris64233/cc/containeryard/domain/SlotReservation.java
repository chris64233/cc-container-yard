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
@Table(name = "slot_reservation")
public class SlotReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false, length = 32)
    private String stackCode;

    @Column(nullable = false)
    private int tier;

    @Column(nullable = false)
    private long weight;

    /**
     * 预留生效期间为 堆栈编码#层号，数据库唯一约束防止同一堆位被重复预留；
     * 释放后置空，堆位可被再次预留。
     */
    @Column(length = 40, unique = true)
    private String activeSlotKey;

    protected SlotReservation() {
    }

    public SlotReservation(Appointment appointment, String stackCode, int tier, long weight) {
        this.appointment = appointment;
        this.stackCode = stackCode;
        this.tier = tier;
        this.weight = weight;
        this.activeSlotKey = stackCode + "#" + tier;
    }

    public void release() {
        this.activeSlotKey = null;
    }

    public boolean isActive() {
        return activeSlotKey != null;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public String getStackCode() {
        return stackCode;
    }

    public int getTier() {
        return tier;
    }

    public long getWeight() {
        return weight;
    }
}
