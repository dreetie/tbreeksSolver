package be.reeks;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Eén optimalisatierun op een achtergrondthread. */
public final class SolveJob {

    public enum State { RUNNING, DONE, ERROR }

    public final Series series;
    public final int[] scores;           // belangrijkheid per zachte regel, 0..10
    public final int seconds;
    public final long seed;
    public final int[] startGrid;
    public volatile State state = State.RUNNING;
    public volatile String error;
    public volatile int[] bestGrid;
    public volatile long bestHard, bestSoft;
    public volatile long moves;
    public final long startedAt = System.currentTimeMillis();

    public SolveJob(Series series, int[] scores, int seconds, long seed, int[] startGrid) {
        this.series = series; this.scores = scores; this.seconds = seconds; this.seed = seed;
        this.startGrid = startGrid.clone();
        this.bestGrid = startGrid.clone();
    }

    public double elapsedSeconds() { return (System.currentTimeMillis() - startedAt) / 1000.0; }

    public void start() {
        Thread t = new Thread(this::run, "solver");
        t.setDaemon(true);
        t.start();
    }

    public void stop() { for (Solver<Roster> s : solvers) s.terminateEarly(); }

    private final java.util.List<Solver<Roster>> solvers = new java.util.concurrent.CopyOnWriteArrayList<>();
    public volatile int threads = 1;
    public volatile boolean forbidHalf = true;
    public volatile boolean forbidConsec = true;
    private HardSoftLongScore globalBest;

    private void run() {
        try {
            int n = Math.max(1, threads);
            Thread[] ts = new Thread[n];
            final Throwable[] failure = new Throwable[1];
            for (int i = 0; i < n; i++) {
                final long sd = seed + 7919L * i;
                ts[i] = new Thread(() -> {
                    try { runOne(sd); } catch (Throwable e) { failure[0] = e; stop(); }
                }, "solver-" + i);
                ts[i].setDaemon(true);
                ts[i].start();
            }
            for (Thread t : ts) t.join();
            if (failure[0] != null) throw failure[0];
            state = State.DONE;
        } catch (Throwable e) {
            e.printStackTrace();
            error = String.valueOf(e);
            state = State.ERROR;
        }
    }

    /** Eén zoekrun: drie fasen (warm -> koud), elke fase start vanaf het beste resultaat van de vorige. */
    private void runOne(long sd) {
        Roster problem = buildProblem();
        double[] share = {0.55, 0.28, 0.17};
        String[] temp = {"0hard/5000000soft", "0hard/1500000soft", "0hard/400000soft"};
        SolverConfig cfg = new SolverConfig()
                .withSolutionClass(Roster.class)
                .withEntityClasses(Slot.class)
                .withConstraintProviderClass(RosterConstraints.class)
                .withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
                .withRandomSeed(sd)
                .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(seconds)));
        java.util.List<PhaseConfig> phases = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long ms = Math.max(500, Math.round(seconds * 1000.0 * share[i]));
            phases.add(new LocalSearchPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMillis(ms)))
                    .withMoveSelectorConfig(new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(ReeksMoves.class))
                    .withAcceptorConfig(new LocalSearchAcceptorConfig().withSimulatedAnnealingStartingTemperature(temp[i]))
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1)));
        }
        cfg.setPhaseConfigList(phases);
        Solver<Roster> s = SolverFactory.<Roster>create(cfg).buildSolver();
        solvers.add(s);
        s.addEventListener(ev -> publish(ev.getNewBestSolution()));
        Roster best = s.solve(problem);
        publish(best);
    }

    private synchronized void publish(Roster r) {
        HardSoftLongScore sc = r.getScore();
        if (sc == null || (globalBest != null && sc.compareTo(globalBest) <= 0)) return;
        globalBest = sc;
        int[] g = new int[series.weeks * 7];
        for (Slot sl : r.getSlots()) g[sl.getId()] = sl.getCell().id;
        bestGrid = g;
        bestHard = sc.hardScore();
        bestSoft = sc.softScore();
    }

    private Roster buildProblem() {
        List<Slot> slots = new ArrayList<>();
        for (int w = 0; w < series.weeks; w++) {
            for (int c = 0; c < 7; c++) {
                Cell cell = series.cells.get(startGrid[w * 7 + c]);
                slots.add(new Slot(w, c, series.byCol.get(c), series.params, cell));
            }
        }
        Map<String, HardSoftLongScore> w = new HashMap<>();
        for (int i = 0; i < Rules.Z; i++) w.put(Rules.SOFT[i], HardSoftLongScore.ofSoft(Rules.weightFor(i, scores[i])));
        w.put(Rules.HARD[12], forbidHalf ? HardSoftLongScore.ofHard(1) : HardSoftLongScore.ZERO);
        w.put(Rules.HARD[13], forbidConsec ? HardSoftLongScore.ofHard(1) : HardSoftLongScore.ZERO);
        ConstraintWeightOverrides<HardSoftLongScore> overrides = ConstraintWeightOverrides.of(w);
        return new Roster(series.cells, slots, overrides);
    }
}
