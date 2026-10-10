package be.reeks;

import ai.timefold.solver.core.api.score.stream.uni.UniConstraintCollector;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Verzamelt alle Slots in een dag-array en geeft een Snapshot door aan de constraints. */
public final class GridCollector implements UniConstraintCollector<Slot, GridCollector.Box, Snapshot> {

    public static final class Box {
        int[] grid = new int[0];
        Params params;
    }

    @Override
    public Supplier<Box> supplier() { return Box::new; }

    @Override
    public BiFunction<Box, Slot, Runnable> accumulator() {
        return (box, slot) -> {
            Params p = slot.getParams();
            if (box.params == null) { box.params = p; box.grid = new int[p.days]; Arrays.fill(box.grid, -1); }
            final int d = slot.getId();
            box.grid[d] = slot.getCell().id;
            return () -> box.grid[d] = -1;
        };
    }

    @Override
    public Function<Box, Snapshot> finisher() {
        return box -> new Snapshot(box.params, box.grid.clone());
    }
}
