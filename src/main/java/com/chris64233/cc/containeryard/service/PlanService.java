package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.Container;
import com.chris64233.cc.containeryard.domain.MoveEvent;
import com.chris64233.cc.containeryard.domain.MovePlan;
import com.chris64233.cc.containeryard.domain.MovePlanStackSnapshot;
import com.chris64233.cc.containeryard.domain.MovePlanStep;
import com.chris64233.cc.containeryard.domain.PlanStatus;
import com.chris64233.cc.containeryard.domain.YardStack;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.dto.EventView;
import com.chris64233.cc.containeryard.service.dto.ExecuteResult;
import com.chris64233.cc.containeryard.service.dto.PlanRequest;
import com.chris64233.cc.containeryard.service.dto.PlanView;
import com.chris64233.cc.containeryard.service.dto.SimulationResult;
import com.chris64233.cc.containeryard.service.dto.StepRequest;
import com.chris64233.cc.containeryard.service.dto.StepSimulation;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlanService {

    private final YardStackRepository stackRepository;
    private final ContainerRepository containerRepository;
    private final MovePlanRepository planRepository;
    private final MoveEventRepository eventRepository;
    private final EntityManager entityManager;

    public PlanService(YardStackRepository stackRepository,
                       ContainerRepository containerRepository,
                       MovePlanRepository planRepository,
                       MoveEventRepository eventRepository,
                       EntityManager entityManager) {
        this.stackRepository = stackRepository;
        this.containerRepository = containerRepository;
        this.planRepository = planRepository;
        this.eventRepository = eventRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public SimulationResult simulate(PlanRequest request) {
        Simulation sim = Simulation.load(stackRepository.findAll(), containerRepository.findAll());
        return sim.run(request.steps());
    }

    @Transactional
    public PlanView createPlan(PlanRequest request) {
        Simulation sim = Simulation.load(stackRepository.findAll(), containerRepository.findAll());
        SimulationResult result = sim.run(request.steps());
        if (!result.valid()) {
            throw new PlanValidationException(result);
        }
        Map<String, YardStack> stacksByCode = stackRepository.findAll().stream()
                .collect(Collectors.toMap(YardStack::getCode, Function.identity()));

        MovePlan plan = new MovePlan();
        int seq = 1;
        for (StepRequest step : request.steps()) {
            plan.addStep(seq++, step.containerNo(), step.targetStack());
        }
        for (String code : sim.involvedStacks()) {
            plan.addSnapshot(code, stacksByCode.get(code).getVersion());
        }
        return PlanView.from(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public PlanView getPlan(Long planId) {
        return PlanView.from(planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("计划不存在: " + planId)));
    }

    @Transactional
    public ExecuteResult execute(Long planId) {
        MovePlan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new NotFoundException("计划不存在: " + planId));

        if (plan.getStatus() == PlanStatus.EXECUTED) {
            return new ExecuteResult(plan.getId(), PlanStatus.EXECUTED,
                    plan.getResultMessage(), eventsOf(planId));
        }
        if (plan.getStatus() == PlanStatus.STALE_REJECTED) {
            return new ExecuteResult(plan.getId(), PlanStatus.STALE_REJECTED,
                    plan.getResultMessage(), List.of());
        }

        List<String> codes = plan.getSnapshots().stream()
                .map(MovePlanStackSnapshot::getStackCode)
                .sorted()
                .toList();
        Map<String, YardStack> locked = stackRepository.findAllByCodeInForUpdate(codes).stream()
                .collect(Collectors.toMap(YardStack::getCode, Function.identity()));

        for (MovePlanStackSnapshot snapshot : plan.getSnapshots()) {
            YardStack stack = locked.get(snapshot.getStackCode());
            if (stack == null) {
                return rejectStale(plan, "堆栈已不存在: " + snapshot.getStackCode());
            }
            if (stack.getVersion() != snapshot.getStackVersion()) {
                return rejectStale(plan, "堆栈 " + snapshot.getStackCode() + " 版本已变化（快照 "
                        + snapshot.getStackVersion() + "，当前 " + stack.getVersion() + "），计划按陈旧拒绝");
            }
        }

        for (MovePlanStep step : plan.getSteps()) {
            applyStep(plan.getId(), step, locked);
        }
        plan.markExecuted("共执行 " + plan.getSteps().size() + " 步");
        return new ExecuteResult(plan.getId(), PlanStatus.EXECUTED,
                plan.getResultMessage(), eventsOf(planId));
    }

    private ExecuteResult rejectStale(MovePlan plan, String message) {
        plan.markStaleRejected(message);
        return new ExecuteResult(plan.getId(), PlanStatus.STALE_REJECTED, message, List.of());
    }

    private void applyStep(Long planId, MovePlanStep step, Map<String, YardStack> locked) {
        Container container = containerRepository.findByContainerNo(step.getContainerNo())
                .orElseThrow(() -> new IllegalStateException("箱不存在: " + step.getContainerNo()));
        YardStack source = locked.get(container.getStack().getCode());
        YardStack target = locked.get(step.getTargetStackCode());
        if (source == null || target == null) {
            throw new IllegalStateException("步骤涉及未锁定堆栈");
        }
        if (source.getId().equals(target.getId())) {
            throw new IllegalStateException("箱 " + step.getContainerNo() + " 已在目标堆栈 " + target.getCode());
        }
        if (container.getTier() != source.getCurrentTiers()) {
            throw new IllegalStateException("箱 " + step.getContainerNo() + " 不在栈顶，无法移动");
        }
        if (!target.canAccept(container.getWeight())) {
            throw new IllegalStateException("目标堆栈 " + target.getCode() + " 层数或重量超限");
        }

        int fromTier = container.getTier();
        source.removeTop(container.getWeight());
        target.addTop(container.getWeight());
        container.placeOn(target, target.getCurrentTiers());
        entityManager.flush();

        eventRepository.save(new MoveEvent(planId, step.getSeq(), container.getContainerNo(),
                source.getCode(), target.getCode(), fromTier, container.getTier(),
                container.getWeight()));
    }

    @Transactional(readOnly = true)
    public List<EventView> eventsOf(Long planId) {
        List<MoveEvent> events = (planId == null)
                ? eventRepository.findAllByOrderById()
                : eventRepository.findByPlanIdOrderBySeq(planId);
        return events.stream().map(EventView::from).toList();
    }

    private static final class Simulation {

        private record SimContainer(String no, long weight, String stackCode) {
        }

        private static final class SimStack {
            final String code;
            final int maxTiers;
            final long maxWeight;
            final Deque<String> containers = new ArrayDeque<>();
            long currentWeight;

            SimStack(String code, int maxTiers, long maxWeight) {
                this.code = code;
                this.maxTiers = maxTiers;
                this.maxWeight = maxWeight;
            }
        }

        private final Map<String, SimStack> stacks;
        private final Map<String, SimContainer> containers;
        private final Set<String> involved = new LinkedHashSet<>();

        private Simulation(Map<String, SimStack> stacks, Map<String, SimContainer> containers) {
            this.stacks = stacks;
            this.containers = containers;
        }

        static Simulation load(List<YardStack> stacks, List<Container> containers) {
            Map<String, SimStack> simStacks = new HashMap<>();
            for (YardStack s : stacks) {
                simStacks.put(s.getCode(), new SimStack(s.getCode(), s.getMaxTiers(), s.getMaxWeight()));
            }
            Map<String, SimContainer> simContainers = new HashMap<>();
            containers.stream()
                    .sorted((a, b) -> Integer.compare(a.getTier(), b.getTier()))
                    .forEach(c -> {
                        simContainers.put(c.getContainerNo(),
                                new SimContainer(c.getContainerNo(), c.getWeight(), c.getStack().getCode()));
                        SimStack stack = simStacks.get(c.getStack().getCode());
                        stack.containers.addLast(c.getContainerNo());
                        stack.currentWeight += c.getWeight();
                    });
            return new Simulation(simStacks, simContainers);
        }

        Set<String> involvedStacks() {
            return involved;
        }

        SimulationResult run(List<StepRequest> steps) {
            List<StepSimulation> results = new ArrayList<>();
            boolean valid = true;
            int seq = 1;
            for (StepRequest step : steps) {
                SimContainer container = containers.get(step.containerNo());
                String from = container != null ? container.stackCode() : null;
                String error = applyStep(step);
                results.add(new StepSimulation(seq++, step.containerNo(), from, step.targetStack(),
                        error == null, error == null ? "ok" : error));
                if (error != null) {
                    valid = false;
                    break;
                }
            }
            return new SimulationResult(valid, results);
        }

        private String applyStep(StepRequest step) {
            SimContainer container = containers.get(step.containerNo());
            if (container == null) {
                return "箱不存在: " + step.containerNo();
            }
            SimStack source = stacks.get(container.stackCode());
            SimStack target = stacks.get(step.targetStack());
            if (target == null) {
                return "目标堆栈不存在: " + step.targetStack();
            }
            involved.add(source.code);
            involved.add(target.code);
            if (source == target) {
                return "箱 " + step.containerNo() + " 已在堆栈 " + target.code;
            }
            if (!source.containers.peekLast().equals(step.containerNo())) {
                return "箱 " + step.containerNo() + " 不在堆栈 " + source.code + " 栈顶";
            }
            if (target.containers.size() >= target.maxTiers) {
                return "目标堆栈 " + target.code + " 层数超限";
            }
            if (target.currentWeight + container.weight() > target.maxWeight) {
                return "目标堆栈 " + target.code + " 重量超限";
            }
            source.containers.pollLast();
            source.currentWeight -= container.weight();
            target.containers.addLast(step.containerNo());
            target.currentWeight += container.weight();
            containers.put(step.containerNo(),
                    new SimContainer(container.no(), container.weight(), target.code));
            return null;
        }
    }
}
