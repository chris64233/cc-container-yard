package com.chris64233.cc.containeryard;

import com.chris64233.cc.containeryard.domain.AppointmentStatus;
import com.chris64233.cc.containeryard.domain.GateDirection;
import com.chris64233.cc.containeryard.repo.AppointmentRepository;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.GateWindowRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.SlotReservationRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.AppointmentConflictException;
import com.chris64233.cc.containeryard.service.AppointmentService;
import com.chris64233.cc.containeryard.service.DuplicateException;
import com.chris64233.cc.containeryard.service.NotFoundException;
import com.chris64233.cc.containeryard.service.PlanService;
import com.chris64233.cc.containeryard.service.YardService;
import com.chris64233.cc.containeryard.service.dto.AppointmentRequest;
import com.chris64233.cc.containeryard.service.dto.AppointmentResult;
import com.chris64233.cc.containeryard.service.dto.AppointmentView;
import com.chris64233.cc.containeryard.service.dto.ArrivalOutcome;
import com.chris64233.cc.containeryard.service.dto.ArrivalRequest;
import com.chris64233.cc.containeryard.service.dto.ArrivalResult;
import com.chris64233.cc.containeryard.service.dto.CancelRequest;
import com.chris64233.cc.containeryard.service.dto.OccupancyView;
import com.chris64233.cc.containeryard.service.dto.PlanRequest;
import com.chris64233.cc.containeryard.service.dto.StepRequest;
import com.chris64233.cc.containeryard.service.dto.WindowRequest;
import com.chris64233.cc.containeryard.service.dto.WindowView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AppointmentServiceIntegrationTest {

    @Autowired
    private YardService yardService;
    @Autowired
    private PlanService planService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private YardStackRepository stackRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private MovePlanRepository planRepository;
    @Autowired
    private MoveEventRepository eventRepository;
    @Autowired
    private GateWindowRepository windowRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private SlotReservationRepository slotRepository;

    @BeforeEach
    void setUp() {
        slotRepository.deleteAll();
        appointmentRepository.deleteAll();
        windowRepository.deleteAll();
        eventRepository.deleteAll();
        planRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();

        yardService.createStack("S1", 3, 100);
        yardService.createStack("S2", 3, 100);
        yardService.createStack("S3", 2, 50);
        yardService.placeContainer("C1", 10, "S1");
        yardService.placeContainer("C2", 20, "S1");
        yardService.placeContainer("C3", 30, "S2");
    }

    private WindowView currentWindow(int capacity) {
        return appointmentService.createWindow(new WindowRequest(
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), capacity));
    }

    private WindowView futureWindow(int capacity) {
        return appointmentService.createWindow(new WindowRequest(
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), capacity));
    }

    private AppointmentRequest inRequest(String no, String containerNo, long weight,
                                         Long windowId, String stack) {
        return new AppointmentRequest(no, containerNo, GateDirection.IN,
                "TRUCK-" + containerNo, windowId, stack, weight);
    }

    private AppointmentRequest outRequest(String no, String containerNo, Long windowId, String stack) {
        return new AppointmentRequest(no, containerNo, GateDirection.OUT,
                "TRUCK-" + containerNo, windowId, stack, null);
    }

    private OccupancyView occupancyOf(String stackCode) {
        return appointmentService.occupancy().stream()
                .filter(o -> o.stackCode().equals(stackCode))
                .findFirst().orElseThrow();
    }

    @Test
    void inboundBookingReservesWindowCapacityAndSlot() {
        WindowView window = currentWindow(2);

        AppointmentResult result = appointmentService.book(inRequest("A-1", "C9", 40, window.id(), "S1"));

        assertThat(result.created()).isTrue();
        AppointmentView view = result.view();
        assertThat(view.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(view.slot().stackCode()).isEqualTo("S1");
        assertThat(view.slot().tier()).isEqualTo(3);
        assertThat(view.slot().weight()).isEqualTo(40);
        assertThat(view.slot().active()).isTrue();

        WindowView booked = appointmentService.schedule().get(0);
        assertThat(booked.bookedCount()).isEqualTo(1);
        assertThat(booked.remaining()).isEqualTo(1);

        OccupancyView s1 = occupancyOf("S1");
        assertThat(s1.reservedTiers()).isEqualTo(1);
        assertThat(s1.reservedWeight()).isEqualTo(40);
        assertThat(s1.availableTiers()).isEqualTo(0);
        assertThat(s1.reservedSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.containerNo()).isEqualTo("C9");
            assertThat(slot.appointmentNo()).isEqualTo("A-1");
            assertThat(slot.tier()).isEqualTo(3);
        });
    }

    @Test
    void bookingIsIdempotentByAppointmentNo() {
        WindowView window = currentWindow(2);
        AppointmentRequest request = inRequest("A-1", "C9", 40, window.id(), "S1");

        AppointmentResult first = appointmentService.book(request);
        AppointmentResult replay = appointmentService.book(request);

        assertThat(replay.created()).isFalse();
        assertThat(replay.view().id()).isEqualTo(first.view().id());
        assertThat(appointmentRepository.findAll()).hasSize(1);
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(1);
        assertThat(slotRepository.findAll()).hasSize(1);
    }

    @Test
    void bookingFailsWhenWindowIsFullAndReservesNothing() {
        WindowView window = currentWindow(1);
        appointmentService.book(inRequest("A-1", "C8", 10, window.id(), "S1"));

        assertThatThrownBy(() -> appointmentService.book(inRequest("A-2", "C9", 10, window.id(), "S2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("容量已满");

        assertThat(appointmentRepository.findAll()).hasSize(1);
        assertThat(slotRepository.findAll()).hasSize(1);
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(1);
    }

    @Test
    void inboundBookingValidatesWeightAndTierLimitsIncludingExistingReservations() {
        WindowView window = currentWindow(10);

        assertThatThrownBy(() -> appointmentService.book(inRequest("A-W", "C9", 60, window.id(), "S3")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重量超限");

        appointmentService.book(inRequest("A-1", "C9", 10, window.id(), "S3"));
        appointmentService.book(inRequest("A-2", "C10", 10, window.id(), "S3"));
        assertThatThrownBy(() -> appointmentService.book(inRequest("A-3", "C11", 10, window.id(), "S3")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("层数不足");

        OccupancyView s3 = occupancyOf("S3");
        assertThat(s3.reservedTiers()).isEqualTo(2);
        assertThat(s3.availableTiers()).isEqualTo(0);
        assertThat(appointmentRepository.findAll()).hasSize(2);
    }

    @Test
    void inboundBookingRequiresWeightAndContainerOutsideYard() {
        WindowView window = currentWindow(5);

        assertThatThrownBy(() -> appointmentService.book(
                new AppointmentRequest("A-1", "C9", GateDirection.IN, "T1", window.id(), "S1", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("箱重");
        assertThatThrownBy(() -> appointmentService.book(inRequest("A-2", "C1", 10, window.id(), "S2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已在场内");
        assertThatThrownBy(() -> appointmentService.book(inRequest("A-3", "C9", 10, window.id(), "SX")))
                .isInstanceOf(NotFoundException.class);
        assertThat(appointmentRepository.findAll()).isEmpty();
    }

    @Test
    void oneActiveAppointmentPerContainer() {
        WindowView window = currentWindow(5);
        appointmentService.book(outRequest("A-1", "C2", window.id(), "S1"));

        assertThatThrownBy(() -> appointmentService.book(outRequest("A-2", "C2", window.id(), "S1")))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("已存在有效预约");

        appointmentService.cancel("A-1", new CancelRequest("X-1"));
        AppointmentResult rebooked = appointmentService.book(outRequest("A-3", "C2", window.id(), "S1"));
        assertThat(rebooked.created()).isTrue();
    }

    @Test
    void outboundBookingGeneratesPreMoveStepsTopFirst() {
        WindowView window = currentWindow(5);

        AppointmentView buried = appointmentService.book(outRequest("A-1", "C1", window.id(), "S1")).view();

        assertThat(buried.planVersion()).isEqualTo(1);
        assertThat(buried.preMoveSteps()).hasSize(1);
        assertThat(buried.preMoveSteps().get(0).containerNo()).isEqualTo("C2");
        assertThat(buried.preMoveSteps().get(0).fromStack()).isEqualTo("S1");
        assertThat(buried.preMoveSteps().get(0).toStack()).isEqualTo("S2");
        assertThat(buried.layoutSnapshots())
                .extracting(AppointmentView.SnapshotView::stackCode)
                .containsExactlyInAnyOrder("S1", "S2");

        AppointmentView top = appointmentService.book(outRequest("A-2", "C2", window.id(), "S1")).view();
        assertThat(top.preMoveSteps()).isEmpty();
        assertThat(top.layoutSnapshots())
                .extracting(AppointmentView.SnapshotView::stackCode)
                .containsExactly("S1");
    }

    @Test
    void outboundBookingFailsWhenNoCapacityForBlockingContainers() {
        yardService.placeContainer("C4", 30, "S2");
        yardService.placeContainer("C5", 40, "S2");
        yardService.placeContainer("C6", 40, "S3");
        yardService.placeContainer("C7", 1, "S3");
        WindowView window = currentWindow(5);

        assertThatThrownBy(() -> appointmentService.book(outRequest("A-1", "C1", window.id(), "S1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("前置移箱");

        assertThat(appointmentRepository.findAll()).isEmpty();
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(0);
    }

    @Test
    void outboundBookingValidatesContainerPosition() {
        WindowView window = currentWindow(5);

        assertThatThrownBy(() -> appointmentService.book(outRequest("A-1", "C1", window.id(), "S2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不一致");
        assertThatThrownBy(() -> appointmentService.book(outRequest("A-2", "CX", window.id(), "S1")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void arrivalConfirmsWithinWindowAndIsIdempotent() {
        WindowView window = currentWindow(2);
        appointmentService.book(inRequest("A-1", "C9", 40, window.id(), "S1"));

        ArrivalResult first = appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 0));
        assertThat(first.outcome()).isEqualTo(ArrivalOutcome.CONFIRMED);
        assertThat(first.appointment().status()).isEqualTo(AppointmentStatus.ARRIVED);

        ArrivalResult replay = appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 0));
        assertThat(replay.outcome()).isEqualTo(ArrivalOutcome.CONFIRMED);
        assertThat(replay.appointment().arrivedAt()).isEqualTo(first.appointment().arrivedAt());

        assertThatThrownBy(() -> appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-2", 0)))
                .isInstanceOf(DuplicateException.class);
    }

    @Test
    void arrivalChecksPlanVersionAndTimeWindow() {
        WindowView window = currentWindow(5);
        appointmentService.book(outRequest("A-1", "C2", window.id(), "S1"));

        assertThatThrownBy(() -> appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 0)))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("版本不一致");

        WindowView future = futureWindow(5);
        appointmentService.book(outRequest("A-2", "C3", future.id(), "S2"));
        assertThatThrownBy(() -> appointmentService.confirmArrival("A-2", new ArrivalRequest("ARR-2", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("时间窗");

        appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 1));
        assertThatThrownBy(() -> appointmentService.cancel("A-1", new CancelRequest("X-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可取消");
    }

    @Test
    void outboundArrivalWithIntactLayoutKeepsOriginalPlan() {
        WindowView window = currentWindow(5);
        appointmentService.book(outRequest("A-1", "C1", window.id(), "S1"));

        ArrivalResult result = appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 1));

        assertThat(result.outcome()).isEqualTo(ArrivalOutcome.CONFIRMED);
        assertThat(result.appointment().planVersion()).isEqualTo(1);
        assertThat(result.appointment().preMoveSteps()).hasSize(1);
        assertThat(result.appointment().preMoveSteps().get(0).containerNo()).isEqualTo("C2");
    }

    @Test
    @Transactional
    void outboundArrivalReplansWhenLayoutChanged() {
        WindowView window = currentWindow(5);
        appointmentService.book(outRequest("A-1", "C1", window.id(), "S1"));

        // 布局变化：把 C3 从 S2 移到 S1，S1/S2 版本号随之变化
        var plan = planService.createPlan(new PlanRequest(List.of(new StepRequest("C3", "S1"))));
        planService.execute(plan.id());

        ArrivalResult result = appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 1));

        assertThat(result.outcome()).isEqualTo(ArrivalOutcome.REPLANNED);
        assertThat(result.appointment().status()).isEqualTo(AppointmentStatus.ARRIVED);
        assertThat(result.appointment().planVersion()).isEqualTo(2);
        assertThat(result.appointment().preMoveSteps()).hasSize(2);
        assertThat(result.appointment().preMoveSteps().get(0).containerNo()).isEqualTo("C3");
        assertThat(result.appointment().preMoveSteps().get(0).toStack()).isEqualTo("S2");
        assertThat(result.appointment().preMoveSteps().get(1).containerNo()).isEqualTo("C2");
        // 旧计划未执行：C2、C3 仍在 S1
        assertThat(containerRepository.findByContainerNo("C2").orElseThrow().getStack().getCode()).isEqualTo("S1");
        assertThat(containerRepository.findByContainerNo("C3").orElseThrow().getStack().getCode()).isEqualTo("S1");
    }

    @Test
    void outboundArrivalKeepsAppointmentForManualReviewWhenReplanFails() {
        WindowView window = currentWindow(5);
        appointmentService.book(outRequest("A-1", "C1", window.id(), "S1"));

        // 布局变化且占满所有可移箱堆位：S2、S3 全部放满
        yardService.placeContainer("C4", 30, "S2");
        yardService.placeContainer("C5", 40, "S2");
        yardService.placeContainer("C6", 40, "S3");
        yardService.placeContainer("C7", 1, "S3");

        ArrivalResult result = appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 1));

        assertThat(result.outcome()).isEqualTo(ArrivalOutcome.MANUAL_REVIEW);
        AppointmentView view = appointmentService.getAppointment("A-1");
        assertThat(view.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(view.manualReview()).isTrue();
        assertThat(view.resultMessage()).contains("重新规划失败");
        // 资源未释放，等待人工处理
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(1);

        ArrivalResult retry = appointmentService.confirmArrival("A-1", new ArrivalRequest("ARR-1", 1));
        assertThat(retry.outcome()).isEqualTo(ArrivalOutcome.MANUAL_REVIEW);

        appointmentService.cancel("A-1", new CancelRequest("X-1"));
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(0);
    }

    @Test
    void cancelReleasesResourcesExactlyOnce() {
        WindowView window = currentWindow(2);
        appointmentService.book(inRequest("A-1", "C9", 40, window.id(), "S1"));
        appointmentService.book(inRequest("A-2", "C10", 10, window.id(), "S2"));

        AppointmentView cancelled = appointmentService.cancel("A-1", new CancelRequest("X-1"));

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(1);
        assertThat(occupancyOf("S1").reservedTiers()).isEqualTo(0);

        AppointmentView replay = appointmentService.cancel("A-1", new CancelRequest("X-1"));
        assertThat(replay.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(1);

        assertThatThrownBy(() -> appointmentService.cancel("A-1", new CancelRequest("X-2")))
                .isInstanceOf(DuplicateException.class);

        // 取消后箱与堆位均可再次预约
        AppointmentResult rebooked = appointmentService.book(inRequest("A-3", "C9", 40, window.id(), "S1"));
        assertThat(rebooked.view().slot().tier()).isEqualTo(3);
    }

    @Test
    void concurrentBookingsNeverExceedWindowCapacity() throws Exception {
        WindowView window = currentWindow(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<AppointmentResult> fa = executor.submit(() -> {
                ready.countDown();
                go.await();
                return appointmentService.book(inRequest("A-1", "C8", 10, window.id(), "S1"));
            });
            Future<AppointmentResult> fb = executor.submit(() -> {
                ready.countDown();
                go.await();
                return appointmentService.book(inRequest("A-2", "C9", 10, window.id(), "S2"));
            });
            ready.await();
            go.countDown();

            int successes = 0;
            int failures = 0;
            for (Future<AppointmentResult> f : List.of(fa, fb)) {
                try {
                    f.get();
                    successes++;
                } catch (Exception e) {
                    failures++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(failures).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(1);
        assertThat(appointmentRepository.findAll()).hasSize(1);
        assertThat(slotRepository.findAll()).hasSize(1);
    }

    @Test
    void concurrentBookingsNeverDoubleBookSameContainer() throws Exception {
        WindowView window = currentWindow(5);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<AppointmentResult> fa = executor.submit(() -> {
                ready.countDown();
                go.await();
                return appointmentService.book(outRequest("A-1", "C3", window.id(), "S2"));
            });
            Future<AppointmentResult> fb = executor.submit(() -> {
                ready.countDown();
                go.await();
                return appointmentService.book(outRequest("A-2", "C3", window.id(), "S2"));
            });
            ready.await();
            go.countDown();

            int successes = 0;
            for (Future<AppointmentResult> f : List.of(fa, fb)) {
                try {
                    f.get();
                    successes++;
                } catch (Exception ignored) {
                    // 另一线程应因箱已存在有效预约而失败
                }
            }
            assertThat(successes).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(appointmentRepository.findAll()).hasSize(1);
        assertThat(appointmentService.schedule().get(0).bookedCount()).isEqualTo(1);
    }
}
