package com.chris64233.cc.containeryard;

import com.chris64233.cc.containeryard.domain.PlanStatus;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.PlanService;
import com.chris64233.cc.containeryard.service.PlanValidationException;
import com.chris64233.cc.containeryard.service.YardService;
import com.chris64233.cc.containeryard.service.dto.ExecuteResult;
import com.chris64233.cc.containeryard.service.dto.PlanRequest;
import com.chris64233.cc.containeryard.service.dto.PlanView;
import com.chris64233.cc.containeryard.service.dto.SimulationResult;
import com.chris64233.cc.containeryard.service.dto.StackView;
import com.chris64233.cc.containeryard.service.dto.StepRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PlanServiceIntegrationTest {

    @Autowired
    private YardService yardService;
    @Autowired
    private PlanService planService;
    @Autowired
    private YardStackRepository stackRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private MovePlanRepository planRepository;
    @Autowired
    private MoveEventRepository eventRepository;

    @BeforeEach
    void setUp() {
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

    private PlanRequest plan(StepRequest... steps) {
        return new PlanRequest(List.of(steps));
    }

    private StepRequest step(String containerNo, String target) {
        return new StepRequest(containerNo, target);
    }

    @Test
    void simulateValidPlanTracksContainerAcrossSteps() {
        SimulationResult result = planService.simulate(
                plan(step("C2", "S2"), step("C2", "S3")));

        assertThat(result.valid()).isTrue();
        assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().get(0).fromStack()).isEqualTo("S1");
        assertThat(result.steps().get(1).fromStack()).isEqualTo("S2");
    }

    @Test
    void simulateRejectsNonTopContainer() {
        SimulationResult result = planService.simulate(plan(step("C1", "S2")));

        assertThat(result.valid()).isFalse();
        assertThat(result.steps().get(0).message()).contains("栈顶");
    }

    @Test
    void simulateRejectsCapacityOverflow() {
        yardService.placeContainer("C4", 40, "S3");
        yardService.placeContainer("C5", 1, "S3");
        SimulationResult tierOverflow = planService.simulate(plan(step("C2", "S3")));
        assertThat(tierOverflow.valid()).isFalse();
        assertThat(tierOverflow.steps().get(0).message()).contains("层数超限");

        yardService.createStack("S4", 3, 35);
        yardService.placeContainer("C6", 30, "S4");
        SimulationResult weightOverflow = planService.simulate(plan(step("C2", "S4")));
        assertThat(weightOverflow.valid()).isFalse();
        assertThat(weightOverflow.steps().get(0).message()).contains("重量超限");
    }

    @Test
    void invalidPlanIsNotSaved() {
        assertThatThrownBy(() -> planService.createPlan(plan(step("C1", "S2"))))
                .isInstanceOf(PlanValidationException.class);
        assertThat(planRepository.findAll()).isEmpty();
    }

    @Test
    void createPlanSavesStepsAndVersionSnapshots() {
        PlanView plan = planService.createPlan(plan(step("C2", "S3"), step("C3", "S1")));

        assertThat(plan.status()).isEqualTo(PlanStatus.PENDING);
        assertThat(plan.steps()).hasSize(2);
        assertThat(plan.snapshots())
                .extracting(PlanView.SnapshotView::stackCode)
                .containsExactlyInAnyOrder("S1", "S2", "S3");
        long s1Version = stackRepository.findByCode("S1").orElseThrow().getVersion();
        assertThat(plan.snapshots()).anySatisfy(s -> {
            if (s.stackCode().equals("S1")) {
                assertThat(s.version()).isEqualTo(s1Version);
            }
        });
    }

    @Test
    @Transactional
    void executeAppliesAllStepsAndWritesImmutableEvents() {
        PlanView plan = planService.createPlan(plan(step("C2", "S2"), step("C2", "S3")));

        ExecuteResult result = planService.execute(plan.id());

        assertThat(result.status()).isEqualTo(PlanStatus.EXECUTED);
        assertThat(result.events()).hasSize(2);
        assertThat(result.events().get(1).fromStack()).isEqualTo("S2");
        assertThat(result.events().get(1).toStack()).isEqualTo("S3");

        var c2 = containerRepository.findByContainerNo("C2").orElseThrow();
        assertThat(c2.getStack().getCode()).isEqualTo("S3");
        assertThat(c2.getTier()).isEqualTo(1);

        var s1 = stackRepository.findByCode("S1").orElseThrow();
        var s2 = stackRepository.findByCode("S2").orElseThrow();
        var s3 = stackRepository.findByCode("S3").orElseThrow();
        assertThat(s1.getCurrentTiers()).isEqualTo(1);
        assertThat(s1.getCurrentWeight()).isEqualTo(10);
        assertThat(s2.getCurrentTiers()).isEqualTo(1);
        assertThat(s2.getCurrentWeight()).isEqualTo(30);
        assertThat(s3.getCurrentTiers()).isEqualTo(1);
        assertThat(s3.getCurrentWeight()).isEqualTo(20);
    }

    @Test
    void repeatedExecuteReturnsOriginalResultWithoutDuplicatingEvents() {
        PlanView plan = planService.createPlan(plan(step("C2", "S2")));
        ExecuteResult first = planService.execute(plan.id());

        ExecuteResult second = planService.execute(plan.id());

        assertThat(second.status()).isEqualTo(PlanStatus.EXECUTED);
        assertThat(second.events()).hasSize(1);
        assertThat(second.events().get(0).id()).isEqualTo(first.events().get(0).id());
        assertThat(eventRepository.findByPlanIdOrderBySeq(plan.id())).hasSize(1);
    }

    @Test
    @Transactional
    void stalePlanIsRejectedWithoutPartialExecution() {
        PlanView planA = planService.createPlan(plan(step("C2", "S3")));
        PlanView planB = planService.createPlan(plan(step("C2", "S2")));
        planService.execute(planB.id());

        ExecuteResult stale = planService.execute(planA.id());

        assertThat(stale.status()).isEqualTo(PlanStatus.STALE_REJECTED);
        assertThat(stale.message()).contains("S1");
        assertThat(eventRepository.findByPlanIdOrderBySeq(planA.id())).isEmpty();
        var c2 = containerRepository.findByContainerNo("C2").orElseThrow();
        assertThat(c2.getStack().getCode()).isEqualTo("S2");

        ExecuteResult again = planService.execute(planA.id());
        assertThat(again.status()).isEqualTo(PlanStatus.STALE_REJECTED);
        assertThat(again.message()).isEqualTo(stale.message());
    }

    @Test
    void concurrentPlansSharingStacksAllowAtMostOneSuccess() throws Exception {
        PlanView planA = planService.createPlan(plan(step("C2", "S2")));
        PlanView planB = planService.createPlan(plan(step("C3", "S1")));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<ExecuteResult> fa = executor.submit(() -> {
                ready.countDown();
                go.await();
                return planService.execute(planA.id());
            });
            Future<ExecuteResult> fb = executor.submit(() -> {
                ready.countDown();
                go.await();
                return planService.execute(planB.id());
            });
            ready.await();
            go.countDown();

            List<ExecuteResult> results = List.of(fa.get(), fb.get());
            assertThat(results.stream().filter(r -> r.status() == PlanStatus.EXECUTED).count())
                    .isEqualTo(1);
            assertThat(results.stream().filter(r -> r.status() == PlanStatus.STALE_REJECTED).count())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(eventRepository.findAll()).hasSize(1);
        long totalWeight = stackRepository.findAll().stream()
                .mapToLong(s -> s.getCurrentWeight()).sum();
        assertThat(totalWeight).isEqualTo(60);
    }

    @Test
    void layoutReflectsCurrentPositionsAndVersions() {
        PlanView plan = planService.createPlan(plan(step("C2", "S2")));
        long versionBefore = stackRepository.findByCode("S1").orElseThrow().getVersion();
        planService.execute(plan.id());

        List<StackView> layout = yardService.layout();

        StackView s1 = layout.stream().filter(s -> s.code().equals("S1")).findFirst().orElseThrow();
        StackView s2 = layout.stream().filter(s -> s.code().equals("S2")).findFirst().orElseThrow();
        assertThat(s1.containers()).extracting(StackView.ContainerView::containerNo)
                .containsExactly("C1");
        assertThat(s2.containers()).extracting(StackView.ContainerView::containerNo)
                .containsExactly("C3", "C2");
        assertThat(s2.currentWeight()).isEqualTo(50);
        assertThat(s1.version()).isGreaterThan(versionBefore);
    }
}
