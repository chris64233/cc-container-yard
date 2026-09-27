package com.chris64233.cc.containeryard.service.dto;

import java.util.List;

public record OccupancyView(String stackCode, int maxTiers, long maxWeight,
                            int currentTiers, long currentWeight,
                            int reservedTiers, long reservedWeight,
                            int availableTiers, long availableWeight,
                            List<ReservedSlotView> reservedSlots) {

    public record ReservedSlotView(int tier, String containerNo, long weight, String appointmentNo) {
    }
}
