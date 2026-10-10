package be.reeks;

/** Namen, volgorde en standaardscores van alle regels. */
public final class Rules {
    private Rules() { }

    public static final String[] HARD = {
        "H1 Interval tussen diensten >= 14 u",
        "H2 Weekgrens <= 50 u",
        "H3 Minimumduur rustblok",
        "H4 Diensteinde voor rustblok",
        "H5 Dienstaanvang na rustblok",
        "H7 Dienst na CY max 8 u",
        "H8 Rustblok max 3 dagen",
        "H9 Max 8 dagen tussen twee W-rustdagen",
        "H10 Max 7 (of 5 nacht) werkdagen na elkaar",
        "H11 Vrij weekend: elke 4 weken, max 5 tussen",
        "H12 CY max 1 per 4 weken",
        "H13 Tellers rust en compensatie per periode",
        "H15 Geen halve weekends (optioneel)",
        "H16 Geen twee weekends na elkaar (optioneel)"
    };

    public static final String[] SOFT = {
        "Z1 Omschakeling vroeg naar laat op rustdag",
        "Z2 Weken van 6 op 7 of 7 op 8",
        "Z3 Rust groeperen in blokken van 2",
        "Z4 Weg blijven van de 50 u",
        "Z5 Geen halve weekends",
        "Z6 Vroege starts niet opstapelen",
        "Z7 Late einden niet opstapelen",
        "Z8 Geen rust op het 14 u-minimum",
        "Z9 Korte werkreeksen",
        "Z10 Meer kwalificerende vrije weekends",
        "Z11 Geen twee lange diensten na elkaar",
        "Z12 Geen twee weekends na elkaar werken"
    };

    /** Standaardscores uit de masterprompt (0..10, 5 = standaard). */
    public static final int[] DEFAULT_SCORE = {10, 8, 5, 6, 10, 5, 5, 2, 5, 8, 8, 7};

    /** Eenheid per regel: de ruwe straf van één "typisch voorkomen". Zo worden alle regels in "keer" uitgedrukt. */
    public static final double[] UNIT = {20, 15, 3, 50, 14, 4, 4, 3, 8, 5, 1.5, 8};

    /** Naam van wat geteld wordt (voor de pagina). */
    public static final String[] NOUN = {
        "rustdagen met omschakeling", "stukken van 6 op 7 werken", "rustblokken van 3 dagen", "zware weken",
        "halve weekends", "vroege starts", "late einden", "krappe rusten", "lange werkreeksen",
        "vrije weekends", "paren lange diensten", "keer twee weekends na elkaar"
    };

    /** Belang 0..10 -> gewicht. 0 = negeren, 5 = 1, elke 2,5 stappen x5: 3 is ca. 0,3 en 10 is 25. */
    public static double importance(int score) {
        return score <= 0 ? 0 : Math.pow(5.0, (score - 5) / 2.5);
    }

    /** Geheel gewicht voor Timefold: score-eenheden per (milli-)ruwe straf, gedeeld door de eenheid van de regel. */
    public static long weightFor(int rule, int score) {
        return score <= 0 ? 0 : Math.max(1, Math.round(10000.0 * importance(score) / UNIT[rule]));
    }

    public static final int H = HARD.length;
    public static final int Z = SOFT.length;
}
