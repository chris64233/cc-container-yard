package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.GateWindow;

import java.time.Instant;

public record WindowView(Long id, Instant startAt, Instant endAt, int capacity,
                         int reserved, int served, int available, long version) {

    public static WindowView from(GateWindow w) {
        return new WindowView(w.getId(), w.getStartAt(), w.getEndAt(), w.getCapacity(),
                w.getReserved(), w.getServed(), w.getCapacity() - w.getReserved(), w.getVersion());
    }
}
