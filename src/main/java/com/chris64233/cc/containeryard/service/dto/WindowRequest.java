package com.chris64233.cc.containeryard.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record WindowRequest(@NotNull(message = "时间窗开始时间不能为空") Instant startAt,
                            @NotNull(message = "时间窗结束时间不能为空") Instant endAt,
                            @Min(value = 1, message = "时间窗容量至少为 1") int capacity) {
}
