package com.chris64233.cc.containeryard.service.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(@NotBlank(message = "取消业务号不能为空") String cancelNo) {
}
