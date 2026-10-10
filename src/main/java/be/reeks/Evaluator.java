package be.reeks;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure regelcontrole op een reeks (cyclisch). Eén pass geeft alle harde tellers en alle zachte straffen.
 * Dag d = week*7 + kolom; de reeks sluit cyclisch aan (laatste week -> eerste week).
 */
public final class Evaluator {

    /** Resultaat van één evaluatie. */
    public static final class Result {
        public final long[] hard = new long[Rules.H];
        public final double[] soft = new double[Rules.Z];
        public double[] weekSoft;           // alleen bij detail
        public List<String> messages;       // alleen bij detail
        public int freeWeekends;            // kwalificerende vrije weekends
        public int shortestRestMin = Integer.MAX_VALUE;

        /** Som van de verplichte harde regels (H15 en H16 zijn optioneel en tellen hier niet mee). */
        public long hardTotal() { long t = 0; for (int i = 0; i < hard.length; i++) if (i != 12 && i != 13) t += hard[i]; return t; }
    }

    private static final int W36 = 36 * 60, C34 = 34 * 60, Y38 = 38 * 60;
    private static final int[] BASE = {W36, C34, Y38};
    private static final int[] END_LIMIT = {20 * 60, 23 * 60, 25 * 60};
    private static final int[] START_LIMIT = {6 * 60, 5 * 60, 3 * 60};

    private Evaluator() { }

