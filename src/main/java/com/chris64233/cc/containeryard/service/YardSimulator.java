package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.Container;
import com.chris64233.cc.containeryard.domain.YardStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯内存的移箱模拟器：不修改任何 JPA 实体，只根据给定状态推演整份计划。
 * 提交计划与执行计划共用同一套校验规则。
 */
public final class YardSimulator {

    public record StepCommand(String containerNo, String targetStackCode) {
    }

    public record SimulatedMove(int seq, String containerNo,
                                String fromStackCode, String toStackCode,
                                int fromTier, int toTier) {
    }

    private record Box(String containerNo, long weight) {
    }

    private static final class StackState {
        private final int maxTiers;
        private final long maxTotalWeight;
        private final Deque<Box> boxes = new ArrayDeque<>();
        private long totalWeight;

        private StackState(int maxTiers, long maxTotalWeight) {
            this.maxTiers = maxTiers;
            this.maxTotalWeight = maxTotalWeight;
        }
    }

    private YardSimulator() {
    }

    public static List<SimulatedMove> simulate(List<StepCommand> steps,
                                               Collection<YardStack> stacks,
                                               Collection<Container> containers) {
        Map<String, StackState> states = new HashMap<>();
        for (YardStack stack : stacks) {
            states.put(stack.getCode(), new StackState(stack.getMaxTiers(), stack.getMaxTotalWeight()));
        }
        List<Container> ordered = containers.stream()
                .sorted(Comparator.comparing((Container c) -> c.getStack().getCode()).thenComparingInt(Container::getTier))
                .toList();
        for (Container container : ordered) {
            StackState state = states.get(container.getStack().getCode());
            if (state == null) {
                throw new PlanValidationException("集装箱 " + container.getContainerNo() + " 所在堆栈不存在");
            }
            state.boxes.addLast(new Box(container.getContainerNo(), container.getWeight()));
            state.totalWeight += container.getWeight();
        }

        List<SimulatedMove> moves = new ArrayList<>();
        int seq = 0;
        for (StepCommand step : steps) {
            seq++;
            Box box = findBox(states, step.containerNo(), seq);
            StackState source = findStackOf(states, step.containerNo(), seq);
            StackState target = states.get(step.targetStackCode());
            if (target == null) {
                throw new PlanValidationException("第 " + seq + " 步：目标堆栈 " + step.targetStackCode() + " 不存在");
            }
            if (source == target) {
                throw new PlanValidationException("第 " + seq + " 步：目标堆栈与源堆栈相同");
            }
            Box top = source.boxes.peekLast();
            if (top == null || !top.containerNo().equals(step.containerNo())) {
                throw new PlanValidationException("第 " + seq + " 步：集装箱 " + step.containerNo() + " 不在栈顶，无法移动");
            }
            if (target.boxes.size() >= target.maxTiers) {
                throw new PlanValidationException("第 " + seq + " 步：目标堆栈 " + step.targetStackCode() + " 已达最大层数");
            }
            if (target.totalWeight + box.weight() > target.maxTotalWeight) {
                throw new PlanValidationException("第 " + seq + " 步：目标堆栈 " + step.targetStackCode() + " 超过最大总重量");
            }
            String fromStackCode = findStackCode(states, source);
            int fromTier = source.boxes.size();
            source.boxes.pollLast();
            source.totalWeight -= box.weight();
            target.boxes.addLast(box);
            target.totalWeight += box.weight();
            moves.add(new SimulatedMove(seq, step.containerNo(), fromStackCode,
                    step.targetStackCode(), fromTier, target.boxes.size()));
        }
        return moves;
    }

    private static Box findBox(Map<String, StackState> states, String containerNo, int seq) {
        for (StackState state : states.values()) {
            for (Box box : state.boxes) {
                if (box.containerNo().equals(containerNo)) {
                    return box;
                }
            }
        }
        throw new PlanValidationException("第 " + seq + " 步：集装箱 " + containerNo + " 不存在");
    }

    private static StackState findStackOf(Map<String, StackState> states, String containerNo, int seq) {
        for (StackState state : states.values()) {
            for (Box box : state.boxes) {
                if (box.containerNo().equals(containerNo)) {
                    return state;
                }
            }
        }
        throw new PlanValidationException("第 " + seq + " 步：集装箱 " + containerNo + " 不存在");
    }

    private static String findStackCode(Map<String, StackState> states, StackState target) {
        for (Map.Entry<String, StackState> entry : states.entrySet()) {
            if (entry.getValue() == target) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("堆栈状态缺失");
    }
}
