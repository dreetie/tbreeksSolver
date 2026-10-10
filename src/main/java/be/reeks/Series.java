package be.reeks;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Een ingelezen reeks: cellen, parameters en de oorspronkelijke indeling. */
public final class Series {

    public final int weeks;
    public final List<Cell> cells = new ArrayList<>();
    public final List<List<Cell>> byCol = new ArrayList<>();
    public final int[] original;      // cel-id per dag (week*7+kolom)
    public final Params params;

    private static final Pattern SERVICE = Pattern.compile("^(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})$");

    private Series(int weeks, List<String[]> rows) {
        this.weeks = weeks;
        for (int c = 0; c < 7; c++) byCol.add(new ArrayList<>());
        original = new int[weeks * 7];
        int id = 0;
        for (int w = 0; w < weeks; w++) {
            for (int c = 0; c < 7; c++) {
                Cell cell = parseCell(id, c, w, rows.get(w)[c].trim());
                cells.add(cell);
                byCol.get(c).add(cell);
                original[w * 7 + c] = id;
                id++;
            }
        }
        params = new Params(cells, weeks);
        params.minRh4 = Evaluator.minCount(params, original, 4, true);
        params.minRh8 = Evaluator.minCount(params, original, 8, true);
        params.minCh4 = Evaluator.minCount(params, original, 4, false);
        params.minCh13 = Evaluator.minCount(params, original, 13, false);
    }

    private static Cell parseCell(int id, int col, int week, String t) {
        Matcher m = SERVICE.matcher(t);
        if (m.matches()) {
            int s = Integer.parseInt(m.group(2)) * 60 + Integer.parseInt(m.group(3));
            int e = Integer.parseInt(m.group(4)) * 60 + Integer.parseInt(m.group(5));
            if (e <= s) e += 1440;
            return new Cell(id, col, week, m.group(1), Cell.Kind.SERVICE, s, e);
        }
        Cell.Kind k;
        switch (t) {
            case "RH": k = Cell.Kind.RH; break;
            case "RR": k = Cell.Kind.RR; break;
            case "CH": k = Cell.Kind.CH; break;
            case "CV": k = Cell.Kind.CV; break;
            case "CY": k = Cell.Kind.CY; break;
            case "RH/CH": k = Cell.Kind.RH_CH; break;
            case "RR/CV": k = Cell.Kind.RR_CV; break;
            default: throw new IllegalArgumentException("Onbekende cel '" + t + "' (week " + (week + 1) + ", R" + (col + 1) + ")");
        }
        return new Cell(id, col, week, t, k, 0, 0);
    }

    /**
     * Formaat: één regel per week, 7 cellen gescheiden door ';' (eventueel voorafgegaan door het weeknummer als 8e veld vooraan).
     * Cel = "300 03:25-12:10" of een rustcode (RH, RR, CH, CV, CY, RH/CH, RR/CV).
     */
    public static Series parse(String text) {
        List<String[]> rows = new ArrayList<>();
        int lineNo = 0;
        for (String line : text.split("\\R")) {
            lineNo++;
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] f = l.split(";");
            if (f.length == 8) {
                String[] g = new String[7];
                System.arraycopy(f, 1, g, 0, 7);
                f = g;
            }
            if (f.length != 7) throw new IllegalArgumentException("Regel " + lineNo + ": verwacht 7 cellen (R1..R7), gevonden " + f.length);
            rows.add(f);
        }
        if (rows.size() < 5) throw new IllegalArgumentException("Te weinig weken (" + rows.size() + ")");
        return new Series(rows.size(), rows);
    }

    public String toText(int[] grid) {
        StringBuilder sb = new StringBuilder();
        for (int w = 0; w < weeks; w++) {
            sb.append(w + 1);
            for (int c = 0; c < 7; c++) sb.append(';').append(cells.get(grid[w * 7 + c]).text());
            sb.append('\n');
        }
        return sb.toString();
    }
}
