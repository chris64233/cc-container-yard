package com.chris64233.cc.containeryard.service.dto;

import java.util.List;

public record StackView(String code, int maxTiers, long maxWeight,
                        int currentTiers, long currentWeight, long version,
                        List<ContainerView> containers) {

    public record ContainerView(String containerNo, long weight, int tier) {
    }
}
