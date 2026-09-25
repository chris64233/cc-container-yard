package com.chris64233.cc.containeryard.service.dto;

public record StepSimulation(int seq, String containerNo, String fromStack, String toStack,
                             boolean ok, String message) {
}
