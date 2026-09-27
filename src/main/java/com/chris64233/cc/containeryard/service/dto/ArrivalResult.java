package com.chris64233.cc.containeryard.service.dto;

public record ArrivalResult(AppointmentView appointment, ArrivalOutcome outcome, String message) {
}