    public static Result evaluate(Params p, int[] g, boolean detail) {
        final int N = p.days, WK = p.weeks;
        Result r = new Result();
        if (detail) { r.weekSoft = new double[WK]; r.messages = new ArrayList<>(); }

        boolean[] svc = new boolean[N];
        int[] st = new int[N], en = new int[N], du = new int[N], rc = new int[N], kd = new int[N];
        int nSvc = 0;
        for (int d = 0; d < N; d++) {
            int c = g[d];
            svc[d] = p.cellRc[c] < 0;
            st[d] = p.cellStart[c]; en[d] = p.cellEnd[c]; du[d] = p.cellDur[c];
            rc[d] = p.cellRc[c]; kd[d] = p.cellKind[c];
            if (svc[d]) nSvc++;
        }
        if (nSvc < 2) return r;

        // ---------- H2 en Z4 : weekuren ----------
        int[] weekMin = new int[WK];
        for (int d = 0; d < N; d++) weekMin[d / 7] += du[d];
        for (int w = 0; w < WK; w++) {
            if (weekMin[w] > 50 * 60) { r.hard[1]++; msg(r, "H2", w, "week " + (w + 1) + " heeft " + fmt(weekMin[w]) + " u"); }
            double h = weekMin[w] / 60.0;
            if (h > 42) { double v = 1.5 * (h - 42) * (h - 42); r.soft[3] += v; wk(r, w, v); }
        }
        int win = 0;
        for (int d = 0; d < 7; d++) win += du[d];
        for (int s = 0; s < N; s++) {
            double h = win / 60.0;
            if (h > 42) { double v = 0.1 * (h - 42) * (h - 42); r.soft[3] += v; wk(r, s / 7, v); }
            win += du[(s + 7) % N] - du[s];
        }

        // ---------- paren opeenvolgende diensten ----------
        int first = 0;
        while (!svc[first]) first++;
        int prev = first;
        for (int i = 1; i <= N; i++) {
            int d = first + i;
            int dd = d % N;
            if (!svc[dd]) continue;
            // paar (prev -> d), prev/d zijn "afgerolde" dagindexen (d > prev)
            pair(p, r, prev, d, N, svc, st, en, du, rc, kd);
            prev = d;
            if (dd == first) break;
        }

        // ---------- H9 : W-rustdagen ----------
        int firstW = -1, lastW = -1;
        for (int d = 0; d < N; d++) {
            if (!svc[d] && rc[d] == 0) {
                if (firstW < 0) firstW = d;
                if (lastW >= 0) {
                    if (d - lastW - 1 > 8) { r.hard[7]++; msg(r, "H9", d / 7, "meer dan 8 dagen tussen twee W-rustdagen rond week " + (d / 7 + 1)); }
                }
                lastW = d;
            }
        }
        if (firstW < 0) { r.hard[7]++; }
        else {
            int dist = firstW + N - lastW - 1;
            if (dist > 8) { r.hard[7]++; msg(r, "H9", firstW / 7, "meer dan 8 dagen tussen twee W-rustdagen (over de cyclus heen)"); }
        }

        // ---------- werkreeksen: H10, Z6, Z7, Z9 ; Z2 ; Z3 ; Z5 ; Z11 ----------
        // start op een rustdag zodat een reeks niet over de grens knipt
        int startRest = -1;
        for (int d = 0; d < N; d++) if (!svc[d]) { startRest = d; break; }
        if (startRest >= 0) {
            int i = 1;
            while (i <= N) {
                int d = (startRest + i) % N;
                if (!svc[d]) { i++; continue; }
                int j = i;
                while (j <= N && svc[(startRest + j) % N]) j++;
                int len = j - i;
                int sd = (startRest + i) % N;
                // H10
                if (len > 7) { r.hard[8] += len - 7; msg(r, "H10", sd / 7, len + " werkdagen na elkaar vanaf week " + (sd / 7 + 1)); }
                if (len > 5) {
                    int run = 0; boolean viol = false;
                    for (int k = 0; k < len; k++) {
                        int x = (sd + k) % N;
                        boolean night = st[x] < 4 * 60 || en[x] > 23 * 60;
                        run = night ? run + 1 : 0;
                        if (run >= 5) viol = true;
                    }
                    if (viol) { r.hard[8]++; msg(r, "H10", sd / 7, "meer dan 5 werkdagen met nachtdienst vanaf week " + (sd / 7 + 1)); }
                }
                // Z9
                double z9 = 0;
                if (len == 4) z9 = 3; else if (len >= 5) z9 = 8 + 25.0 * (len - 5);
                if (z9 > 0) { r.soft[8] += z9; wk(r, sd / 7, z9); }
                // Z6 / Z7 runs
                int runE = 0, runL = 0;
                for (int k = 0; k < len; k++) {
                    int x = (sd + k) % N;
                    if (st[x] < 300) runE++; else { if (runE > 1) { double v = 3.0 * (runE - 1) * (runE - 1); r.soft[5] += v; wk(r, x / 7, v); } runE = 0; }
                    if (en[x] > 1440) runL++; else { if (runL > 1) { double v = 3.0 * (runL - 1) * (runL - 1); r.soft[6] += v; wk(r, x / 7, v); } runL = 0; }
                }
                if (runE > 1) { double v = 3.0 * (runE - 1) * (runE - 1); r.soft[5] += v; wk(r, ((sd + len - 1) % N) / 7, v); }
                if (runL > 1) { double v = 3.0 * (runL - 1) * (runL - 1); r.soft[6] += v; wk(r, ((sd + len - 1) % N) / 7, v); }
                i = j;
            }
        }
        // Z6 / Z7 per dienst
        for (int d = 0; d < N; d++) {
            if (!svc[d]) continue;
            double v6 = st[d] < 240 ? 3 : st[d] < 270 ? 2 : st[d] < 300 ? 1 : 0;
            if (v6 > 0) { r.soft[5] += v6; wk(r, d / 7, v6); }
            double v7 = 0;
            if (en[d] > 1440) v7 += 2;
            if (en[d] > 1500) v7 += 1;
            if (en[d] > 1530) v7 += 1;
            if (v7 > 0) { r.soft[6] += v7; wk(r, d / 7, v7); }
            // Z11
            int nx = (d + 1) % N;
            if (svc[nx] && du[d] >= 510 && du[nx] >= 510) { r.soft[10] += 1; wk(r, d / 7, 1); }
        }
        // Z2 : vensters van 7 en 8 dagen
        int c7 = 0, c8 = 0;
        for (int d = 0; d < 7; d++) if (svc[d]) c7++;
        for (int d = 0; d < 8; d++) if (svc[d % N]) c8++;
        for (int s = 0; s < N; s++) {
            double v = 0;
            if (c7 == 6) v += 6; else if (c7 == 7) v += 20;
            if (c8 >= 7) v += 14;
            if (v > 0) { r.soft[1] += v; wk(r, s / 7, v); }
            if (svc[s]) { c7--; c8--; }
            if (svc[(s + 7) % N]) c7++;
            if (svc[(s + 8) % N]) c8++;
        }
        // Z3 : twee W-rustdagen naast elkaar
        for (int d = 0; d < N; d++) {
            int nx = (d + 1) % N;
            if (!svc[d] && !svc[nx] && rc[d] == 0 && rc[nx] == 0) { r.soft[2] += 2; wk(r, d / 7, 2); }
        }
        // Z5 halve weekends, qualifying weekends
        boolean[] free = new boolean[WK];
        int nFree = 0;
        for (int w = 0; w < WK; w++) {
            int sat = w * 7 + 5, sun = w * 7 + 6;
            if (svc[sat] != svc[sun]) { r.soft[4] += 14; r.hard[12]++; wk(r, w, 14); msg(r, "H15", w, "half weekend in week " + (w + 1)); }
            free[w] = qualifies(p, w, N, svc, st, en, rc, kd);
            if (free[w]) nFree++;
        }
        // Z12: weekends (za of zo gewerkt) in aansluitende weken; reeks van r weekends kost 8 x (r-1)^2
        {
            boolean[] worked = new boolean[WK];
            int firstOff = -1;
            for (int w = 0; w < WK; w++) { worked[w] = svc[w * 7 + 5] || svc[w * 7 + 6]; if (!worked[w] && firstOff < 0) firstOff = w; }
            if (firstOff < 0) { double v = 8.0 * (WK - 1) * (WK - 1); r.soft[11] += v; r.hard[13] += WK; }
            else {
                int i = 1;
                while (i <= WK) {
                    int w = (firstOff + i) % WK;
                    if (!worked[w]) { i++; continue; }
                    int j = i;
                    while (j <= WK && worked[(firstOff + j) % WK]) j++;
                    int len = j - i;
                    if (len >= 2) {
                        double v = 8.0 * (len - 1) * (len - 1); r.soft[11] += v; wk(r, (firstOff + j - 1) % WK, v);
                        r.hard[13] += len - 1;
                        msg(r, "H16", (firstOff + j - 1) % WK, len + " weekends na elkaar gewerkt, eindigend in week " + ((firstOff + j - 1) % WK + 1));
                    }
                    i = j;
                }
            }
        }
        r.freeWeekends = nFree;
        r.soft[9] -= 5.0 * nFree;
        for (int w = 0; w < WK; w++) if (free[w]) wk(r, w, -5);

        // ---------- H11 ----------
        for (int w = 0; w < WK; w++) {
            boolean any = false;
            for (int k = 0; k < 4 && !any; k++) any = free[(w + k) % WK];
            if (!any) { r.hard[9]++; msg(r, "H11", w, "geen vrij weekend in de 4 weken vanaf week " + (w + 1)); }
        }
        {
            int lastF = -1, firstF = -1;
            for (int w = 0; w < WK; w++) if (free[w]) {
                if (firstF < 0) firstF = w;
                if (lastF >= 0 && w - lastF > 5) { r.hard[9]++; msg(r, "H11", w, "meer dan 5 weken tussen twee vrije weekends"); }
                lastF = w;
            }
            if (firstF >= 0 && firstF + WK - lastF > 5) { r.hard[9]++; msg(r, "H11", firstF, "meer dan 5 weken tussen twee vrije weekends (over de cyclus)"); }
        }

        // ---------- H12 : CY ----------
        int prevCy = -1, firstCy = -1;
        for (int d = 0; d < N; d++) if (!svc[d] && kd[d] == Cell.Kind.CY.ordinal()) {
            int w = d / 7;
            if (firstCy < 0) firstCy = w;
            if (prevCy >= 0 && w - prevCy < 4) { r.hard[10]++; msg(r, "H12", w, "twee CY binnen 4 weken (week " + (prevCy + 1) + " en " + (w + 1) + ")"); }
            prevCy = w;
        }
        if (firstCy >= 0 && prevCy != firstCy && firstCy + WK - prevCy < 4) { r.hard[10]++; msg(r, "H12", firstCy, "twee CY binnen 4 weken over de cyclus heen"); }
        if (firstCy >= 0 && prevCy == firstCy && WK < 4) r.hard[10]++;

        // ---------- H13 : tellers per periode ----------
        counts(p, r, g, 4, true, p.minRh4);
        counts(p, r, g, 8, true, p.minRh8);
        counts(p, r, g, 4, false, p.minCh4);
        counts(p, r, g, 13, false, p.minCh13);

        return r;
    }

