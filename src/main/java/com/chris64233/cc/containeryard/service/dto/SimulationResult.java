package com.chris64233.cc.containeryard.service.dto;

import java.util.List;

public record SimulationResult(boolean valid, List<StepSimulation> steps) {
}
