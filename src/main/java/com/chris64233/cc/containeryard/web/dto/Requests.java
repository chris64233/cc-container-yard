package com.chris64233.cc.containeryard.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class Requests {

    private Requests() {
    }

    public record CreateStackRequest(@NotBlank String code,
                                     @Min(1) int maxTiers,
                                     @Min(1) long maxTotalWeight) {
    }

    public record RegisterContainerRequest(@NotBlank String containerNo,
                                           @Min(1) long weight,
                                           @NotBlank String stackCode) {
    }

    public record PlanStepRequest(@NotBlank String containerNo,
                                  @NotBlank String targetStackCode) {
    }

    public record CreatePlanRequest(@NotEmpty List<@Valid PlanStepRequest> steps) {
    }
}
