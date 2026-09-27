package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.AppointmentDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AppointmentRequest(
        @NotBlank(message = "预约业务号不能为空") String appointmentNo,
        @NotBlank(message = "箱号不能为空") String containerNo,
        @NotNull(message = "方向不能为空") AppointmentDirection direction,
        @NotBlank(message = "车牌号不能为空") String vehicleNo,
        @NotNull(message = "时间窗不能为空") Long windowId,
        String targetStack,
        long weight) {
}
