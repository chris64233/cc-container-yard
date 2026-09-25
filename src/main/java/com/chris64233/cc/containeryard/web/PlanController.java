package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.domain.PlanStatus;
import com.chris64233.cc.containeryard.service.PlanService;
import com.chris64233.cc.containeryard.service.dto.EventView;
import com.chris64233.cc.containeryard.service.dto.ExecuteResult;
import com.chris64233.cc.containeryard.service.dto.PlanRequest;
import com.chris64233.cc.containeryard.service.dto.PlanView;
import com.chris64233.cc.containeryard.service.dto.SimulationResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/plans/simulate")
    public SimulationResult simulate(@Valid @RequestBody PlanRequest request) {
        return planService.simulate(request);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanView createPlan(@Valid @RequestBody PlanRequest request) {
        return planService.createPlan(request);
    }

    @GetMapping("/plans/{planId}")
    public PlanView getPlan(@PathVariable Long planId) {
        return planService.getPlan(planId);
    }

    @PostMapping("/plans/{planId}/execute")
    public ResponseEntity<ExecuteResult> execute(@PathVariable Long planId) {
        ExecuteResult result = planService.execute(planId);
        HttpStatus status = result.status() == PlanStatus.STALE_REJECTED
                ? HttpStatus.CONFLICT : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/events")
    public List<EventView> events(@RequestParam(required = false) Long planId) {
        return planService.eventsOf(planId);
    }
}
