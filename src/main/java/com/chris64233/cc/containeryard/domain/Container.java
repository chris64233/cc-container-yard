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
@Table(name = "container",
        uniqueConstraints = @UniqueConstraint(name = "uk_container_stack_tier", columnNames = {"stack_id", "tier"}))
public class Container {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "container_no", nullable = false, unique = true, length = 32)
    private String containerNo;

    @Column(nullable = false)
    private long weight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stack_id", nullable = false)
    private YardStack stack;

    @Column(nullable = false)
    private int tier;

    protected Container() {
    }

    public Container(String containerNo, long weight) {
        this.containerNo = containerNo;
        this.weight = weight;
    }

    public void placeOn(YardStack stack, int tier) {
        this.stack = stack;
        this.tier = tier;
    }

    public Long getId() {
        return id;
    }

    public String getContainerNo() {
        return containerNo;
    }

    public long getWeight() {
        return weight;
    }

    public YardStack getStack() {
        return stack;
    }

    public int getTier() {
        return tier;
    }
}
