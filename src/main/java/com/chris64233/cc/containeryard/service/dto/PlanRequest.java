package com.chris64233.cc.containeryard.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlanRequest(@NotEmpty(message = "计划至少包含一个步骤") List<@Valid StepRequest> steps) {
}
