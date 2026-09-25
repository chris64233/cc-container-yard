package com.chris64233.cc.containeryard.service.dto;

import jakarta.validation.constraints.NotBlank;

public record StepRequest(
        @NotBlank(message = "箱号不能为空") String containerNo,
        @NotBlank(message = "目标堆栈不能为空") String targetStack) {
}
