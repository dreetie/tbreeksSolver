package be.reeks;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import java.util.List;

/** Een dag in de reeks (week x kolom). De planning variable is de cel die er staat. */
@PlanningEntity
public class Slot {

    @PlanningId
    private int id;
    private int week;
    private int col;
    private List<Cell> candidates;   // alle cellen van dezelfde kolom (de permutatie blijft binnen de kolom)
    private Params params;

    @PlanningVariable(valueRangeProviderRefs = "colCells")
    private Cell cell;

    public Slot() { }

    public Slot(int week, int col, List<Cell> candidates, Params params, Cell cell) {
        this.id = week * 7 + col;
        this.week = week;
        this.col = col;
        this.candidates = candidates;
        this.params = params;
        this.cell = cell;
    }

    @ValueRangeProvider(id = "colCells")
    public List<Cell> getCandidates() { return candidates; }

    public int getId() { return id; }
    public int getWeek() { return week; }
    public int getCol() { return col; }
    public Params getParams() { return params; }
    public Cell getCell() { return cell; }
    public void setCell(Cell cell) { this.cell = cell; }

    @Override public String toString() { return "w" + (week + 1) + "R" + (col + 1) + "=" + cell; }
}
