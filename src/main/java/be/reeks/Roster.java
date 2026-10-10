package be.reeks;

import ai.timefold.solver.core.api.domain.solution.*;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;

import java.util.List;

@PlanningSolution
public class Roster {

    @ProblemFactCollectionProperty
    private List<Cell> cells;

    @PlanningEntityCollectionProperty
    private List<Slot> slots;

    /** Per constraint een gewicht (de sliders). */
    private ConstraintWeightOverrides<HardSoftLongScore> weights;

    @PlanningScore
    private HardSoftLongScore score;

    public Roster() { }

    public Roster(List<Cell> cells, List<Slot> slots, ConstraintWeightOverrides<HardSoftLongScore> weights) {
        this.cells = cells;
        this.slots = slots;
        this.weights = weights;
    }

    public List<Cell> getCells() { return cells; }
    public void setCells(List<Cell> cells) { this.cells = cells; }
    public List<Slot> getSlots() { return slots; }
    public void setSlots(List<Slot> slots) { this.slots = slots; }
    public ConstraintWeightOverrides<HardSoftLongScore> getWeights() { return weights; }
    public void setWeights(ConstraintWeightOverrides<HardSoftLongScore> weights) { this.weights = weights; }
    public HardSoftLongScore getScore() { return score; }
    public void setScore(HardSoftLongScore score) { this.score = score; }
}
