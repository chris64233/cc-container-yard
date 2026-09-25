package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.service.PlanService;
import com.chris64233.cc.containeryard.service.YardSimulator.StepCommand;
import com.chris64233.cc.containeryard.web.dto.Requests.CreatePlanRequest;
import com.chris64233.cc.containeryard.web.dto.Views.ExecuteResultView;
import com.chris64233.cc.containeryard.web.dto.Views.MoveEventView;
import com.chris64233.cc.containeryard.web.dto.Views.PlanView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanView submit(@Valid @RequestBody CreatePlanRequest request) {
        List<StepCommand> commands = request.steps().stream()
                .map(s -> new StepCommand(s.containerNo(), s.targetStackCode()))
                .toList();
        return planService.submit(commands);
    }

    @GetMapping("/{planId}")
    public PlanView getPlan(@PathVariable Long planId) {
        return planService.getPlan(planId);
    }

    @PostMapping("/{planId}/execute")
    public ExecuteResultView execute(@PathVariable Long planId) {
        return planService.execute(planId);
    }

    @GetMapping("/{planId}/events")
    public List<MoveEventView> events(@PathVariable Long planId) {
        return planService.getEvents(planId);
    }
}
