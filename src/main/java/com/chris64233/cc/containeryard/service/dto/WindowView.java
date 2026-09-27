package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.GateWindow;

import java.time.Instant;

public record WindowView(Long id, Instant startAt, Instant endAt,
                         int capacity, int bookedCount, int remaining) {

    public static WindowView from(GateWindow window) {
        return new WindowView(window.getId(), window.getStartAt(), window.getEndAt(),
                window.getCapacity(), window.getBookedCount(),
                window.getCapacity() - window.getBookedCount());
    }
}
