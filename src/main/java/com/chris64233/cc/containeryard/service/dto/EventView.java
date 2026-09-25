package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.MoveEvent;

import java.time.Instant;

public record EventView(Long id, Long planId, int seq, String containerNo,
                        String fromStack, String toStack, int fromTier, int toTier,
                        long containerWeight, Instant occurredAt) {

    public static EventView from(MoveEvent e) {
        return new EventView(e.getId(), e.getPlanId(), e.getSeq(), e.getContainerNo(),
                e.getFromStackCode(), e.getToStackCode(), e.getFromTier(), e.getToTier(),
                e.getContainerWeight(), e.getOccurredAt());
    }
}
