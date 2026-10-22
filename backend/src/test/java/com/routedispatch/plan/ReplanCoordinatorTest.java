package com.routedispatch.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.routedispatch.plan.PlanRevision.Reason;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReplanCoordinatorTest {

    final ExecutorService executor = Executors.newFixedThreadPool(4);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    /** Records runs; the first run blocks until released so requests pile up behind it. */
    static final class BlockingRunner implements ReplanCoordinator.JobRunner {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final List<String> runs = new CopyOnWriteArrayList<>();

        @Override
        public void run(long planId, PlanningEngine.Job job) {
            runs.add(planId + ":" + job.reason() + ":" + job.detail());
            if (runs.size() == 1) {
                firstStarted.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Test
    void requestsArrivingDuringARunAreCoalescedIntoOneFollowUp() throws Exception {
        BlockingRunner runner = new BlockingRunner();
        ReplanCoordinator coordinator = new ReplanCoordinator(runner, executor);

        coordinator.request(1, new PlanningEngine.Job(Reason.INITIAL, "first"));
        assertThat(runner.firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        coordinator.request(1, new PlanningEngine.Job(Reason.NEW_ORDER, "a"));
        coordinator.request(1, new PlanningEngine.Job(Reason.DRIVER_LATE, "b"));
        coordinator.request(1, new PlanningEngine.Job(Reason.NEW_ORDER, "c"));
        runner.release.countDown();

        awaitIdle(coordinator, 1);
        assertThat(runner.runs).hasSize(2);
        assertThat(runner.runs.get(1)).startsWith("1:NEW_ORDER:").contains("a").contains("b").contains("c");
    }

    @Test
    void fullReoptimizationIsNotDowngradedByALaterReplanRequest() throws Exception {
        BlockingRunner runner = new BlockingRunner();
        ReplanCoordinator coordinator = new ReplanCoordinator(runner, executor);

        coordinator.request(1, new PlanningEngine.Job(Reason.MANUAL, "first"));
        assertThat(runner.firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        coordinator.request(1, new PlanningEngine.Job(Reason.REOPTIMIZE, "full"));
        coordinator.request(1, new PlanningEngine.Job(Reason.NEW_ORDER, "order"));
        runner.release.countDown();

        awaitIdle(coordinator, 1);
        assertThat(runner.runs.get(1)).startsWith("1:REOPTIMIZE:");
    }

    @Test
    void differentPlansRunIndependently() throws Exception {
        BlockingRunner runner = new BlockingRunner();
        ReplanCoordinator coordinator = new ReplanCoordinator(runner, executor);

        coordinator.request(1, new PlanningEngine.Job(Reason.INITIAL, "p1"));
        assertThat(runner.firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        coordinator.request(2, new PlanningEngine.Job(Reason.INITIAL, "p2"));
        awaitIdle(coordinator, 2);
        runner.release.countDown();
        awaitIdle(coordinator, 1);

        assertThat(runner.runs).containsExactlyInAnyOrder("1:INITIAL:p1", "2:INITIAL:p2");
    }

    private static void awaitIdle(ReplanCoordinator coordinator, long planId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (coordinator.isBusy(planId) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(coordinator.isBusy(planId)).isFalse();
    }
}
