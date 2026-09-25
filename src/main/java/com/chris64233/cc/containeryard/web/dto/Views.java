package com.chris64233.cc.containeryard.web.dto;

import java.time.Instant;
import java.util.List;

public final class Views {

    private Views() {
    }

    public record ContainerView(String containerNo, long weight, String stackCode, int tier) {
    }

    public record StackView(String code, int maxTiers, long maxTotalWeight,
                            long currentTotalWeight, long version,
                            List<ContainerView> containers) {
    }

    public record PlanStepView(int seq, String containerNo, String fromStackCode,
                               String toStackCode, int toTier) {
    }

    public record SnapshotView(String stackCode, long version) {
    }

    public record PlanView(Long id, String status, Instant createdAt, Instant executedAt,
                           List<PlanStepView> steps, List<SnapshotView> snapshots) {
    }

    public record MoveEventView(Long id, int seq, String containerNo,
                                String fromStackCode, String toStackCode,
                                int fromTier, int toTier, Instant executedAt) {
    }

    public record ExecuteResultView(Long planId, String status, boolean idempotentReplay,
                                    List<MoveEventView> events) {
    }
}
