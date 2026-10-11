package be.reeks;

import java.nio.file.*;
import java.util.*;

public final class SolveTool {
    public static void main(String[] a) throws Exception {
        Series s = Series.parse(Files.readString(Path.of(a[0])));
        int secs = Integer.parseInt(a[1]);
        int[] scores = a.length > 2 ? Arrays.stream(a[2].split(",")).mapToInt(Integer::parseInt).toArray() : Rules.DEFAULT_SCORE;
        SolveJob j = new SolveJob(s, scores, secs, Long.parseLong(a.length > 3 ? a[3] : "1"), s.original);
        if (a.length > 4) for (String x : a[4].split(",")) if (!x.isEmpty()) j.never[Integer.parseInt(x)] = true;   // lijst van soft-regel-indexen die "nooit" zijn
        j.start();
        while (j.state == SolveJob.State.RUNNING) {
            Thread.sleep(5000);
            System.out.printf("t=%.0fs hard=%d soft=%d%n", j.elapsedSeconds(), j.bestHard, j.bestSoft);
        }
        System.out.println("state " + j.state + " " + j.error);
        Evaluator.Result r0 = Evaluator.evaluate(s.params, s.original, false);
        Evaluator.Result r1 = Evaluator.evaluate(s.params, j.bestGrid, true);
        System.out.println("hard orig " + Arrays.toString(r0.hard) + "\nhard new  " + Arrays.toString(r1.hard));
        for (int i = 0; i < Rules.Z; i++)
            System.out.printf(Locale.ROOT, "%-45s %6.1f -> %6.1f keer (score %d)%n", Rules.SOFT[i], r0.soft[i] / Rules.UNIT[i], r1.soft[i] / Rules.UNIT[i], scores[i]);
        double w0 = 0, w1 = 0;
        for (int i = 0; i < Rules.Z; i++) { w0 += Rules.importance(scores[i]) * r0.soft[i] / Rules.UNIT[i]; w1 += Rules.importance(scores[i]) * r1.soft[i] / Rules.UNIT[i]; }
        System.out.printf(Locale.ROOT, "gewogen totaal %.1f -> %.1f ; vrije weekends %d -> %d%n", w0, w1, r0.freeWeekends, r1.freeWeekends);
        // permutatiecontrole
        for (int c = 0; c < 7; c++) {
            List<Integer> o = new ArrayList<>(), n = new ArrayList<>();
            for (int w = 0; w < s.weeks; w++) { o.add(s.original[w * 7 + c]); n.add(j.bestGrid[w * 7 + c]); }
            Collections.sort(o); Collections.sort(n);
            if (!o.equals(n)) System.out.println("PERMUTATIEFOUT kolom " + c);
        }
        long moved = 0;
        for (int d = 0; d < s.weeks * 7; d++) if (j.bestGrid[d] != s.original[d]) moved++;
        System.out.println("cellen verplaatst: " + moved + " / " + s.weeks * 7);
    }
}