    // ---- tellers (RH + RH/CH) of (CH + RH/CH) in vensters van n weken ----
    public static int minCount(Params p, int[] g, int len, boolean rh) {
        int WK = p.weeks, best = Integer.MAX_VALUE;
        if (len > WK) len = WK;
        for (int w = 0; w < WK; w++) {
            int c = 0;
            for (int k = 0; k < len; k++) {
                int wk = (w + k) % WK;
                for (int col = 0; col < 7; col++) c += hit(p, g[wk * 7 + col], rh);
            }
            best = Math.min(best, c);
        }
        return best;
    }

    private static int hit(Params p, int cellId, boolean rh) {
        int k = p.cellKind[cellId];
        if (rh) return (k == Cell.Kind.RH.ordinal() || k == Cell.Kind.RH_CH.ordinal()) ? 1 : 0;
        return (k == Cell.Kind.CH.ordinal() || k == Cell.Kind.RH_CH.ordinal()) ? 1 : 0;
    }

    private static void counts(Params p, Result r, int[] g, int len, boolean rh, int min) {
        int WK = p.weeks;
        if (len > WK) len = WK;
        for (int w = 0; w < WK; w++) {
            int c = 0;
            for (int k = 0; k < len; k++) {
                int wk = (w + k) % WK;
                for (int col = 0; col < 7; col++) c += hit(p, g[wk * 7 + col], rh);
            }
            if (c < min) {
                r.hard[11]++;
                msg(r, "H13", w, "venster van " + len + " weken vanaf week " + (w + 1) + ": " + c + " " + (rh ? "RH" : "CH") + " (minimum " + min + ")");
            }
        }
    }

