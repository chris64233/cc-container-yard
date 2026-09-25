package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.MovePlan;
import com.chris64233.cc.containeryard.domain.PlanStatus;

import java.time.Instant;
import java.util.List;

public record PlanView(Long id, PlanStatus status, Instant createdAt, Instant executedAt,
                       String resultMessage, List<StepView> steps, List<SnapshotView> snapshots) {

    public record StepView(int seq, String containerNo, String targetStack) {
    }

    public record SnapshotView(String stackCode, long version) {
    }

    public static PlanView from(MovePlan plan) {
        return new PlanView(
                plan.getId(),
                plan.getStatus(),
                plan.getCreatedAt(),
                plan.getExecutedAt(),
                plan.getResultMessage(),
                plan.getSteps().stream()
                        .map(s -> new StepView(s.getSeq(), s.getContainerNo(), s.getTargetStackCode()))
                        .toList(),
                plan.getSnapshots().stream()
                        .map(s -> new SnapshotView(s.getStackCode(), s.getStackVersion()))
                        .toList());
    }
}
