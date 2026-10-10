package be.reeks;

import java.util.Locale;

/** Bouwt de JSON voor de webpagina (zonder externe JSON-bibliotheek). */
public final class Report {
    private Report() { }

    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }

    static String num(double d) { return String.format(Locale.ROOT, "%.2f", d); }

    /** Validatie van de ingelezen reeks (masterprompt sectie 2). */
    public static String validation(Series s, Evaluator.Result base) {
        int n = s.cells.size(), svc = 0, dur = 0;
        int[] kind = new int[Cell.Kind.values().length];
        for (Cell c : s.cells) { kind[c.kind.ordinal()]++; if (c.isService()) { svc++; dur += c.dur; } }
        int bothRest = 0;
        for (int w = 0; w < s.weeks; w++)
            if (!s.cells.get(s.original[w * 7 + 5]).isService() && !s.cells.get(s.original[w * 7 + 6]).isService()) bothRest++;
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"weeks\":").append(s.weeks).append(",\"cells\":").append(n).append(",\"services\":").append(svc);
        sb.append(",\"avg\":").append(q(svc == 0 ? "-" : String.format("%d:%02d", dur / svc / 60, (dur / svc) % 60)));
        for (Cell.Kind k : Cell.Kind.values()) if (k != Cell.Kind.SERVICE) sb.append(",\"").append(k.name()).append("\":").append(kind[k.ordinal()]);
        sb.append(",\"weekendsBothRest\":").append(bothRest);
        sb.append(",\"weekendsQualifying\":").append(base.freeWeekends);
        sb.append(",\"shortestRest\":").append(q(String.format("%d:%02d", base.shortestRestMin / 60, base.shortestRestMin % 60)));
        sb.append(",\"minRh4\":").append(s.params.minRh4).append(",\"minRh8\":").append(s.params.minRh8)
          .append(",\"minCh4\":").append(s.params.minCh4).append(",\"minCh13\":").append(s.params.minCh13);
        sb.append(",\"hardTotal\":").append(base.hardTotal());
        return sb.append('}').toString();
    }

    private static String grid(Series s, int[] g, int[] ref) {
        StringBuilder sb = new StringBuilder("[");
        for (int w = 0; w < s.weeks; w++) {
            if (w > 0) sb.append(',');
            sb.append('[');
            for (int c = 0; c < 7; c++) {
                Cell cell = s.cells.get(g[w * 7 + c]);
                if (c > 0) sb.append(',');
                sb.append("{\"c\":").append(q(cell.code));
                if (cell.isService()) {
                    sb.append(",\"s\":").append(q(Cell.hhmm(cell.start))).append(",\"e\":").append(q(Cell.hhmm(cell.end)))
                      .append(",\"m0\":").append(cell.start).append(",\"m1\":").append(cell.end).append(",\"d\":").append(cell.dur);
                }
                sb.append(",\"k\":").append(q(cell.kind.name())).append(",\"from\":").append(cell.origWeek + 1)
                  .append(",\"moved\":").append(g[w * 7 + c] != ref[w * 7 + c]).append('}');
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    public static String status(Series s, int[] scores, SolveJob job, String seriesError, Evaluator.Result base, Evaluator.Result best,
                                int[] bestGrid, String state, double elapsed, int seconds, String seriesText, boolean forbidHalf, boolean forbidConsec) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"state\":").append(q(state)).append(",\"elapsed\":").append(num(elapsed)).append(",\"seconds\":").append(seconds).append(",\"forbidHalf\":").append(forbidHalf).append(",\"forbidConsec\":").append(forbidConsec);
        if (job != null && job.error != null) sb.append(",\"error\":").append(q(job.error));
        if (seriesError != null) sb.append(",\"error\":").append(q(seriesError));
        if (s == null) return sb.append('}').toString();
        sb.append(",\"unit\":[");
        for (int i = 0; i < Rules.Z; i++) { if (i > 0) sb.append(','); sb.append(Rules.UNIT[i]); }
        sb.append("],\"noun\":[");
        for (int i = 0; i < Rules.Z; i++) { if (i > 0) sb.append(','); sb.append(q(Rules.NOUN[i])); }
        sb.append("]");
        sb.append(",\"validation\":").append(validation(s, base));
        sb.append(",\"rules\":[");
        for (int i = 0; i < Rules.H; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"name\":").append(q(Rules.HARD[i])).append(",\"kind\":\"hard\",\"base\":").append(base.hard[i])
              .append(",\"best\":").append(best.hard[i]).append(",\"enabled\":").append(i == 12 ? forbidHalf : i == 13 ? forbidConsec : true).append('}');
        }
        double wb = 0, wn = 0;
        for (int i = 0; i < Rules.Z; i++) {
            sb.append(",{\"name\":").append(q(Rules.SOFT[i])).append(",\"kind\":\"soft\",\"score\":").append(scores[i])
              .append(",\"base\":").append(num(base.soft[i])).append(",\"best\":").append(num(best.soft[i])).append('}');
            wb += Rules.importance(scores[i]) * base.soft[i] / Rules.UNIT[i]; wn += Rules.importance(scores[i]) * best.soft[i] / Rules.UNIT[i];
        }
        sb.append("],\"weighted\":{\"base\":").append(num(wb)).append(",\"best\":").append(num(wn)).append('}');
        sb.append(",\"free\":{\"base\":").append(base.freeWeekends).append(",\"best\":").append(best.freeWeekends).append('}');
        sb.append(",\"shortestRest\":{\"base\":").append(base.shortestRestMin).append(",\"best\":").append(best.shortestRestMin).append('}');
        sb.append(",\"grid\":").append(grid(s, bestGrid, s.original));
        sb.append(",\"orig\":").append(grid(s, s.original, s.original));
        sb.append(",\"weekSoftBase\":[");
        for (int w = 0; w < s.weeks; w++) { if (w > 0) sb.append(','); sb.append(num(weekWeighted(base, w, scores))); }
        sb.append("],\"weekSoftBest\":[");
        for (int w = 0; w < s.weeks; w++) { if (w > 0) sb.append(','); sb.append(num(weekWeighted(best, w, scores))); }
        sb.append("],\"messages\":[");
        int shown = 0;
        for (int i = 0; best.messages != null && i < best.messages.size() && shown < 40; i++) {
            String m = best.messages.get(i);
            if (!forbidHalf && m.startsWith("H15")) continue;
            if (!forbidConsec && m.startsWith("H16")) continue;
            if (shown++ > 0) sb.append(',');
            sb.append(q(m));
        }
        sb.append("],\"series\":").append(q(seriesText == null ? "" : seriesText));
        sb.append(",\"text\":").append(q(s.toText(bestGrid)));
        return sb.append('}').toString();
    }

    // Weekstraf per regel weegt niet per regel; we geven de ruwe weekstraf (som over regels met gemiddelde schaal)
    private static double weekWeighted(Evaluator.Result r, int w, int[] scores) {
        return r.weekSoft == null ? 0 : r.weekSoft[w];
    }
}
