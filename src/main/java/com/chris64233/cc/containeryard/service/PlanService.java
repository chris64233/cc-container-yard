package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.Container;
import com.chris64233.cc.containeryard.domain.MoveEvent;
import com.chris64233.cc.containeryard.domain.MovePlan;
import com.chris64233.cc.containeryard.domain.MovePlanStep;
import com.chris64233.cc.containeryard.domain.PlanStackSnapshot;
import com.chris64233.cc.containeryard.domain.PlanStatus;
import com.chris64233.cc.containeryard.domain.YardStack;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.YardSimulator.SimulatedMove;
import com.chris64233.cc.containeryard.service.YardSimulator.StepCommand;
import com.chris64233.cc.containeryard.web.dto.Views.ExecuteResultView;
import com.chris64233.cc.containeryard.web.dto.Views.MoveEventView;
import com.chris64233.cc.containeryard.web.dto.Views.PlanStepView;
import com.chris64233.cc.containeryard.web.dto.Views.PlanView;
import com.chris64233.cc.containeryard.web.dto.Views.SnapshotView;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlanService {

    private final MovePlanRepository planRepository;
    private final MoveEventRepository eventRepository;
    private final YardStackRepository stackRepository;
    private final ContainerRepository containerRepository;
    private final EntityManager entityManager;

    public PlanService(MovePlanRepository planRepository,
                       MoveEventRepository eventRepository,
                       YardStackRepository stackRepository,
                       ContainerRepository containerRepository,
                       EntityManager entityManager) {
        this.planRepository = planRepository;
        this.eventRepository = eventRepository;
        this.stackRepository = stackRepository;
        this.containerRepository = containerRepository;
        this.entityManager = entityManager;
    }

    /**
     * 提交计划：在内存中完整模拟所有步骤，任一步失败则整份计划不保存。
     * 保存时记录所有涉及堆栈的版本快照。
     */
    @Transactional
    public PlanView submit(List<StepCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new PlanValidationException("计划至少包含一个步骤");
        }
        Set<String> containerNos = commands.stream().map(StepCommand::containerNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Container> containersByNo = containerRepository.findByContainerNoIn(containerNos)
                .stream().collect(Collectors.toMap(Container::getContainerNo, Function.identity()));
        for (String no : containerNos) {
            if (!containersByNo.containsKey(no)) {
                throw new PlanValidationException("集装箱 " + no + " 不存在");
            }
        }
        Set<String> involvedCodes = new LinkedHashSet<>();
        commands.forEach(c -> involvedCodes.add(c.targetStackCode()));
        containersByNo.values().forEach(c -> involvedCodes.add(c.getStack().getCode()));

        List<YardStack> stacks = stackRepository.findByCodeIn(involvedCodes);
        Map<String, YardStack> stacksByCode = stacks.stream()
                .collect(Collectors.toMap(YardStack::getCode, Function.identity()));
        for (StepCommand command : commands) {
            if (!stacksByCode.containsKey(command.targetStackCode())) {
                throw new PlanValidationException("目标堆栈 " + command.targetStackCode() + " 不存在");
            }
        }
        List<Container> containers = containerRepository
                .findByStack_CodeInOrderByStack_CodeAscTierAsc(involvedCodes);

        List<SimulatedMove> moves = YardSimulator.simulate(commands, stacks, containers);

        List<MovePlanStep> steps = moves.stream()
                .map(m -> new MovePlanStep(m.seq(), m.containerNo(), m.fromStackCode(),
                        m.toStackCode(), m.toTier()))
                .toList();
        List<PlanStackSnapshot> snapshots = stacks.stream()
                .map(s -> new PlanStackSnapshot(s.getCode(), s.getVersion()))
                .toList();
        MovePlan plan = planRepository.save(new MovePlan(steps, snapshots));
        return toView(plan);
    }

    /**
     * 执行计划：单一事务内重放全部步骤。任一相关堆栈版本已变化则整份计划
     * 标记为陈旧并拒绝，不执行部分步骤；重复执行返回原结果。
     */
    @Transactional
    public ExecuteResultView execute(Long planId) {
        MovePlan plan = planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new NotFoundException("计划 " + planId + " 不存在"));
        if (plan.getStatus() == PlanStatus.EXECUTED) {
            return new ExecuteResultView(plan.getId(), plan.getStatus().name(), true,
                    eventViews(plan.getId()));
        }
        if (plan.getStatus() == PlanStatus.STALE) {
            return new ExecuteResultView(plan.getId(), plan.getStatus().name(), false, List.of());
        }

        Set<String> involvedCodes = plan.getSnapshots().stream()
                .map(PlanStackSnapshot::getStackCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<YardStack> stacks = stackRepository.findByCodeIn(involvedCodes);
        Map<String, YardStack> stacksByCode = stacks.stream()
                .collect(Collectors.toMap(YardStack::getCode, Function.identity()));
        for (PlanStackSnapshot snapshot : plan.getSnapshots()) {
            YardStack stack = stacksByCode.get(snapshot.getStackCode());
            if (stack == null || stack.getVersion() != snapshot.getStackVersion()) {
                plan.markStale();
                return new ExecuteResultView(plan.getId(), PlanStatus.STALE.name(), false, List.of());
            }
        }

        List<Container> containers = containerRepository
                .findByStack_CodeInOrderByStack_CodeAscTierAsc(involvedCodes);
        List<StepCommand> commands = plan.getSteps().stream()
                .map(s -> new StepCommand(s.getContainerNo(), s.getToStackCode()))
                .toList();
        List<SimulatedMove> moves = YardSimulator.simulate(commands, stacks, containers);

        Map<String, Container> containersByNo = containers.stream()
                .collect(Collectors.toMap(Container::getContainerNo, Function.identity()));
        List<MoveEvent> events = new ArrayList<>();
        for (SimulatedMove move : moves) {
            Container container = containersByNo.get(move.containerNo());
            YardStack from = stacksByCode.get(move.fromStackCode());
            YardStack to = stacksByCode.get(move.toStackCode());
            from.release(container.getWeight());
            to.accept(container.getWeight());
            container.moveTo(to, move.toTier());
            entityManager.flush();
            events.add(eventRepository.save(new MoveEvent(plan.getId(), move.seq(),
                    move.containerNo(), move.fromStackCode(), move.toStackCode(),
                    move.fromTier(), move.toTier())));
        }
        plan.markExecuted();
        return new ExecuteResultView(plan.getId(), PlanStatus.EXECUTED.name(), false,
                events.stream().map(PlanService::toView).toList());
    }

    @Transactional(readOnly = true)
    public PlanView getPlan(Long planId) {
        MovePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("计划 " + planId + " 不存在"));
        return toView(plan);
    }

    @Transactional(readOnly = true)
    public List<MoveEventView> getEvents(Long planId) {
        if (!planRepository.existsById(planId)) {
            throw new NotFoundException("计划 " + planId + " 不存在");
        }
        return eventViews(planId);
    }

    private List<MoveEventView> eventViews(Long planId) {
        return eventRepository.findByPlanIdOrderBySeq(planId).stream()
                .map(PlanService::toView).toList();
    }

    private static PlanView toView(MovePlan plan) {
        List<PlanStepView> steps = plan.getSteps().stream()
                .map(s -> new PlanStepView(s.getSeq(), s.getContainerNo(), s.getFromStackCode(),
                        s.getToStackCode(), s.getToTier()))
                .toList();
        List<SnapshotView> snapshots = plan.getSnapshots().stream()
                .map(s -> new SnapshotView(s.getStackCode(), s.getStackVersion()))
                .toList();
        return new PlanView(plan.getId(), plan.getStatus().name(), plan.getCreatedAt(),
                plan.getExecutedAt(), steps, snapshots);
    }

    private static MoveEventView toView(MoveEvent event) {
        return new MoveEventView(event.getId(), event.getSeq(), event.getContainerNo(),
                event.getFromStackCode(), event.getToStackCode(),
                event.getFromTier(), event.getToTier(), event.getExecutedAt());
    }
}