    // ---- weekend kwalificatie (H11) ----
    private static boolean qualifies(Params p, int w, int N, boolean[] svc, int[] st, int[] en, int[] rc, int[] kd) {
        int sat = w * 7 + 5, sun = w * 7 + 6;
        if (svc[sat] || svc[sun]) return false;
        if (kd[sat] == Cell.Kind.CY.ordinal() || kd[sun] == Cell.Kind.CY.ordinal()) return false;
        // vorige dienst (teruglopen vanaf vrijdag) en volgende dienst (vanaf maandag)
        int pd = -1;
        for (int k = 1; k <= N; k++) { int x = ((sat - k) % N + N) % N; if (svc[x]) { pd = sat - k; break; } }
        int nd = -1;
        for (int k = 1; k <= N; k++) { int x = (sun + k) % N; if (svc[x]) { nd = sun + k; break; } }
        if (pd < 0 || nd < 0) return false;
        long endP = (long) pd * 1440 + en[((pd % N) + N) % N];
        long startN = (long) nd * 1440 + st[nd % N];
        long fri23 = (long) (w * 7 + 4) * 1440 + 23 * 60;
        long mon05 = (long) (w * 7 + 7) * 1440 + 5 * 60;
        return endP <= fri23 && startN >= mon05 && (startN - endP) >= 58 * 60;
    }

