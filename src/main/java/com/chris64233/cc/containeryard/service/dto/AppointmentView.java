package com.chris64233.cc.containeryard.service.dto;

import com.chris64233.cc.containeryard.domain.Appointment;
import com.chris64233.cc.containeryard.domain.AppointmentStatus;
import com.chris64233.cc.containeryard.domain.GateDirection;
import com.chris64233.cc.containeryard.domain.SlotReservation;

import java.time.Instant;
import java.util.List;

public record AppointmentView(Long id, String appointmentNo, String containerNo, GateDirection direction,
                              String vehicleNo, Long windowId, Instant windowStartAt, Instant windowEndAt,
                              String targetStack, Long containerWeight, AppointmentStatus status,
                              int planVersion, boolean manualReview, SlotView slot,
                              List<PreMoveStepView> preMoveSteps, List<SnapshotView> layoutSnapshots,
                              String arrivalNo, String cancelNo, String resultMessage,
                              Instant createdAt, Instant arrivedAt, Instant cancelledAt) {

    public record SlotView(String stackCode, int tier, long weight, boolean active) {
    }

    public record PreMoveStepView(int seq, String containerNo, String fromStack, String toStack) {
    }

    public record SnapshotView(String stackCode, long version) {
    }

    public static AppointmentView from(Appointment appointment, SlotReservation slot) {
        SlotView slotView = slot == null ? null
                : new SlotView(slot.getStackCode(), slot.getTier(), slot.getWeight(), slot.isActive());
        return new AppointmentView(
                appointment.getId(),
                appointment.getAppointmentNo(),
                appointment.getContainerNo(),
                appointment.getDirection(),
                appointment.getVehicleNo(),
                appointment.getWindow().getId(),
                appointment.getWindow().getStartAt(),
                appointment.getWindow().getEndAt(),
                appointment.getTargetStackCode(),
                appointment.getContainerWeight(),
                appointment.getStatus(),
                appointment.getPlanVersion(),
                appointment.isManualReview(),
                slotView,
                appointment.getPreMoveSteps().stream()
                        .map(s -> new PreMoveStepView(s.getSeq(), s.getContainerNo(),
                                s.getFromStackCode(), s.getToStackCode()))
                        .toList(),
                appointment.getSnapshots().stream()
                        .map(s -> new SnapshotView(s.getStackCode(), s.getStackVersion()))
                        .toList(),
                appointment.getArrivalNo(),
                appointment.getCancelNo(),
                appointment.getResultMessage(),
                appointment.getCreatedAt(),
                appointment.getArrivedAt(),
                appointment.getCancelledAt());
    }
}
