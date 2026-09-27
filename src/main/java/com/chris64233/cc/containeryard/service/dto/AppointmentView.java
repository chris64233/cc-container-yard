package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.Appointment;
import com.chris64233.cc.containeryard.domain.AppointmentDirection;
import com.chris64233.cc.containeryard.domain.AppointmentStatus;

import java.time.Instant;
import java.util.List;

public record AppointmentView(
        Long id,
        String appointmentNo,
        String containerNo,
        AppointmentDirection direction,
        String vehicleNo,
        Long windowId,
        Instant windowStart,
        Instant windowEnd,
        String targetStack,
        long containerWeight,
        AppointmentStatus status,
        Long planId,
        PlanView plan,
        String arrivalNo,
        String cancelNo,
        Instant arrivedAt,
        String message,
        long version,
        Instant createdAt) {

    public static AppointmentView from(Appointment a, PlanView plan) {
        return new AppointmentView(
                a.getId(),
                a.getAppointmentNo(),
                a.getContainerNo(),
                a.getDirection(),
                a.getVehicleNo(),
                a.getWindow().getId(),
                a.getWindow().getStartAt(),
                a.getWindow().getEndAt(),
                a.getTargetStackCode(),
                a.getContainerWeight(),
                a.getStatus(),
                a.getPlanId(),
                plan,
                a.getArrivalNo(),
                a.getCancelNo(),
                a.getArrivedAt(),
                a.getMessage(),
                a.getVersion(),
                a.getCreatedAt());
    }

    /** 预约列表元素：不含计划详情。 */
    public static AppointmentView summary(Appointment a) {
        return from(a, null);
    }

    /** 资源占用视角的预约条目。 */
    public record OccupancyEntry(String appointmentNo, String containerNo,
                                 AppointmentDirection direction, String vehicleNo,
                                 AppointmentStatus status) {
        public static OccupancyEntry from(Appointment a) {
            return new OccupancyEntry(a.getAppointmentNo(), a.getContainerNo(),
                    a.getDirection(), a.getVehicleNo(), a.getStatus());
        }
    }

    /** 单个资源（闸口时段或堆栈）的占用查询结果。 */
    public record OccupancyView(String resourceType, String resourceCode, int capacity,
                                int reserved, int served, List<OccupancyEntry> appointments) {
    }
}
