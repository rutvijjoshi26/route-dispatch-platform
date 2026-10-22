package com.routedispatch.optimization;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchType;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.routedispatch.optimization.baseline.NearestNeighborPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Runs Timefold. The same model serves both solve modes; only the time budget differs. A fresh day is seeded
 * with a nearest-neighbor construction before local search. Re-plans are warm
 * starts: visits keep their current vehicle and order, pinned prefixes cannot move, and new orders start
 * unassigned so the construction heuristic inserts them before local search polishes the routes.
 */
public class RouteOptimizer {

    private final SolverFactory<RoutingSolution> initialFactory;
    private final SolverFactory<RoutingSolution> replanFactory;
    private final SolutionManager<RoutingSolution, HardMediumSoftScore> solutionManager;

    public RouteOptimizer(SolverSettings initial, SolverSettings replan) {
        this.initialFactory = SolverFactory.create(config(initial));
        this.replanFactory = SolverFactory.create(config(replan));
        this.solutionManager = SolutionManager.create(initialFactory);
    }

    private static SolverConfig config(SolverSettings settings) {
        return new SolverConfig()
                .withSolutionClass(RoutingSolution.class)
                .withEntityClasses(VehicleRoute.class, Visit.class)
                .withConstraintProviderClass(RoutingConstraintProvider.class)
                .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
                // Construction inserts unassigned orders (new orders on re-plan); tabu search then improves the
                // routes. On the 200-stop benchmark tabu converged far faster than the default late acceptance.
                .withPhases(new ConstructionHeuristicPhaseConfig(),
                        new LocalSearchPhaseConfig().withLocalSearchType(LocalSearchType.TABU_SEARCH))
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(settings.spentLimit())
                        .withUnimprovedSpentLimit(settings.unimprovedSpentLimit()));
    }

    public OptimizationResult optimize(RoutingSolution problem, SolveMode mode, Consumer<ScoreUpdate> progress) {
        if (mode == SolveMode.INITIAL && problem.getVehicles().stream().allMatch(v -> v.getVisits().isEmpty())) {
            // Timefold's generic construction heuristic is slow to reach good routes at this size without the
            // enterprise nearby selection; a greedy nearest-neighbor seed lets local search start from a sane plan.
            new NearestNeighborPlanner().plan(problem);
        }
        Solver<RoutingSolution> solver = (mode == SolveMode.INITIAL ? initialFactory : replanFactory).buildSolver();
        List<ScoreUpdate> timeline = new CopyOnWriteArrayList<>();
        long start = System.nanoTime();
        solver.addEventListener(event -> {
            ScoreUpdate update = new ScoreUpdate((System.nanoTime() - start) / 1_000_000, (HardMediumSoftScore) event.getNewBestScore());
            timeline.add(update);
            progress.accept(update);
        });
        RoutingSolution best = solver.solve(problem);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return new OptimizationResult(best, elapsed, new ArrayList<>(timeline));
    }

    /** Computes arrival times and the score of a solution without changing any assignment. */
    public RoutingSolution evaluate(RoutingSolution solution) {
        solutionManager.update(solution);
        return solution;
    }
}
