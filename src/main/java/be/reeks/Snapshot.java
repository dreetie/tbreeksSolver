package be.reeks;

/** Onveranderlijke momentopname van de indeling; evalueert alle regels één keer (lazy). */
public final class Snapshot {
    private final Params params;
    private final int[] grid;
    private Evaluator.Result result;

    Snapshot(Params params, int[] grid) { this.params = params; this.grid = grid; }

    private Evaluator.Result res() {
        Evaluator.Result r = result;
        if (r == null) { r = Evaluator.evaluate(params, grid, false); result = r; }
        return r;
    }

    public long hard(int i) { return res().hard[i]; }

    /** Ruwe zachte straf in duizendsten; het gewicht per regel (belang / eenheid) komt van de constraint-override. */
    public long soft(int i) { return Math.round(res().soft[i] * 1000.0); }
}
