package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.PlanStatus;

import java.util.List;

public record ExecuteResult(Long planId, PlanStatus status, String message, List<EventView> events) {
}
