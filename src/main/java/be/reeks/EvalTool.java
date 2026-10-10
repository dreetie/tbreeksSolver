package be.reeks;

import java.nio.file.*;
import java.util.*;

/** Hulpprogramma: evalueert een reeks (en optioneel een permutatie) en print per regel. */
public final class EvalTool {
    public static void main(String[] a) throws Exception {
        Series s = Series.parse(Files.readString(Path.of(a[0])));
        int[] g = s.original.clone();
        if (a.length > 1) {   // permutatiebestand: per dag het cel-id
            String[] f = Files.readString(Path.of(a[1])).trim().split("\\s+");
            for (int i = 0; i < f.length; i++) g[i] = Integer.parseInt(f[i]);
        }
        Evaluator.Result r = Evaluator.evaluate(s.params, g, a.length > 2);
        StringBuilder sb = new StringBuilder();
        sb.append("HARD");
        for (long h : r.hard) sb.append(' ').append(h);
        sb.append("\nSOFT");
        for (double z : r.soft) sb.append(' ').append(String.format(Locale.ROOT, "%.4f", z));
        sb.append("\nFREE ").append(r.freeWeekends).append("\nMINREST ").append(r.shortestRestMin);
        sb.append("\nMIN ").append(s.params.minRh4).append(' ').append(s.params.minRh8).append(' ').append(s.params.minCh4).append(' ').append(s.params.minCh13);
        System.out.println(sb);
        if (r.messages != null) for (String m : r.messages) System.out.println(m);
    }
}
