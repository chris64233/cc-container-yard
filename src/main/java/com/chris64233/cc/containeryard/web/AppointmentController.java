package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.service.AppointmentService;
import com.chris64233.cc.containeryard.service.dto.AppointmentRequest;
import com.chris64233.cc.containeryard.service.dto.AppointmentView;
import com.chris64233.cc.containeryard.service.dto.ArrivalRequest;
import com.chris64233.cc.containeryard.service.dto.ArrivalResult;
import com.chris64233.cc.containeryard.service.dto.CancelRequest;
import com.chris64233.cc.containeryard.service.dto.PlanView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentView create(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.create(request);
    }

    @PostMapping("/appointments/{appointmentNo}/approve")
    public AppointmentView approve(@PathVariable String appointmentNo) {
        return appointmentService.approve(appointmentNo);
    }

    @PostMapping("/appointments/{appointmentNo}/arrivals")
    public ResponseEntity<ArrivalResult> arrive(@PathVariable String appointmentNo,
                                                @Valid @RequestBody ArrivalRequest request) {
        ArrivalRequest withPathNo = new ArrivalRequest(
                appointmentNo, request.arrivalNo(), request.expectedVersion(), request.at());
        ArrivalResult result = appointmentService.arrive(withPathNo);
        HttpStatus status = ArrivalResult.OUTCOME_NEEDS_MANUAL.equals(result.outcome())
                ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/appointments/{appointmentNo}/cancel")
    public AppointmentView cancel(@PathVariable String appointmentNo,
                                  @Valid @RequestBody CancelRequest request) {
        CancelRequest withPathNo = new CancelRequest(appointmentNo, request.cancelNo());
        return appointmentService.cancel(withPathNo);
    }

    @GetMapping("/appointments")
    public List<AppointmentView> list() {
        return appointmentService.list();
    }

    @GetMapping("/appointments/{appointmentNo}")
    public AppointmentView detail(@PathVariable String appointmentNo) {
        return appointmentService.get(appointmentNo);
    }

    @GetMapping("/appointments/{appointmentNo}/plan")
    public PlanView prerequisitePlan(@PathVariable String appointmentNo) {
        return appointmentService.getPlan(appointmentNo);
    }

    @GetMapping("/occupancy/windows/{windowId}")
    public AppointmentView.OccupancyView windowOccupancy(@PathVariable Long windowId) {
        return appointmentService.windowOccupancy(windowId);
    }

    @GetMapping("/occupancy/stacks/{stackCode}")
    public AppointmentView.OccupancyView stackOccupancy(@PathVariable String stackCode) {
        return appointmentService.stackOccupancy(stackCode);
    }
}
