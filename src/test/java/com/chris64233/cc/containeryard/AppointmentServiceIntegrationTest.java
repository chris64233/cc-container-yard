package com.chris64233.cc.containeryard;

import com.chris64233.cc.containeryard.domain.AppointmentStatus;
import com.chris64233.cc.containeryard.domain.PlanStatus;
import com.chris64233.cc.containeryard.repo.AppointmentRepository;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.GateWindowRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.AppointmentService;
import com.chris64233.cc.containeryard.service.ConflictException;
import com.chris64233.cc.containeryard.service.DuplicateException;
import com.chris64233.cc.containeryard.service.GateWindowService;
import com.chris64233.cc.containeryard.service.PlanService;
import com.chris64233.cc.containeryard.service.YardService;
import com.chris64233.cc.containeryard.service.dto.AppointmentRequest;
import com.chris64233.cc.containeryard.service.dto.AppointmentView;
import com.chris64233.cc.containeryard.service.dto.ArrivalRequest;
import com.chris64233.cc.containeryard.service.dto.ArrivalResult;
import com.chris64233.cc.containeryard.service.dto.CancelRequest;
import com.chris64233.cc.containeryard.service.dto.PlanView;
import com.chris64233.cc.containeryard.service.dto.StackView;
import com.chris64233.cc.containeryard.service.dto.StepRequest;
import com.chris64233.cc.containeryard.service.dto.WindowRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AppointmentServiceIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private GateWindowService windowService;
    @Autowired
    private YardService yardService;
    @Autowired
    private PlanService planService;
    @Autowired
    private GateWindowRepository windowRepository;
    @Autowired
    private YardStackRepository stackRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private MovePlanRepository planRepository;
    @Autowired
    private MoveEventRepository eventRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    private Instant windowStart;
    private Instant windowEnd;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        planRepository.deleteAll();
        appointmentRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();
        windowRepository.deleteAll();

        windowStart = Instant.now().minus(1, ChronoUnit.HOURS);
        windowEnd = Instant.now().plus(1, ChronoUnit.HOURS);

        yardService.createStack("S1", 3, 1000);
        yardService.createStack("S2", 3, 1000);
        yardService.createStack("S3", 1, 1000);
    }

    private Long createWindow(int capacity) {
        return windowService.createWindow(new WindowRequest(windowStart, windowEnd, capacity)).id();
    }

    private AppointmentRequest inbound(String no, String containerNo, Long windowId, String stack, long weight) {
        return new AppointmentRequest(no, containerNo,
                com.chris64233.cc.containeryard.domain.AppointmentDirection.INBOUND,
                "V-" + no, windowId, stack, weight);
    }

    private AppointmentRequest outbound(String no, String containerNo, Long windowId) {
        return new AppointmentRequest(no, containerNo,
                com.chris64233.cc.containeryard.domain.AppointmentDirection.OUTBOUND,
                "V-" + no, windowId, null, 0);
    }

    // --------------------------------------------------------------- 闸口时段

    @Test
    void overlappingWindowsAreRejected() {
        windowService.createWindow(new WindowRequest(windowStart, windowEnd, 2));
        assertThatThrownBy(() -> windowService.createWindow(
                new WindowRequest(windowStart.plus(10, ChronoUnit.MINUTES),
                        windowEnd.plus(10, ChronoUnit.MINUTES), 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重叠");
    }

    @Test
    void invalidWindowRangeIsRejected() {
        assertThatThrownBy(() -> windowService.createWindow(
                new WindowRequest(windowEnd, windowStart, 2)))
                .isInstanceOf(IllegalStateException.class);
    }

    // --------------------------------------------------------------- 预约申报

    @Test
    void duplicateAppointmentNoIsIdempotent() {
        Long windowId = createWindow(5);
        AppointmentView first = appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        AppointmentView second = appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(appointmentRepository.findAll()).hasSize(1);
    }

    @Test
    void oneContainerCanHaveOnlyOneActiveAppointment() {
        Long windowId = createWindow(5);
        yardService.placeContainer("CX", 10, "S2");
        appointmentService.create(outbound("O1", "CX", windowId));

        assertThatThrownBy(() -> appointmentService.create(outbound("O2", "CX", windowId)))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("有效预约");
    }

    @Test
    void inboundRequiresTargetStackAndUnknownContainer() {
        Long windowId = createWindow(5);
        yardService.placeContainer("EXIST", 10, "S2");

        assertThatThrownBy(() -> appointmentService.create(
                new AppointmentRequest("A2", "NEW2",
                        com.chris64233.cc.containeryard.domain.AppointmentDirection.INBOUND,
                        "V", windowId, null, 10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> appointmentService.create(inbound("A3", "EXIST", windowId, "S1", 10)))
                .isInstanceOf(DuplicateException.class);
    }

    @Test
    void outboundRequiresContainerInYard() {
        Long windowId = createWindow(5);
        assertThatThrownBy(() -> appointmentService.create(outbound("O9", "GHOST", windowId)))
                .isInstanceOf(com.chris64233.cc.containeryard.service.NotFoundException.class);
    }

    @Test
    void concurrentCreateForSameContainerAllowsOnlyOneAppointment() throws Exception {
        Long windowId = createWindow(5);
        yardService.placeContainer("CC", 10, "S2");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        try {
            List<Future<Object>> futures = new java.util.ArrayList<>();
            for (String no : List.of("O1", "O2")) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        appointmentService.create(outbound(no, "CC", windowId));
                        success.incrementAndGet();
                    } catch (DuplicateException e) {
                        conflict.incrementAndGet();
                    }
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<Object> f : futures) {
                f.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);
        assertThat(appointmentRepository.findAll()).hasSize(1);
    }

    // --------------------------------------------------------------- 批准（进场）

    @Test
    void approveInboundReservesGateCapacityAndStackSlot() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S2", 40));

        AppointmentView approved = appointmentService.approve("A1");

        assertThat(approved.status()).isEqualTo(AppointmentStatus.APPROVED);
        var window = windowRepository.findById(windowId).orElseThrow();
        assertThat(window.getReserved()).isEqualTo(1);
        var stack = stackRepository.findByCode("S2").orElseThrow();
        assertThat(stack.getReservedTiers()).isEqualTo(1);
        assertThat(stack.getReservedWeight()).isEqualTo(40);
        // 预留尚未落位：箱不存在、实际占用不变
        assertThat(containerRepository.findByContainerNo("NEW1")).isEmpty();
        assertThat(stack.getCurrentTiers()).isZero();
    }

    @Test
    void approveInboundFailsWholeAppointmentWhenGateFull() {
        Long windowId = createWindow(1);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        appointmentService.create(inbound("A2", "NEW2", windowId, "S2", 10));
        appointmentService.approve("A1");

        assertThatThrownBy(() -> appointmentService.approve("A2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("容量不足");

        // 第二笔整体失败：预约仍待批准，未占用任何资源
        assertThat(appointmentService.get("A2").status()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(stackRepository.findByCode("S2").orElseThrow().getReservedTiers()).isZero();
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isEqualTo(1);
    }

    @Test
    void approveInboundFailsWhenStackCannotAcceptHeightOrWeight() {
        Long windowId = createWindow(5);
        // S3 只有 1 层且已放一箱
        yardService.placeContainer("C0", 10, "S3");
        appointmentService.create(inbound("A1", "NEW1", windowId, "S3", 10));

        assertThatThrownBy(() -> appointmentService.approve("A1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("高度或重量不足");
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isZero();
    }

    @Test
    void approveIsIdempotent() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        appointmentService.approve("A1");

        AppointmentView again = appointmentService.approve("A1");

        assertThat(again.status()).isEqualTo(AppointmentStatus.APPROVED);
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isEqualTo(1);
    }

    @Test
    void concurrentApprovalsNeverExceedGateCapacity() throws Exception {
        Long windowId = createWindow(1);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        appointmentService.create(inbound("A2", "NEW2", windowId, "S2", 10));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            List<String> nos = List.of("A1", "A2");
            List<Future<Object>> futures = new java.util.ArrayList<>();
            for (String no : nos) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        appointmentService.approve(no);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<Object> f : futures) {
                f.get();
            }
        } finally {
            executor.shutdownNow();
        }

        long approved = appointmentRepository.findAll().stream()
                .filter(a -> a.getStatus() == AppointmentStatus.APPROVED).count();
        assertThat(approved).isEqualTo(1);
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isEqualTo(1);
        long reservedSlots = stackRepository.findAll().stream()
                .mapToLong(s -> s.getReservedTiers()).sum();
        assertThat(reservedSlots).isEqualTo(1);
    }

    // --------------------------------------------------------------- 批准（出场）

    @Test
    @org.springframework.transaction.annotation.Transactional
    void approveOutboundBuildsPrerequisiteMovePlanForBuriedContainer() {
        Long windowId = createWindow(5);
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
        yardService.placeContainer("C3", 30, "S1");
        appointmentService.create(outbound("O1", "C2", windowId));

        AppointmentView approved = appointmentService.approve("O1");

        assertThat(approved.status()).isEqualTo(AppointmentStatus.APPROVED);
        assertThat(approved.planId()).isNotNull();
        PlanView plan = appointmentService.getPlan("O1");
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).containerNo()).isEqualTo("C3");
        assertThat(plan.status()).isEqualTo(PlanStatus.PENDING);
        assertThat(plan.targetContainerNo()).isEqualTo("C2");
        // 仅生成计划，未发生任何移动
        assertThat(containerRepository.findByContainerNo("C3").orElseThrow().getStack().getCode())
                .isEqualTo("S1");
        assertThat(eventRepository.findAll()).isEmpty();
    }

    @Test
    void approveOutboundForTopContainerHasNoPlan() {
        Long windowId = createWindow(5);
        yardService.placeContainer("C1", 10, "S1");
        appointmentService.create(outbound("O1", "C1", windowId));

        AppointmentView approved = appointmentService.approve("O1");
        assertThat(approved.planId()).isNull();
    }

    @Test
    void approveOutboundFailsWhenNoBufferStackAvailable() {
        Long windowId = createWindow(5);
        // S1 三层放满，S2 三层放满，S3 一层放满，无处安置上方箱
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 10, "S1");
        yardService.placeContainer("C3", 10, "S1");
        yardService.placeContainer("B2a", 10, "S2");
        yardService.placeContainer("B2b", 10, "S2");
        yardService.placeContainer("B2c", 10, "S2");
        yardService.placeContainer("B3", 10, "S3");
        appointmentService.create(outbound("O1", "C2", windowId));

        assertThatThrownBy(() -> appointmentService.approve("O1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("整笔预约失败");
        assertThat(appointmentService.get("O1").status()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(planRepository.findAll()).isEmpty();
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isZero();
    }

    // --------------------------------------------------------------- 到场（进场）

    @Test
    @org.springframework.transaction.annotation.Transactional
    void inboundArrivalConsumesReservationAndPlacesContainer() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S2", 40));
        long version = appointmentService.approve("A1").version();

        ArrivalResult result = appointmentService.arrive(
                new ArrivalRequest("A1", "IN-1", version, Instant.now()));

        assertThat(result.outcome()).isEqualTo(ArrivalResult.OUTCOME_ARRIVED);
        var placed = containerRepository.findByContainerNo("NEW1").orElseThrow();
        assertThat(placed.getStack().getCode()).isEqualTo("S2");
        assertThat(placed.getTier()).isEqualTo(1);
        var stack = stackRepository.findByCode("S2").orElseThrow();
        assertThat(stack.getReservedTiers()).isZero();
        assertThat(stack.getCurrentWeight()).isEqualTo(40);
        var window = windowRepository.findById(windowId).orElseThrow();
        assertThat(window.getServed()).isEqualTo(1);
        assertThat(window.getReserved()).isZero();
        // 到场事件
        assertThat(eventRepository.findAll()).hasSize(1);
        assertThat(eventRepository.findAll().get(0).getRefNo()).isEqualTo("IN-1");
    }

    @Test
    void arrivalRejectsWrongVersion() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        appointmentService.approve("A1");

        assertThatThrownBy(() -> appointmentService.arrive(
                new ArrivalRequest("A1", "IN-1", 999L, Instant.now())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("版本");
    }

    @Test
    void arrivalOutsideWindowIsRejected() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        long version = appointmentService.approve("A1").version();

        assertThatThrownBy(() -> appointmentService.arrive(
                new ArrivalRequest("A1", "IN-1", version, windowEnd.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("时间窗");
        assertThat(appointmentService.get("A1").status()).isEqualTo(AppointmentStatus.APPROVED);
    }

    @Test
    void duplicateArrivalNoIsIdempotent() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        long version = appointmentService.approve("A1").version();
        appointmentService.arrive(new ArrivalRequest("A1", "IN-1", version, Instant.now()));

        ArrivalResult again = appointmentService.arrive(
                new ArrivalRequest("A1", "IN-1", version + 1, Instant.now()));

        assertThat(again.outcome()).isEqualTo(ArrivalResult.OUTCOME_ARRIVED);
        assertThat(containerRepository.findByContainerNo("NEW1")).isPresent();
        assertThat(eventRepository.findAll()).hasSize(1);
    }

    // --------------------------------------------------------------- 到场（出场）

    @Test
    @org.springframework.transaction.annotation.Transactional
    void outboundArrivalExecutesPrerequisitePlanThenRetrievesContainer() {
        Long windowId = createWindow(5);
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
        yardService.placeContainer("C3", 30, "S1");
        appointmentService.create(outbound("O1", "C2", windowId));
        long version = appointmentService.approve("O1").version();

        ArrivalResult result = appointmentService.arrive(
                new ArrivalRequest("O1", "OUT-1", version, Instant.now()));

        assertThat(result.outcome()).isEqualTo(ArrivalResult.OUTCOME_ARRIVED);
        assertThat(result.replanned()).isFalse();
        // C2 提走出场，C3 移到 S2，C1 留在 S1 栈底
        assertThat(containerRepository.findByContainerNo("C2")).isEmpty();
        assertThat(containerRepository.findByContainerNo("C3").orElseThrow().getStack().getCode())
                .isEqualTo("S2");
        assertThat(containerRepository.findByContainerNo("C1").orElseThrow().getStack().getCode())
                .isEqualTo("S1");
        // 1 步前置移箱事件 + 1 步提箱事件
        var events = eventRepository.findAll();
        assertThat(events).hasSize(2);
        assertThat(windowRepository.findById(windowId).orElseThrow().getServed()).isEqualTo(1);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void staleOutboundPlanIsReplannedAtArrival() {
        Long windowId = createWindow(5);
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
        yardService.placeContainer("C3", 30, "S1");
        appointmentService.create(outbound("O1", "C2", windowId));
        AppointmentView approved = appointmentService.approve("O1");
        Long oldPlanId = approved.planId();

        // 批准后堆场变化：C3 被提前移走（使旧计划版本快照陈旧），又新压一箱 C5 到 C2 上方
        planService.execute(planService.createPlan(
                new com.chris64233.cc.containeryard.service.dto.PlanRequest(
                        List.of(new StepRequest("C3", "S2")))).id());
        yardService.placeContainer("C5", 50, "S1");

        ArrivalResult result = appointmentService.arrive(
                new ArrivalRequest("O1", "OUT-1", approved.version(), Instant.now()));

        assertThat(result.outcome()).isEqualTo(ArrivalResult.OUTCOME_ARRIVED);
        assertThat(result.replanned()).isTrue();
        assertThat(containerRepository.findByContainerNo("C2")).isEmpty();
        // 新计划把新压箱 C5 移到 S2（S2 已有 C3，C5 落第二层），C3 留在 S2
        var c5 = containerRepository.findByContainerNo("C5").orElseThrow();
        assertThat(c5.getStack().getCode()).isEqualTo("S2");
        assertThat(c5.getTier()).isEqualTo(2);
        // 原计划标记陈旧，新计划已执行
        assertThat(planRepository.findById(oldPlanId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.STALE_REJECTED);
        assertThat(appointmentService.get("O1").plan().status()).isEqualTo(PlanStatus.EXECUTED);
    }

    @Test
    void replanFailureKeepsAppointmentForManualHandling() {
        // 重建为小缓冲布局：S2/S3 各只有 1 层
        eventRepository.deleteAll();
        planRepository.deleteAll();
        appointmentRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();
        windowRepository.deleteAll();
        yardService.createStack("S1", 3, 1000);
        yardService.createStack("S2", 1, 1000);
        yardService.createStack("S3", 1, 1000);
        Long windowId = createWindow(5);

        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
        yardService.placeContainer("C3", 30, "S1");
        appointmentService.create(outbound("O1", "C2", windowId));
        long version = appointmentService.approve("O1").version();
        // 批准时计划把 C3 送往 S2；批准后把 S2、S3 全部占满，使重排无处安置 C3
        yardService.placeContainer("X2", 10, "S2");
        yardService.placeContainer("X3", 10, "S3");

        ArrivalResult result = appointmentService.arrive(
                new ArrivalRequest("O1", "OUT-1", version, Instant.now()));

        assertThat(result.outcome()).isEqualTo(ArrivalResult.OUTCOME_NEEDS_MANUAL);
        // 关键断言：NEEDS_MANUAL 必须真实提交（独立事务），而非被 rollback-only 回滚
        assertThat(appointmentService.get("O1").status()).isEqualTo(AppointmentStatus.APPROVED);
        assertThat(appointmentService.get("O1").message()).contains("重新规划失败");
        // 没有任何移动发生，资源未结算
        assertThat(containerRepository.findByContainerNo("C2")).isPresent();
        var layout = yardService.layout();
        var s1 = layout.stream().filter(s -> s.code().equals("S1")).findFirst().orElseThrow();
        assertThat(s1.containers()).extracting(StackView.ContainerView::containerNo)
                .containsExactly("C1", "C2", "C3");
        assertThat(eventRepository.findAll()).isEmpty();
        assertThat(windowRepository.findById(windowId).orElseThrow().getServed()).isZero();
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isEqualTo(1);

        // 人工处理：为占位箱 X2 走一笔出场预约把它提走，腾出 S2 缓冲位
        appointmentService.create(outbound("OX2", "X2", windowId));
        long vx2 = appointmentService.approve("OX2").version();
        appointmentService.arrive(new ArrivalRequest("OX2", "OUT-X2", vx2, Instant.now()));

        // 按人工处理后的新版本再次到场，可以成功
        long currentVersion = appointmentService.get("O1").version();
        ArrivalResult retry = appointmentService.arrive(
                new ArrivalRequest("O1", "OUT-1", currentVersion, Instant.now()));
        assertThat(retry.outcome()).isEqualTo(ArrivalResult.OUTCOME_ARRIVED);
        assertThat(containerRepository.findByContainerNo("C2")).isEmpty();
    }

    // --------------------------------------------------------------- 取消

    @Test
    void cancelApprovedInboundReleasesResourcesExactlyOnce() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S2", 40));
        appointmentService.approve("A1");

        AppointmentView cancelled = appointmentService.cancel(new CancelRequest("A1", "CAN-1"));
        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(stackRepository.findByCode("S2").orElseThrow().getReservedTiers()).isZero();
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isZero();

        // 同取消业务号重复：幂等，不重复释放
        AppointmentView again = appointmentService.cancel(new CancelRequest("A1", "CAN-1"));
        assertThat(again.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(stackRepository.findByCode("S2").orElseThrow().getReservedTiers()).isZero();

        // 不同取消业务号：冲突
        assertThatThrownBy(() -> appointmentService.cancel(new CancelRequest("A1", "CAN-2")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void cancelApprovedOutboundCancelsPrerequisitePlan() {
        Long windowId = createWindow(5);
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
        yardService.placeContainer("C3", 30, "S1");
        appointmentService.create(outbound("O1", "C2", windowId));
        appointmentService.approve("O1");
        Long planId = appointmentService.get("O1").planId();

        appointmentService.cancel(new CancelRequest("O1", "CAN-1"));

        assertThat(planRepository.findById(planId).orElseThrow().getStatus())
                .isEqualTo(PlanStatus.CANCELLED);
        assertThat(windowRepository.findById(windowId).orElseThrow().getReserved()).isZero();
        assertThat(containerRepository.findByContainerNo("C3").orElseThrow().getStack().getCode())
                .isEqualTo("S1");
    }

    @Test
    void cancelAfterArrivalIsRejected() {
        Long windowId = createWindow(2);
        appointmentService.create(inbound("A1", "NEW1", windowId, "S1", 10));
        long version = appointmentService.approve("A1").version();
        appointmentService.arrive(new ArrivalRequest("A1", "IN-1", version, Instant.now()));

        assertThatThrownBy(() -> appointmentService.cancel(new CancelRequest("A1", "CAN-1")))
                .isInstanceOf(ConflictException.class);
    }
}
