package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.service.dto.SimulationResult;

public class PlanValidationException extends RuntimeException {

    private final SimulationResult simulation;

    public PlanValidationException(SimulationResult simulation) {
        super("计划校验失败");
        this.simulation = simulation;
    }

    public SimulationResult getSimulation() {
        return simulation;
    }
}
