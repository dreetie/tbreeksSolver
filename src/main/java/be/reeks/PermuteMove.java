package be.reeks;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.AbstractMove;
import ai.timefold.solver.core.impl.heuristic.move.Move;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Geeft een reeks slots een nieuwe cel (altijd een permutatie binnen dezelfde kolom). */
public final class PermuteMove extends AbstractMove<Roster> {

    private final Slot[] slots;
    private final Cell[] newCells;
    private final String type;

    public PermuteMove(String type, Slot[] slots, Cell[] newCells) {
        this.type = type; this.slots = slots; this.newCells = newCells;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<Roster> sd) {
        for (int i = 0; i < slots.length; i++) if (slots[i].getCell() != newCells[i]) return true;
        return false;
    }

    @Override
    protected Move<Roster> createUndoMove(ScoreDirector<Roster> sd) {
        Cell[] old = new Cell[slots.length];
        for (int i = 0; i < slots.length; i++) old[i] = slots[i].getCell();
        return new PermuteMove(type, slots, old);
    }

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<Roster> sd) {
        for (int i = 0; i < slots.length; i++) {
            sd.beforeVariableChanged(slots[i], "cell");
            slots[i].setCell(newCells[i]);
            sd.afterVariableChanged(slots[i], "cell");
        }
    }

    @Override
    public Move<Roster> rebase(ScoreDirector<Roster> sd) {
        Slot[] s = new Slot[slots.length];
        Cell[] c = new Cell[slots.length];
        for (int i = 0; i < slots.length; i++) {
            s[i] = sd.lookUpWorkingObject(slots[i]);
            c[i] = sd.lookUpWorkingObject(newCells[i]);
        }
        return new PermuteMove(type, s, c);
    }

    @Override public String getSimpleMoveTypeDescription() { return type; }
    @Override public Collection<?> getPlanningEntities() { return Arrays.asList(slots); }
    @Override public Collection<?> getPlanningValues() { return Arrays.asList(newCells); }
    @Override public String toString() { return type + Arrays.toString(slots); }
}
