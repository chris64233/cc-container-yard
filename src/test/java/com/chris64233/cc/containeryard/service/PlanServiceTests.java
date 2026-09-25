package com.chris64233.cc.containeryard.service;

import com.chris64233.cc.containeryard.domain.PlanStatus;
import com.chris64233.cc.containeryard.domain.YardStack;
import com.chris64233.cc.containeryard.repo.ContainerRepository;
import com.chris64233.cc.containeryard.repo.MoveEventRepository;
import com.chris64233.cc.containeryard.repo.MovePlanRepository;
import com.chris64233.cc.containeryard.repo.YardStackRepository;
import com.chris64233.cc.containeryard.service.YardSimulator.StepCommand;
import com.chris64233.cc.containeryard.web.dto.Views.ExecuteResultView;
import com.chris64233.cc.containeryard.web.dto.Views.PlanView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PlanServiceTests {

    @Autowired
    private PlanService planService;
    @Autowired
    private YardService yardService;
    @Autowired
    private MovePlanRepository planRepository;
    @Autowired
    private MoveEventRepository eventRepository;
    @Autowired
    private YardStackRepository stackRepository;
    @Autowired
    private ContainerRepository containerRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clean() {
        eventRepository.deleteAll();
        planRepository.deleteAll();
        containerRepository.deleteAll();
        stackRepository.deleteAll();
    }

    private void stack(String code, int maxTiers, long maxWeight) {
        yardService.createStack(code, maxTiers, maxWeight);
    }

    private void box(String no, long weight, String stackCode) {
        yardService.registerContainer(no, weight, stackCode);
    }

    private String stackOf(String containerNo) {
        return transactionTemplate.execute(tx ->
                containerRepository.findByContainerNo(containerNo).orElseThrow()
                        .getStack().getCode());
    }

    private int tierOf(String containerNo) {
        return transactionTemplate.execute(tx ->
                containerRepository.findByContainerNo(containerNo).orElseThrow().getTier());
    }

    @Test
    void submitValidPlanSavesSimulationAndSnapshots() {
        stack("A", 3, 1000);
        stack("B", 3, 1000);
        box("C1", 100, "A");
        box("C2", 200, "A");

        PlanView plan = planService.submit(List.of(new StepCommand("C2", "B")));

        assertThat(plan.status()).isEqualTo("PENDING");
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().get(0).fromStackCode()).isEqualTo("A");
        assertThat(plan.steps().get(0).toStackCode()).isEqualTo("B");
        assertThat(plan.steps().get(0).toTier()).isEqualTo(1);
        assertThat(plan.snapshots()).hasSize(2)
                .allSatisfy(s -> assertThat(s.version()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void nonTopContainerIsRejectedAndPlanNotSaved() {
        stack("A", 3, 1000);
        stack("B", 3, 1000);
        box("C1", 100, "A");
        box("C2", 200, "A");

        assertThatThrownBy(() -> planService.submit(List.of(new StepCommand("C1", "B"))))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("不在栈顶");
        assertThat(planRepository.count()).isZero();
    }

    @Test
    void tierLimitIsRejected() {
        stack("A", 3, 1000);
        stack("B", 1, 1000);
        box("C1", 100, "A");
        box("C2", 100, "B");

        assertThatThrownBy(() -> planService.submit(List.of(new StepCommand("C1", "B"))))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("最大层数");
        assertThat(planRepository.count()).isZero();
    }

    @Test
    void weightLimitIsRejected() {
        stack("A", 3, 1000);
        stack("B", 3, 150);
        box("C1", 200, "A");

        assertThatThrownBy(() -> planService.submit(List.of(new StepCommand("C1", "B"))))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("最大总重量");
        assertThat(planRepository.count()).isZero();
    }

    @Test
    void unknownContainerOrStackIsRejected() {
        stack("A", 3, 1000);
        box("C1", 100, "A");

        assertThatThrownBy(() -> planService.submit(List.of(new StepCommand("GHOST", "A"))))
                .isInstanceOf(PlanValidationException.class);
        assertThatThrownBy(() -> planService.submit(List.of(new StepCommand("C1", "NOWHERE"))))
                .isInstanceOf(PlanValidationException.class);
        assertThat(planRepository.count()).isZero();
    }

    @Test
    void multiStepPlanKeepsContainerPositionContinuous() {
        stack("A", 3, 1000);
        stack("B", 3, 1000);
        stack("C", 3, 1000);
        box("C1", 100, "A");
        box("C2", 200, "A");

        PlanView plan = planService.submit(List.of(
                new StepCommand("C2", "B"),
                new StepCommand("C2", "C"),
                new StepCommand("C1", "B")));

        assertThat(plan.steps()).hasSize(3);
        assertThat(plan.steps().get(1).fromStackCode()).isEqualTo("B");
        assertThat(plan.steps().get(1).toStackCode()).isEqualTo("C");
        assertThat(plan.steps().get(2).fromStackCode()).isEqualTo("A");
        assertThat(plan.steps().get(2).toTier()).isEqualTo(1);
    }

    @Test
    void executeAppliesAllChangesAndWritesEvents() {
        stack("A", 3, 1000);
        stack("B", 3, 1000);
        box("C1", 100, "A");
        box("C2", 200, "A");
        PlanView plan = planService.submit(List.of(
                new StepCommand("C2", "B"),
                new StepCommand("C1", "B")));
        long versionA = stackRepository.findByCode("A").orElseThrow().getVersion();

        ExecuteResultView result = planService.execute(plan.id());

        assertThat(result.status()).isEqualTo("EXECUTED");
        assertThat(result.idempotentReplay()).isFalse();
        assertThat(result.events()).hasSize(2);

        assertThat(stackOf("C1")).isEqualTo("B");
        assertThat(tierOf("C1")).isEqualTo(2);
        assertThat(stackOf("C2")).isEqualTo("B");
        assertThat(tierOf("C2")).isEqualTo(1);

        YardStack a = transactionTemplate.execute(tx -> stackRepository.findByCode("A").orElseThrow());
        YardStack b = transactionTemplate.execute(tx -> stackRepository.findByCode("B").orElseThrow());
        assertThat(a.getCurrentTotalWeight()).isZero();
        assertThat(b.getCurrentTotalWeight()).isEqualTo(300);
        assertThat(a.getVersion()).isGreaterThan(versionA);
        assertThat(eventRepository.findByPlanIdOrderBySeq(plan.id())).hasSize(2);
    }

    @Test
    void repeatedExecuteReturnsOriginalResult() {
        stack("A", 3, 1000);
        stack("B", 3, 1000);
        box("C1", 100, "A");
        PlanView plan = planService.submit(List.of(new StepCommand("C1", "B")));

        ExecuteResultView first = planService.execute(plan.id());
        ExecuteResultView second = planService.execute(plan.id());

        assertThat(first.status()).isEqualTo("EXECUTED");
        assertThat(second.status()).isEqualTo("EXECUTED");
        assertThat(second.idempotentReplay()).isTrue();
        assertThat(second.events()).extracting("id")
                .containsExactlyElementsOf(first.events().stream().map(e -> e.id()).toList());
        assertThat(eventRepository.findByPlanIdOrderBySeq(plan.id())).hasSize(1);
    }

    @Test
    void stalePlanIsRejectedWithoutPartialExecution() {
        stack("A", 3, 1000);
        stack("B", 3, 1000);
        stack("C", 3, 1000);
        box("C1", 100, "A");
        box("C2", 200, "A");

        PlanView plan1 = planService.submit(List.of(new StepCommand("C2", "B")));
        PlanView plan2 = planService.submit(List.of(
                new StepCommand("C2", "B"),
                new StepCommand("C1", "C")));

        ExecuteResultView first = planService.execute(plan2.id());
        assertThat(first.status()).isEqualTo("EXECUTED");

        ExecuteResultView stale = planService.execute(plan1.id());
        assertThat(stale.status()).isEqualTo("STALE");
        assertThat(stale.events()).isEmpty();
        assertThat(planService.getPlan(plan1.id()).status()).isEqualTo(PlanStatus.STALE.name());

        assertThat(stackOf("C2")).isEqualTo("B");
        assertThat(tierOf("C2")).isEqualTo(1);
        assertThat(eventRepository.findByPlanIdOrderBySeq(plan1.id())).isEmpty();
        assertThat(stackRepository.findByCode("C").orElseThrow().getCurrentTotalWeight())
                .isEqualTo(100);
    }

    @Test
    void concurrentPlansSharingStackAllowOnlyOneSuccess() throws Exception {
        stack("A", 3, 1000);
        stack("B", 3, 1000);
        stack("C", 3, 1000);
        box("C1", 100, "A");
        PlanView plan1 = planService.submit(List.of(new StepCommand("C1", "B")));
        PlanView plan2 = planService.submit(List.of(new StepCommand("C1", "C")));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Object> result1 = new AtomicReference<>();
        AtomicReference<Object> result2 = new AtomicReference<>();
        Thread t1 = new Thread(() -> runConcurrent(ready, go, () -> planService.execute(plan1.id()), result1));
        Thread t2 = new Thread(() -> runConcurrent(ready, go, () -> planService.execute(plan2.id()), result2));
        t1.start();
        t2.start();
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        t1.join(10_000);
        t2.join(10_000);

        int executed = 0;
        int rejected = 0;
        for (AtomicReference<Object> ref : List.of(result1, result2)) {
            Object value = ref.get();
            if (value instanceof ExecuteResultView view && view.status().equals("EXECUTED")) {
                executed++;
            } else if (value instanceof ExecuteResultView view && view.status().equals("STALE")) {
                rejected++;
            } else if (value instanceof ConcurrencyFailureException
                    || value instanceof jakarta.persistence.OptimisticLockException
                    || value instanceof jakarta.persistence.PessimisticLockException) {
                rejected++;
            }
        }
        assertThat(executed).as("结果: %s / %s", result1.get(), result2.get()).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(1);
        assertThat(List.of("A", "B", "C")).contains(stackOf("C1"));
    }

    private void runConcurrent(CountDownLatch ready, CountDownLatch go,
                               Supplier<ExecuteResultView> action, AtomicReference<Object> sink) {
        try {
            ready.countDown();
            go.await(10, TimeUnit.SECONDS);
            sink.set(action.get());
        } catch (ConcurrencyFailureException ex) {
            sink.set(ex);
        } catch (Exception ex) {
            sink.set(ex);
        }
    }
}