    // ---- regels per paar opeenvolgende diensten (p -> n), n kan >= N zijn (afgerold) ----
    private static void pair(Params prm, Result r, int p, int n, int N, boolean[] svc, int[] st, int[] en, int[] du, int[] rc, int[] kd) {
        int pp = p % N, nn = n % N;
        int gapDays = n - p;
        int k = gapDays - 1;
        int gap = gapDays * 1440 + st[nn] - en[pp];
        if (gap < r.shortestRestMin) r.shortestRestMin = gap;
        int wp = pp / 7;
        if (gap < 14 * 60) { r.hard[0]++; msg(r, "H1", wp, "rust van " + fmt(gap) + " u tussen dienst " + dayName(pp) + " en " + dayName(nn)); }

        double dh = (st[nn] - st[pp]) / 60.0;
        double z1 = 0;
        if (k == 1) z1 += 12 + 3 * Math.max(0, Math.abs(dh) - 2);
        if (dh > 0) z1 += Math.pow(Math.max(0, dh - 2.5 * gapDays), 1.5);
        else if (dh < 0) z1 += 1.5 * Math.pow(Math.max(0, -dh - 1.2 * gapDays), 1.7);
        if (z1 > 0) { r.soft[0] += z1; wk(r, wp, z1); }

        // Z8
        if (gap < 15 * 60) {
            double v = gap < 14 * 60 + 30 ? 2 : 1;
            if (st[nn] < 300) v += 2;
            r.soft[7] += v; wk(r, wp, v);
        }
        if (k <= 0) return;

        int first = (p + 1) % N, last = (n - 1) % N;
        int rc0 = rc[first], rcL = rc[last];
        boolean hasW = false;
        int base = 0;
        for (int x = p + 1; x < n; x++) {
            int c = rc[x % N];
            if (c == 0) hasW = true;
            base = Math.max(base, BASE[c]);
        }
        int minDur = base + (k - 1) * 1440;
        if (gap < minDur) { r.hard[2]++; msg(r, "H3", wp, "rustblok na " + dayName(pp) + " week " + (wp + 1) + " is " + fmt(gap) + " u (minimum " + fmt(minDur) + ")"); }
        if (k > 3) { r.hard[6]++; msg(r, "H8", wp, "rustblok van " + k + " dagen in week " + (wp + 1)); }
        boolean relaxed = k >= 3 && !hasW;
        if (!relaxed) {
            if (en[pp] > END_LIMIT[rc0]) { r.hard[3]++; msg(r, "H4", wp, "dienst eindigt " + Cell.hhmm(en[pp]) + " voor rustblok in week " + (wp + 1)); }
            if (st[nn] < START_LIMIT[rcL]) { r.hard[4]++; msg(r, "H5", wp, "dienst begint " + Cell.hhmm(st[nn]) + " na rustblok in week " + (wp + 1)); }
        }
        if (rcL == 2 && du[nn] > 8 * 60) { r.hard[5]++; msg(r, "H7", wp, "dienst na CY duurt " + fmt(du[nn]) + " u (week " + (nn / 7 + 1) + ")"); }
        if (k == 3) { r.soft[2] += 3; wk(r, wp, 3); }
    }

    private static void wk(Result r, int w, double v) { if (r.weekSoft != null) r.weekSoft[w] += v; }

    private static void msg(Result r, String rule, int week, String text) {
        if (r.messages != null) r.messages.add(rule + ": " + text);
    }

    private static String fmt(int minutes) { return String.format("%d:%02d", minutes / 60, minutes % 60); }

    private static String dayName(int d) {
        String[] n = {"ma", "di", "wo", "do", "vr", "za", "zo"};
        return n[d % 7] + " w" + (d / 7 + 1);
    }
}
