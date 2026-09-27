package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.Appointment;
import com.chris64233.cc.containeryard.domain.AppointmentDirection;
import com.chris64233.cc.containeryard.domain.AppointmentStatus;
import com.chris64233.cc.containeryard.domain.Container;
import com.chris64233.cc.containeryard.domain.GateWindow;
import com.chris64233.cc.containeryard.domain.MoveEvent;
import com.chris64233.cc.containeryard.domain.PlanStatus;
import com.chris64233.cc.containeryard.domain.YardStack;
import com.chris64233.cc.containeryard.repo.AppointmentRepository;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.GateWindowRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.dto.AppointmentRequest;
import com.chris64233.cc.containeryard.service.dto.AppointmentView;
import com.chris64233.cc.containeryard.service.dto.ArrivalRequest;
import com.chris64233.cc.containeryard.service.dto.ArrivalResult;
import com.chris64233.cc.containeryard.service.dto.CancelRequest;
import com.chris64233.cc.containeryard.service.dto.ExecuteResult;
import com.chris64233.cc.containeryard.service.dto.PlanView;
import com.chris64233.cc.containeryard.service.dto.StepRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final GateWindowRepository windowRepository;
    private final YardStackRepository stackRepository;
    private final ContainerRepository containerRepository;
    private final MovePlanRepository planRepository;
    private final MoveEventRepository eventRepository;
    private final PlanService planService;
    private final EntityManager entityManager;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              GateWindowRepository windowRepository,
                              YardStackRepository stackRepository,
                              ContainerRepository containerRepository,
                              MovePlanRepository planRepository,
                              MoveEventRepository eventRepository,
                              PlanService planService,
                              EntityManager entityManager) {
        this.appointmentRepository = appointmentRepository;
        this.windowRepository = windowRepository;
        this.stackRepository = stackRepository;
        this.containerRepository = containerRepository;
        this.planRepository = planRepository;
        this.eventRepository = eventRepository;
        this.planService = planService;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------ 预约申报

    @Transactional
    public AppointmentView create(AppointmentRequest request) {
        var existing = appointmentRepository.findByAppointmentNo(request.appointmentNo());
        if (existing.isPresent()) {
            // 同一预约业务号重复申报：幂等返回已有预约，不重复占用资源。
            return toView(existing.get());
        }

        GateWindow window = windowRepository.findById(request.windowId())
                .orElseThrow(() -> new NotFoundException("闸口时段不存在: " + request.windowId()));

        long weight;
        if (request.direction() == AppointmentDirection.INBOUND) {
            if (request.targetStack() == null || request.targetStack().isBlank()) {
                throw new IllegalStateException("进场预约必须声明目标堆栈");
            }
            if (request.weight() <= 0) {
                throw new IllegalStateException("进场预约必须声明正数箱重");
            }
            stackRepository.findByCode(request.targetStack())
                    .orElseThrow(() -> new NotFoundException("目标堆栈不存在: " + request.targetStack()));
            if (containerRepository.findByContainerNo(request.containerNo()).isPresent()) {
                throw new DuplicateException("箱已在场内，不能申报进场: " + request.containerNo());
            }
            weight = request.weight();
        } else {
            if (request.targetStack() != null && !request.targetStack().isBlank()) {
                throw new IllegalStateException("出场预约不应声明目标堆栈");
            }
            Container container = containerRepository.findByContainerNo(request.containerNo())
                    .orElseThrow(() -> new NotFoundException("箱不在场内，不能申报出场: " + request.containerNo()));
            weight = container.getWeight();
        }

        if (appointmentRepository.existsByActiveContainerNo(request.containerNo())) {
            throw new DuplicateException("该箱已存在有效预约: " + request.containerNo());
        }

        Appointment appointment = new Appointment(request.appointmentNo(), request.containerNo(),
                request.direction(), request.vehicleNo(), window,
                request.direction() == AppointmentDirection.INBOUND ? request.targetStack() : null,
                weight);
        try {
            appointmentRepository.save(appointment);
            entityManager.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发下 active_container_no 唯一约束兜底：一个箱同一时间只能有一笔有效预约。
            throw new DuplicateException("该箱已存在有效预约: " + request.containerNo());
        }
        return toView(appointment);
    }

    // ------------------------------------------------------------------ 批准

    @Transactional
    public AppointmentView approve(String appointmentNo) {
        Appointment appointment = lockAppointment(appointmentNo);
        if (appointment.getStatus() == AppointmentStatus.APPROVED) {
            return toView(appointment);
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ConflictException("预约当前状态不允许批准: " + appointment.getStatus());
        }

        GateWindow window = windowRepository.findByIdForUpdate(appointment.getWindow().getId())
                .orElseThrow(() -> new NotFoundException("闸口时段不存在"));
        if (!window.hasFreeCapacity()) {
            throw new IllegalStateException("闸口时段容量不足，整笔预约失败");
        }

        Long planId = null;
        String message;
        if (appointment.getDirection() == AppointmentDirection.INBOUND) {
            YardStack stack = stackRepository.findByCodeForUpdate(appointment.getTargetStackCode())
                    .orElseThrow(() -> new NotFoundException("目标堆栈不存在: " + appointment.getTargetStackCode()));
            if (!stack.canReserve(appointment.getContainerWeight())) {
                throw new IllegalStateException(
                        "目标堆栈 " + stack.getCode() + " 可预留高度或重量不足，整笔预约失败");
            }
            window.reserve();
            stack.reserve(appointment.getContainerWeight());
            message = "进场预约已批准：闸口名额与目标堆位已预留";
        } else {
            // 基于当前堆叠关系生成必要的前置移箱计划；任一步无法安置则整笔失败（事务回滚）。
            List<StepRequest> steps;
            try {
                steps = planService.planRetrievalSteps(appointment.getContainerNo());
            } catch (IllegalStateException e) {
                throw new IllegalStateException("出场前置移箱规划失败，整笔预约失败：" + e.getMessage());
            }
            planId = planService.saveOutboundPlan(appointmentNo, appointment.getContainerNo(), steps);
            window.reserve();
            message = steps.isEmpty()
                    ? "出场预约已批准：目标箱已在栈顶，无需前置移箱"
                    : "出场预约已批准：已生成 " + steps.size() + " 步前置移箱计划";
        }

        appointment.markApproved(planId, message);
        entityManager.flush();
        return toView(appointment);
    }

    // ------------------------------------------------------------------ 到场确认

    @Transactional
    public ArrivalResult arrive(ArrivalRequest request) {
        Appointment appointment = lockAppointment(request.appointmentNo());

        if (appointment.getStatus() == AppointmentStatus.ARRIVED) {
            if (!request.arrivalNo().equals(appointment.getArrivalNo())) {
                throw new ConflictException("预约已用其它到场业务号完成确认: " + appointment.getArrivalNo());
            }
            return new ArrivalResult(ArrivalResult.OUTCOME_ARRIVED, false,
                    "到场业务号重复，幂等返回", toView(appointment));
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new ConflictException("预约已取消，不能到场确认");
        }
        if (appointment.getStatus() != AppointmentStatus.APPROVED) {
            throw new IllegalStateException("预约尚未批准，不能到场确认");
        }
        if (appointment.getVersion() != request.expectedVersion()) {
            throw new ConflictException("预约版本不匹配（请求版本 " + request.expectedVersion()
                    + "，当前版本 " + appointment.getVersion() + "）");
        }
        Instant now = request.at() != null ? request.at() : Instant.now();
        if (!appointment.getWindow().contains(now)) {
            throw new IllegalStateException("到场时间 " + now + " 不在预约时间窗 ["
                    + appointment.getWindow().getStartAt() + ", " + appointment.getWindow().getEndAt() + ") 内");
        }

        return appointment.getDirection() == AppointmentDirection.INBOUND
                ? arriveInbound(appointment, request)
                : arriveOutbound(appointment, request);
    }

    private ArrivalResult arriveInbound(Appointment appointment, ArrivalRequest request) {
        if (containerRepository.findByContainerNo(appointment.getContainerNo()).isPresent()) {
            throw new IllegalStateException("箱已在场内，不能重复进场: " + appointment.getContainerNo());
        }
        GateWindow window = windowRepository.findByIdForUpdate(appointment.getWindow().getId())
                .orElseThrow(() -> new NotFoundException("闸口时段不存在"));
        YardStack stack = stackRepository.findByCodeForUpdate(appointment.getTargetStackCode())
                .orElseThrow(() -> new NotFoundException("目标堆栈不存在: " + appointment.getTargetStackCode()));
        long weight = appointment.getContainerWeight();
        if (!stack.canAccept(weight)) {
            // 预留量保证了容量；若不成立说明数据异常，保留预约等待人工处理。
            throw new ConflictException("目标堆栈当前无法落箱，保留预约等待人工处理: " + stack.getCode());
        }
        stack.consumeReserved(weight);
        Container container = new Container(appointment.getContainerNo(), weight);
        container.placeOn(stack, stack.getCurrentTiers());
        containerRepository.save(container);
        window.markServed();
        eventRepository.save(new MoveEvent(null, request.arrivalNo(), 1,
                appointment.getContainerNo(), null, stack.getCode(), 0, stack.getCurrentTiers(), weight));

        appointment.markArrived(request.arrivalNo(), "进场落箱完成：" + stack.getCode());
        entityManager.flush();
        return new ArrivalResult(ArrivalResult.OUTCOME_ARRIVED, false, appointment.getMessage(), toView(appointment));
    }

    private ArrivalResult arriveOutbound(Appointment appointment, ArrivalRequest request) {
        GateWindow window = windowRepository.findByIdForUpdate(appointment.getWindow().getId())
                .orElseThrow(() -> new NotFoundException("闸口时段不存在"));
        Container container = containerRepository.findByContainerNoForUpdate(appointment.getContainerNo())
                .orElseThrow(() -> new IllegalStateException(
                        "箱不在场内，实际位置与预约不符: " + appointment.getContainerNo()));
        YardStack sourceStack = stackRepository.findByCodeForUpdate(container.getStack().getCode())
                .orElseThrow(() -> new IllegalStateException("箱所在堆栈不存在"));

        boolean replanned = false;
        Long effectivePlanId = appointment.getPlanId();

        if (container.getTier() == sourceStack.getCurrentTiers() && effectivePlanId != null) {
            // 目标箱已在栈顶：旧前置计划不再需要，作废且绝不执行。
            planService.cancelPendingPlan(effectivePlanId, "到场时目标箱已在栈顶，前置移箱计划作废");
            appointment.replacePlan(null, "堆场已变化，原前置移箱计划作废；目标箱已在栈顶");
            effectivePlanId = null;
        } else if (container.getTier() != sourceStack.getCurrentTiers()) {
            ExecuteResult executed = effectivePlanId == null ? null : tryExecute(effectivePlanId);
            if (executed == null || executed.status() != PlanStatus.EXECUTED) {
                // 旧计划为空或已陈旧/取消：按当前布局重新规划；失败则保留原预约等待人工处理。
                String staleReason = executed == null
                        ? "批准后无前置移箱计划"
                        : "原前置移箱计划已陈旧（" + executed.message() + "）";
                Long newPlanId;
                try {
                    newPlanId = planService.replanOutbound(
                            appointment.getAppointmentNo(), appointment.getContainerNo());
                    if (newPlanId != null) {
                        ExecuteResult retry = tryExecute(newPlanId);
                        if (retry.status() != PlanStatus.EXECUTED) {
                            throw new IllegalStateException(retry.message());
                        }
                    }
                } catch (IllegalStateException e) {
                    appointment.note(staleReason + "；重新规划失败：" + e.getMessage() + "，预约保留等待人工处理");
                    entityManager.flush();
                    return new ArrivalResult(ArrivalResult.OUTCOME_NEEDS_MANUAL, true,
                            appointment.getMessage(), toView(appointment));
                }
                appointment.replacePlan(newPlanId, staleReason + "；已按当前布局重新规划并执行");
                effectivePlanId = newPlanId;
                replanned = true;            }
        }

        // 重新读取箱位置：前置移箱执行后箱应位于其堆栈栈顶。
        container = containerRepository.findByContainerNoForUpdate(appointment.getContainerNo())
                .orElseThrow(() -> new IllegalStateException("提箱前箱消失: " + appointment.getContainerNo()));
        if (container.getTier() != container.getStack().getCurrentTiers()) {
            appointment.note("前置移箱完成后箱仍不在栈顶，预约保留等待人工处理");
            entityManager.flush();
            return new ArrivalResult(ArrivalResult.OUTCOME_NEEDS_MANUAL, replanned,
                    appointment.getMessage(), toView(appointment));
        }

        YardStack stack = container.getStack();
        int fromTier = container.getTier();
        long weight = container.getWeight();
        String stackCode = stack.getCode();
        stack.removeTop(weight);
        containerRepository.delete(container);
        window.markServed();
        eventRepository.save(new MoveEvent(null, request.arrivalNo(), 1,
                appointment.getContainerNo(), stackCode, null, fromTier, 0, weight));

        String message = (replanned ? "出场计划已重排并执行；" : "")
                + "提箱出场完成：" + stackCode;
        appointment.markArrived(request.arrivalNo(), message);
        entityManager.flush();
        return new ArrivalResult(ArrivalResult.OUTCOME_ARRIVED, replanned,
                appointment.getMessage(), toView(appointment));
    }

    private ExecuteResult tryExecute(Long planId) {
        return planService.executeInternal(planId);
    }

    // ------------------------------------------------------------------ 取消

    @Transactional
    public AppointmentView cancel(CancelRequest request) {
        Appointment appointment = lockAppointment(request.appointmentNo());

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            if (!request.cancelNo().equals(appointment.getCancelNo())) {
                throw new ConflictException("预约已用其它取消业务号取消: " + appointment.getCancelNo());
            }
            return toView(appointment);
        }
        if (appointment.getStatus() == AppointmentStatus.ARRIVED) {
            throw new ConflictException("预约已到场完成，不能取消");
        }

        if (appointment.getStatus() == AppointmentStatus.APPROVED) {
            GateWindow window = windowRepository.findByIdForUpdate(appointment.getWindow().getId())
                    .orElseThrow(() -> new NotFoundException("闸口时段不存在"));
            window.releaseReserved();
            if (appointment.getDirection() == AppointmentDirection.INBOUND) {
                YardStack stack = stackRepository.findByCodeForUpdate(appointment.getTargetStackCode())
                        .orElseThrow(() -> new NotFoundException("目标堆栈不存在: " + appointment.getTargetStackCode()));
                stack.releaseReservation(appointment.getContainerWeight());
            } else if (appointment.getPlanId() != null) {
                planService.cancelPendingPlan(appointment.getPlanId(), "出场预约取消，前置移箱计划作废");
            }
        }

        appointment.markCancelled(request.cancelNo(), "预约已取消，预留资源已释放");
        entityManager.flush();
        return toView(appointment);
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public AppointmentView get(String appointmentNo) {
        Appointment appointment = appointmentRepository.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + appointmentNo));
        return toView(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> list() {
        return appointmentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AppointmentView::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanView getPlan(String appointmentNo) {
        Appointment appointment = appointmentRepository.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + appointmentNo));
        if (appointment.getPlanId() == null) {
            throw new NotFoundException("预约没有关联的前置移箱计划: " + appointmentNo);
        }
        return planRepository.findById(appointment.getPlanId())
                .map(PlanView::from)
                .orElseThrow(() -> new NotFoundException("移箱计划不存在: " + appointment.getPlanId()));
    }

    @Transactional(readOnly = true)
    public AppointmentView.OccupancyView windowOccupancy(Long windowId) {
        GateWindow window = windowRepository.findById(windowId)
                .orElseThrow(() -> new NotFoundException("闸口时段不存在: " + windowId));
        List<AppointmentView.OccupancyEntry> entries = appointmentRepository.findByWindowIdOrderById(windowId)
                .stream()
                .filter(Appointment::isActive)
                .map(AppointmentView.OccupancyEntry::from)
                .toList();
        return new AppointmentView.OccupancyView("GATE_WINDOW",
                window.getStartAt() + " ~ " + window.getEndAt(), window.getCapacity(),
                window.getReserved(), window.getServed(), entries);
    }

    @Transactional(readOnly = true)
    public AppointmentView.OccupancyView stackOccupancy(String stackCode) {
        YardStack stack = stackRepository.findByCode(stackCode)
                .orElseThrow(() -> new NotFoundException("堆栈不存在: " + stackCode));
        List<AppointmentView.OccupancyEntry> entries = appointmentRepository
                .findByTargetStackCodeAndStatusOrderById(stackCode, AppointmentStatus.APPROVED)
                .stream()
                .map(AppointmentView.OccupancyEntry::from)
                .toList();
        return new AppointmentView.OccupancyView("YARD_STACK", stackCode,
                stack.getMaxTiers(), stack.getReservedTiers(), stack.getCurrentTiers(), entries);
    }

    // ------------------------------------------------------------------ 内部

    private Appointment lockAppointment(String appointmentNo) {
        return appointmentRepository.findByAppointmentNoForUpdate(appointmentNo)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + appointmentNo));
    }

    private AppointmentView toView(Appointment appointment) {
        PlanView planView = null;
        if (appointment.getPlanId() != null) {
            planView = planRepository.findById(appointment.getPlanId())
                    .map(PlanView::from)
                    .orElse(null);
        }
        return AppointmentView.from(appointment, planView);
    }
}
