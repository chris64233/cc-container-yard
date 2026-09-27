package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.Appointment;
import com.chris64233.cc.containeryard.domain.AppointmentStackSnapshot;
import com.chris64233.cc.containeryard.domain.AppointmentStatus;
import com.chris64233.cc.containeryard.domain.Container;
import com.chris64233.cc.containeryard.domain.GateDirection;
import com.chris64233.cc.containeryard.domain.GateWindow;
import com.chris64233.cc.containeryard.domain.PreMoveStep;
import com.chris64233.cc.containeryard.domain.SlotReservation;
import com.chris64233.cc.containeryard.domain.YardStack;
import com.chris64233.cc.containeryard.repo.AppointmentRepository;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.GateWindowRepository;
import com.chris64233.cc.containeryard.repo.SlotReservationRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
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
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private final GateWindowRepository windowRepository;
    private final AppointmentRepository appointmentRepository;
    private final SlotReservationRepository slotRepository;
    private final YardStackRepository stackRepository;
    private final ContainerRepository containerRepository;
    private final EntityManager entityManager;

    public AppointmentService(GateWindowRepository windowRepository,
                              AppointmentRepository appointmentRepository,
                              SlotReservationRepository slotRepository,
                              YardStackRepository stackRepository,
                              ContainerRepository containerRepository,
                              EntityManager entityManager) {
        this.windowRepository = windowRepository;
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.stackRepository = stackRepository;
        this.containerRepository = containerRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public WindowView createWindow(WindowRequest request) {
        GateWindow window = windowRepository.save(
                new GateWindow(request.startAt(), request.endAt(), request.capacity()));
        return WindowView.from(window);
    }

    @Transactional(readOnly = true)
    public List<WindowView> schedule() {
        return windowRepository.findAllByOrderByStartAt().stream()
                .map(WindowView::from)
                .toList();
    }

    /**
     * 预约即批准：闸口容量、堆位预留（进场）或前置移箱计划（出场）在同一事务内完成，
     * 任一资源或步骤不成立则整笔预约失败回滚。按预约业务号幂等。
     */
    @Transactional
    public AppointmentResult book(AppointmentRequest request) {
        var existing = appointmentRepository.findByAppointmentNo(request.appointmentNo());
        if (existing.isPresent()) {
            return new AppointmentResult(toView(existing.get()), false);
        }
        GateWindow window = windowRepository.findByIdForUpdate(request.windowId())
                .orElseThrow(() -> new NotFoundException("闸口时间窗不存在: " + request.windowId()));
        if (!window.hasCapacity()) {
            throw new IllegalStateException("闸口时间窗容量已满: " + request.windowId());
        }
        appointmentRepository.findByActiveContainerKey(request.containerNo()).ifPresent(a -> {
            throw new DuplicateException("箱 " + request.containerNo() + " 已存在有效预约: " + a.getAppointmentNo());
        });

        Appointment appointment = new Appointment(request.appointmentNo(), request.containerNo(),
                request.direction(), request.vehicleNo(), window, request.targetStack(), request.weight());

        SlotReservation slot = null;
        if (request.direction() == GateDirection.IN) {
            slot = reserveInboundSlot(appointment, request);
        } else {
            planOutbound(appointment, request);
        }
        window.reserve();
        try {
            appointmentRepository.saveAndFlush(appointment);
            if (slot != null) {
                slotRepository.saveAndFlush(slot);
            }
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateException("预约冲突：预约业务号或箱号已存在有效预约");
        }
        return new AppointmentResult(AppointmentView.from(appointment, slot), true);
    }

    private SlotReservation reserveInboundSlot(Appointment appointment, AppointmentRequest request) {
        if (request.weight() == null) {
            throw new IllegalStateException("进场预约必须声明箱重");
        }
        if (containerRepository.findByContainerNo(request.containerNo()).isPresent()) {
            throw new IllegalStateException("箱 " + request.containerNo() + " 已在场内，不能预约进场");
        }
        YardStack stack = stackRepository.findByCodeForUpdate(request.targetStack())
                .orElseThrow(() -> new NotFoundException("堆栈不存在: " + request.targetStack()));
        List<SlotReservation> active = slotRepository
                .findByStackCodeAndActiveSlotKeyIsNotNull(stack.getCode());
        int nextTier = stack.getCurrentTiers() + active.size() + 1;
        if (nextTier > stack.getMaxTiers()) {
            throw new IllegalStateException("堆栈 " + stack.getCode() + " 层数不足，无法预留堆位");
        }
        long reservedWeight = active.stream().mapToLong(SlotReservation::getWeight).sum();
        if (stack.getCurrentWeight() + reservedWeight + request.weight() > stack.getMaxWeight()) {
            throw new IllegalStateException("堆栈 " + stack.getCode() + " 重量超限，无法预留堆位");
        }
        return new SlotReservation(appointment, stack.getCode(), nextTier, request.weight());
    }

    private void planOutbound(Appointment appointment, AppointmentRequest request) {
        Container container = containerRepository.findByContainerNo(request.containerNo())
                .orElseThrow(() -> new NotFoundException("箱不在场内: " + request.containerNo()));
        YardStack source = stackRepository.findByCodeForUpdate(container.getStack().getCode())
                .orElseThrow(() -> new NotFoundException("堆栈不存在: " + container.getStack().getCode()));
        if (!source.getCode().equals(request.targetStack())) {
            throw new IllegalStateException("箱 " + request.containerNo() + " 实际位于堆栈 "
                    + source.getCode() + "，与预约声明的 " + request.targetStack() + " 不一致");
        }
        List<YardStack> stacks = stackRepository.findAllByOrderByCode();
        List<PreMoveStep> steps = planPreMoves(appointment, container, stacks);
        appointment.replacePlan(steps, snapshotsOf(appointment, stacks, steps, source.getCode()));
    }

    /**
     * 为被压箱生成前置移箱计划：自上而下依次把压在上方的箱移到其他有空余层数与重量的堆栈，
     * 空余容量需扣除已生效的进场堆位预留。
     */
    private List<PreMoveStep> planPreMoves(Appointment appointment, Container target, List<YardStack> stacks) {
        String sourceCode = target.getStack().getCode();
        List<Container> blocking = containerRepository.findByStackCodeOrderByTier(sourceCode).stream()
                .filter(c -> c.getTier() > target.getTier())
                .sorted(Comparator.comparingInt(Container::getTier).reversed())
                .toList();
        Map<String, long[]> free = freeCapacity(stacks);
        List<PreMoveStep> steps = new ArrayList<>();
        int seq = 1;
        for (Container box : blocking) {
            String targetCode = null;
            for (YardStack stack : stacks) {
                if (stack.getCode().equals(sourceCode)) {
                    continue;
                }
                long[] f = free.get(stack.getCode());
                if (f[0] >= 1 && f[1] >= box.getWeight()) {
                    targetCode = stack.getCode();
                    break;
                }
            }
            if (targetCode == null) {
                throw new IllegalStateException(
                        "箱 " + box.getContainerNo() + " 压在目标箱上方且无可用堆位，无法生成前置移箱计划");
            }
            free.get(targetCode)[0] -= 1;
            free.get(targetCode)[1] -= box.getWeight();
            steps.add(new PreMoveStep(appointment, seq++, box.getContainerNo(), sourceCode, targetCode));
        }
        return steps;
    }

    private Map<String, long[]> freeCapacity(List<YardStack> stacks) {
        Map<String, List<SlotReservation>> reservationsByStack = slotRepository
                .findByActiveSlotKeyIsNotNull().stream()
                .collect(Collectors.groupingBy(SlotReservation::getStackCode));
        Map<String, long[]> free = new HashMap<>();
        for (YardStack stack : stacks) {
            List<SlotReservation> reservations = reservationsByStack.getOrDefault(stack.getCode(), List.of());
            long reservedWeight = reservations.stream().mapToLong(SlotReservation::getWeight).sum();
            free.put(stack.getCode(), new long[]{
                    stack.getMaxTiers() - stack.getCurrentTiers() - reservations.size(),
                    stack.getMaxWeight() - stack.getCurrentWeight() - reservedWeight});
        }
        return free;
    }

    private List<AppointmentStackSnapshot> snapshotsOf(Appointment appointment, List<YardStack> stacks,
                                                       List<PreMoveStep> steps, String sourceCode) {
        Set<String> involved = new LinkedHashSet<>();
        involved.add(sourceCode);
        steps.forEach(s -> involved.add(s.getToStackCode()));
        Map<String, YardStack> byCode = stacks.stream()
                .collect(Collectors.toMap(YardStack::getCode, Function.identity()));
        return involved.stream()
                .map(code -> new AppointmentStackSnapshot(appointment, code, byCode.get(code).getVersion()))
                .toList();
    }

    /**
     * 到场确认：校验预约版本、时间窗与箱的实际位置。出场预约若堆场布局已变化，
     * 旧前置移箱计划不得执行，尝试按当前布局重新规划；重新规划失败则保留预约待人工处理。
     * 按到场业务号幂等。
     */
    @Transactional
    public ArrivalResult confirmArrival(String appointmentNo, ArrivalRequest request) {
        Appointment appointment = appointmentRepository.findByAppointmentNoForUpdate(appointmentNo)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + appointmentNo));
        if (appointment.getStatus() == AppointmentStatus.ARRIVED) {
            if (appointment.getArrivalNo().equals(request.arrivalNo())) {
                return new ArrivalResult(toView(appointment), ArrivalOutcome.CONFIRMED,
                        "重复到场确认，返回原结果");
            }
            throw new DuplicateException("预约已确认到场，到场业务号不一致: " + appointmentNo);
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("预约已取消: " + appointmentNo);
        }
        if (appointment.getPlanVersion() != request.planVersion()) {
            throw new AppointmentConflictException("预约版本不一致：当前版本 "
                    + appointment.getPlanVersion() + "，请求版本 " + request.planVersion());
        }
        if (!appointment.getWindow().contains(Instant.now())) {
            throw new IllegalStateException("当前时间不在预约时间窗内: " + appointmentNo);
        }
        if (appointment.getDirection() == GateDirection.IN) {
            return confirmInboundArrival(appointment, request);
        }
        return confirmOutboundArrival(appointment, request);
    }

    private ArrivalResult confirmInboundArrival(Appointment appointment, ArrivalRequest request) {
        if (containerRepository.findByContainerNo(appointment.getContainerNo()).isPresent()) {
            throw new AppointmentConflictException(
                    "箱 " + appointment.getContainerNo() + " 已在场内，与进场预约冲突");
        }
        SlotReservation slot = slotRepository.findByAppointmentId(appointment.getId())
                .orElseThrow(() -> new IllegalStateException("进场预约缺少堆位预留: " + appointment.getAppointmentNo()));
        if (!slot.isActive()) {
            throw new AppointmentConflictException("预留堆位已释放，预约无法确认: " + appointment.getAppointmentNo());
        }
        appointment.markArrived(request.arrivalNo());
        return new ArrivalResult(AppointmentView.from(appointment, slot), ArrivalOutcome.CONFIRMED,
                "到场确认成功");
    }

    private ArrivalResult confirmOutboundArrival(Appointment appointment, ArrivalRequest request) {
        Container container = containerRepository.findByContainerNo(appointment.getContainerNo()).orElse(null);
        if (container != null && isLayoutIntact(appointment, container)) {
            appointment.markArrived(request.arrivalNo());
            return new ArrivalResult(toView(appointment), ArrivalOutcome.CONFIRMED, "到场确认成功");
        }
        try {
            replan(appointment, container);
        } catch (NotFoundException | IllegalStateException e) {
            appointment.markManualReview("重新规划失败: " + e.getMessage());
            return new ArrivalResult(toView(appointment), ArrivalOutcome.MANUAL_REVIEW,
                    "堆场布局已变化且重新规划失败，预约保留待人工处理");
        }
        appointment.markArrived(request.arrivalNo());
        return new ArrivalResult(toView(appointment), ArrivalOutcome.REPLANNED,
                "堆场布局已变化，已按当前布局重新规划前置移箱（计划版本 " + appointment.getPlanVersion() + "）");
    }

    private boolean isLayoutIntact(Appointment appointment, Container container) {
        if (!container.getStack().getCode().equals(appointment.getTargetStackCode())) {
            return false;
        }
        List<String> codes = appointment.getSnapshots().stream()
                .map(AppointmentStackSnapshot::getStackCode)
                .sorted()
                .toList();
        Map<String, YardStack> locked = stackRepository.findAllByCodeInForUpdate(codes).stream()
                .collect(Collectors.toMap(YardStack::getCode, Function.identity()));
        for (AppointmentStackSnapshot snapshot : appointment.getSnapshots()) {
            YardStack stack = locked.get(snapshot.getStackCode());
            if (stack == null || stack.getVersion() != snapshot.getStackVersion()) {
                return false;
            }
        }
        // 进场预留不改动堆栈版本，仍需校验既有计划的移箱目标容量未被预留占满
        return planStillFeasible(appointment, new ArrayList<>(locked.values()));
    }

    private boolean planStillFeasible(Appointment appointment, List<YardStack> stacks) {
        if (appointment.getPreMoveSteps().isEmpty()) {
            return true;
        }
        Map<String, Container> containers = containerRepository.findAll().stream()
                .collect(Collectors.toMap(Container::getContainerNo, Function.identity()));
        Map<String, long[]> free = freeCapacity(stacks);
        for (PreMoveStep step : appointment.getPreMoveSteps()) {
            Container box = containers.get(step.getContainerNo());
            long[] f = free.get(step.getToStackCode());
            if (box == null || f == null || f[0] < 1 || f[1] < box.getWeight()) {
                return false;
            }
            f[0] -= 1;
            f[1] -= box.getWeight();
        }
        return true;
    }

    private void replan(Appointment appointment, Container container) {
        if (container == null) {
            throw new NotFoundException("箱不在场内: " + appointment.getContainerNo());
        }
        if (!container.getStack().getCode().equals(appointment.getTargetStackCode())) {
            throw new IllegalStateException("箱 " + appointment.getContainerNo()
                    + " 已不在预约声明的堆栈 " + appointment.getTargetStackCode());
        }
        List<YardStack> stacks = stackRepository.findAllByOrderByCodeForUpdate();
        List<PreMoveStep> steps = planPreMoves(appointment, container, stacks);
        List<AppointmentStackSnapshot> snapshots =
                snapshotsOf(appointment, stacks, steps, container.getStack().getCode());
        // 先落库删除旧计划行，再写入新计划，避免 (appointment_id, stack_code) 唯一约束冲突
        appointment.clearPlan();
        entityManager.flush();
        appointment.replacePlan(steps, snapshots);
    }

    /**
     * 取消预约：释放闸口容量与堆位预留。按取消业务号幂等，状态机保证资源只释放一次。
     */
    @Transactional
    public AppointmentView cancel(String appointmentNo, CancelRequest request) {
        Appointment appointment = appointmentRepository.findByAppointmentNoForUpdate(appointmentNo)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + appointmentNo));
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            if (appointment.getCancelNo().equals(request.cancelNo())) {
                return toView(appointment);
            }
            throw new DuplicateException("预约已取消，取消业务号不一致: " + appointmentNo);
        }
        if (appointment.getStatus() == AppointmentStatus.ARRIVED) {
            throw new IllegalStateException("车辆已到场，预约不可取消: " + appointmentNo);
        }
        GateWindow window = windowRepository.findByIdForUpdate(appointment.getWindow().getId())
                .orElseThrow(() -> new NotFoundException("闸口时间窗不存在: " + appointment.getWindow().getId()));
        window.release();
        SlotReservation slot = slotRepository.findByAppointmentId(appointment.getId()).orElse(null);
        if (slot != null) {
            slot.release();
        }
        appointment.markCancelled(request.cancelNo());
        return AppointmentView.from(appointment, slot);
    }

    @Transactional(readOnly = true)
    public AppointmentView getAppointment(String appointmentNo) {
        Appointment appointment = appointmentRepository.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + appointmentNo));
        return toView(appointment);
    }

    @Transactional(readOnly = true)
    public List<AppointmentView.PreMoveStepView> preMoveSteps(String appointmentNo) {
        Appointment appointment = appointmentRepository.findByAppointmentNo(appointmentNo)
                .orElseThrow(() -> new NotFoundException("预约不存在: " + appointmentNo));
        return appointment.getPreMoveSteps().stream()
                .map(s -> new AppointmentView.PreMoveStepView(s.getSeq(), s.getContainerNo(),
                        s.getFromStackCode(), s.getToStackCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OccupancyView> occupancy() {
        Map<String, List<SlotReservation>> reservationsByStack = slotRepository
                .findByActiveSlotKeyIsNotNull().stream()
                .collect(Collectors.groupingBy(SlotReservation::getStackCode));
        return stackRepository.findAllByOrderByCode().stream()
                .map(stack -> {
                    List<SlotReservation> reservations =
                            reservationsByStack.getOrDefault(stack.getCode(), List.of());
                    long reservedWeight = reservations.stream()
                            .mapToLong(SlotReservation::getWeight).sum();
                    List<OccupancyView.ReservedSlotView> slots = reservations.stream()
                            .map(r -> new OccupancyView.ReservedSlotView(r.getTier(),
                                    r.getAppointment().getContainerNo(), r.getWeight(),
                                    r.getAppointment().getAppointmentNo()))
                            .toList();
                    return new OccupancyView(stack.getCode(), stack.getMaxTiers(), stack.getMaxWeight(),
                            stack.getCurrentTiers(), stack.getCurrentWeight(),
                            reservations.size(), reservedWeight,
                            stack.getMaxTiers() - stack.getCurrentTiers() - reservations.size(),
                            stack.getMaxWeight() - stack.getCurrentWeight() - reservedWeight,
                            slots);
                })
                .toList();
    }

    private AppointmentView toView(Appointment appointment) {
        SlotReservation slot = slotRepository.findByAppointmentId(appointment.getId()).orElse(null);
        return AppointmentView.from(appointment, slot);
    }
}
