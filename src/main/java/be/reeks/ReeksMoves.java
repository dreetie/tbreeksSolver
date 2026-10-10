package be.reeks;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Zetten die de reeks permuteren: de cellen van een kolom wisselen enkel van week.
 * Types: wissel in kolom, insertie in kolom, hele week wisselen, deelbereik van kolommen wisselen
 * (o.a. R6+R7 samen voor weekends), kolom roteren, twee aansluitende weken wisselen.
 */
public class ReeksMoves implements MoveIteratorFactory<Roster, Move<Roster>> {

    @Override public long getSize(ScoreDirector<Roster> sd) { return 1_000_000L; }

    @Override
    public Iterator<Move<Roster>> createOriginalMoveIterator(ScoreDirector<Roster> sd) {
        return createRandomMoveIterator(sd, new Random(0));
    }

    @Override
    public Iterator<Move<Roster>> createRandomMoveIterator(ScoreDirector<Roster> sd, Random rnd) {
        final List<Slot> slots = sd.getWorkingSolution().getSlots();
        final int W = slots.size() / 7;
        return new Iterator<>() {
            @Override public boolean hasNext() { return true; }
            @Override public Move<Roster> next() { return make(slots, W, rnd); }
        };
    }

    private static Slot at(List<Slot> s, int w, int c) { return s.get(w * 7 + c); }

    static Move<Roster> make(List<Slot> s, int W, Random r) {
        int t = r.nextInt(100);
        int a = r.nextInt(W), b = r.nextInt(W - 1);
        if (b >= a) b++;
        if (t < 40) {                       // wissel twee weken in één kolom
            int c = r.nextInt(7);
            return new PermuteMove("colSwap", new Slot[]{at(s, a, c), at(s, b, c)},
                    new Cell[]{at(s, b, c).getCell(), at(s, a, c).getCell()});
        }
        if (t < 60) {                       // insertie: cel van week a naar week b, rest schuift op
            int c = r.nextInt(7);
            int lo = Math.min(a, b), hi = Math.max(a, b), n = hi - lo + 1;
            Slot[] sl = new Slot[n];
            Cell[] nc = new Cell[n];
            for (int i = 0; i < n; i++) sl[i] = at(s, lo + i, c);
            if (a < b) { for (int i = 0; i < n - 1; i++) nc[i] = sl[i + 1].getCell(); nc[n - 1] = sl[0].getCell(); }
            else       { nc[0] = sl[n - 1].getCell(); for (int i = 1; i < n; i++) nc[i] = sl[i - 1].getCell(); }
            return new PermuteMove("colInsert", sl, nc);
        }
        if (t < 70) return swapWeeks(s, a, b, 0, 6, "weekSwap");
        if (t < 78) return swapWeeks(s, a, b, 5, 6, "weekendSwap");
        if (t < 83) return swapWeeks(s, a, b, 0, 4, "workdaysSwap");
        if (t < 88) {                       // willekeurig aaneengesloten kolombereik
            int c1 = r.nextInt(7), c2 = r.nextInt(7);
            return swapWeeks(s, a, b, Math.min(c1, c2), Math.max(c1, c2), "rangeSwap");
        }
        if (t < 92) {                       // kolom roteren over k weken
            int c = r.nextInt(7), k = 1 + r.nextInt(W - 1);
            Slot[] sl = new Slot[W];
            Cell[] nc = new Cell[W];
            for (int w = 0; w < W; w++) { sl[w] = at(s, w, c); nc[w] = at(s, (w + k) % W, c).getCell(); }
            return new PermuteMove("colRotate", sl, nc);
        }
        // twee aansluitende weken tegen twee andere aansluitende weken wisselen
        int c = r.nextInt(7);
        int p = r.nextInt(W - 1), q = r.nextInt(W - 1);
        if (Math.abs(p - q) < 2) return swapWeeks(s, a, b, 0, 6, "weekSwap");
        return new PermuteMove("pairSwap",
                new Slot[]{at(s, p, c), at(s, p + 1, c), at(s, q, c), at(s, q + 1, c)},
                new Cell[]{at(s, q, c).getCell(), at(s, q + 1, c).getCell(), at(s, p, c).getCell(), at(s, p + 1, c).getCell()});
    }

    private static Move<Roster> swapWeeks(List<Slot> s, int a, int b, int c1, int c2, String name) {
        int n = c2 - c1 + 1;
        Slot[] sl = new Slot[2 * n];
        Cell[] nc = new Cell[2 * n];
        for (int i = 0; i < n; i++) {
            sl[i] = at(s, a, c1 + i); nc[i] = at(s, b, c1 + i).getCell();
            sl[n + i] = at(s, b, c1 + i); nc[n + i] = at(s, a, c1 + i).getCell();
        }
        return new PermuteMove(name, sl, nc);
    }
}
