package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.service.AppointmentService;
import com.chris64233.cc.containeryard.service.dto.AppointmentRequest;
import com.chris64233.cc.containeryard.service.dto.AppointmentResult;
import com.chris64233.cc.containeryard.service.dto.AppointmentView;
import com.chris64233.cc.containeryard.service.dto.ArrivalOutcome;
import com.chris64233.cc.containeryard.service.dto.ArrivalRequest;
import com.chris64233.cc.containeryard.service.dto.ArrivalResult;
import com.chris64233.cc.containeryard.service.dto.CancelRequest;
import com.chris64233.cc.containeryard.service.dto.OccupancyView;
import com.chris64233.cc.containeryard.service.dto.WindowRequest;
import com.chris64233.cc.containeryard.service.dto.WindowView;
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

    @PostMapping("/gate/windows")
    @ResponseStatus(HttpStatus.CREATED)
    public WindowView createWindow(@Valid @RequestBody WindowRequest request) {
        return appointmentService.createWindow(request);
    }

    @GetMapping("/gate/schedule")
    public List<WindowView> schedule() {
        return appointmentService.schedule();
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentView> book(@Valid @RequestBody AppointmentRequest request) {
        AppointmentResult result = appointmentService.book(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.view());
    }

    @GetMapping("/appointments/{appointmentNo}")
    public AppointmentView getAppointment(@PathVariable String appointmentNo) {
        return appointmentService.getAppointment(appointmentNo);
    }

    @GetMapping("/appointments/{appointmentNo}/pre-moves")
    public List<AppointmentView.PreMoveStepView> preMoveSteps(@PathVariable String appointmentNo) {
        return appointmentService.preMoveSteps(appointmentNo);
    }

    @PostMapping("/appointments/{appointmentNo}/arrival")
    public ResponseEntity<ArrivalResult> confirmArrival(@PathVariable String appointmentNo,
                                                        @Valid @RequestBody ArrivalRequest request) {
        ArrivalResult result = appointmentService.confirmArrival(appointmentNo, request);
        HttpStatus status = result.outcome() == ArrivalOutcome.MANUAL_REVIEW
                ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping("/appointments/{appointmentNo}/cancel")
    public AppointmentView cancel(@PathVariable String appointmentNo,
                                  @Valid @RequestBody CancelRequest request) {
        return appointmentService.cancel(appointmentNo, request);
    }

    @GetMapping("/yard/occupancy")
    public List<OccupancyView> occupancy() {
        return appointmentService.occupancy();
    }
}
