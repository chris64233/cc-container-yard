package com.chris64233.cc.containeryard.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ArrivalRequest(
        @NotBlank(message = "预约业务号不能为空") String appointmentNo,
        @NotBlank(message = "到场业务号不能为空") String arrivalNo,
        @NotNull(message = "预约版本不能为空") Long expectedVersion,
        Instant at) {
}
