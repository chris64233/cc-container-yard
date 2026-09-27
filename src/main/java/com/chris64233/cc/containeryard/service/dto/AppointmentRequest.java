package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.GateDirection;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppointmentRequest(
        @NotBlank(message = "预约业务号不能为空") String appointmentNo,
        @NotBlank(message = "箱号不能为空") String containerNo,
        @NotNull(message = "进出方向不能为空") GateDirection direction,
        @NotBlank(message = "车牌号不能为空") String vehicleNo,
        @NotNull(message = "闸口时间窗不能为空") Long windowId,
        @NotBlank(message = "目标堆栈不能为空") String targetStack,
        @Min(value = 1, message = "箱重必须大于 0") Long weight) {
}
