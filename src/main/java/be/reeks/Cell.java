package be.reeks;

/**
 * Eén cel uit de reeks: een dienst (met begin/einde) of een rustcode.
 * Een Cell hoort vast bij één kolom (R1..R7); enkel de week waarin hij staat verschuift.
 */
public final class Cell {

    public enum Kind { SERVICE, RH, RR, CH, CV, CY, RH_CH, RR_CV }

    public final int id;
    public final int col;          // 0..6 = R1..R7 (ma..zo)
    public final int origWeek;     // 0-based weekindex in de ingelezen reeks
    public final String code;      // "300", "RH", "RH/CH", ...
    public final Kind kind;
    public final int start;        // minuten sinds middernacht (enkel diensten)
    public final int end;          // minuten; > 1440 als over middernacht
    public final int dur;          // minuten

    public Cell(int id, int col, int origWeek, String code, Kind kind, int start, int end) {
        this.id = id;
        this.col = col;
        this.origWeek = origWeek;
        this.code = code;
        this.kind = kind;
        this.start = start;
        this.end = end;
        this.dur = kind == Kind.SERVICE ? end - start : 0;
    }

    public boolean isService() { return kind == Kind.SERVICE; }

    /** Rustklasse: 0 = W-type (RH, RR, RH/CH, RR/CV), 1 = C-type (CH, CV), 2 = Y-type (CY), -1 = dienst. */
    public int restClass() {
        switch (kind) {
            case RH: case RR: case RH_CH: case RR_CV: return 0;
            case CH: case CV: return 1;
            case CY: return 2;
            default: return -1;
        }
    }

    public static String hhmm(int minutes) {
        int m = minutes % 1440;
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    public String text() {
        return isService() ? code + " " + hhmm(start) + "-" + hhmm(end) : code;
    }

    @Override public String toString() { return text(); }
}
