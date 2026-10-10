package be.reeks;

/** Reeksbrede gegevens die niet verschuiven: afmetingen en de H13-minima uit de originele reeks. */
public final class Params {
    public final int weeks;
    public final int days;
    public final int[] cellStart;   // per cel-id
    public final int[] cellEnd;
    public final int[] cellDur;
    public final int[] cellRc;      // restklasse, -1 = dienst
    public final int[] cellKind;    // Kind.ordinal()
    public int minRh4, minRh8, minCh4, minCh13;

    public Params(java.util.List<Cell> cells, int weeks) {
        this.weeks = weeks;
        this.days = weeks * 7;
        int n = cells.size();
        cellStart = new int[n]; cellEnd = new int[n]; cellDur = new int[n];
        cellRc = new int[n]; cellKind = new int[n];
        for (Cell c : cells) {
            cellStart[c.id] = c.start; cellEnd[c.id] = c.end; cellDur[c.id] = c.dur;
            cellRc[c.id] = c.restClass(); cellKind[c.id] = c.kind.ordinal();
        }
    }
}
