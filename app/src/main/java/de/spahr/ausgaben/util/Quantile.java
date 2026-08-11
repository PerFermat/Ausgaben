package de.spahr.ausgaben.util;

import java.util.Arrays;

/**
 * Ränge in einer aufsteigend sortierten Zahlenreihe – die Rechnung hinter den Betrags-Schiebereglern.
 *
 * <p>Der Regler bewegt sich in <b>Rängen</b> statt in Beträgen: steht der rechte Daumen auf 90 %,
 * fallen die größten 10 % der Werte weg, gleich ob der größte Wert 200 € oder 20.000 € beträgt. Nach
 * Beträgen gerechnet würde eine einzige Jahresabrechnung den ganzen Weg an sich reißen.</p>
 *
 * <p>Gleiche Werte (viele Käufe zu 4,99 €) liegen auf demselben Rang – der Schnitt trifft dann nicht
 * genau 10 %, sondern die nächstgelegene Stufe.</p>
 */
public final class Quantile {

    private Quantile() {
    }

    /** Wert am Rang {@code percent} (0–100). Leere Reihe → 0. */
    public static long valueAt(long[] sorted, float percent) {
        if (sorted == null || sorted.length == 0) {
            return 0;
        }
        float p = Math.max(0f, Math.min(100f, percent));
        int idx = Math.round((sorted.length - 1) * p / 100f);
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    /**
     * Umkehrung: auf welchem Rang (0–100) liegt {@code value}? Für einen von Hand getippten Betrag,
     * der auch zwischen zwei vorhandenen Werten liegen darf – dann zählt der nächstniedrigere Rang.
     */
    public static float percentOf(long[] sorted, long value) {
        if (sorted == null || sorted.length < 2) {
            return 0f;
        }
        int idx = upperBound(sorted, value) - 1;      // letzter Rang mit Wert <= value
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return 100f * idx / (sorted.length - 1);
    }

    /**
     * Rang des Vorzeichenwechsels – dort sitzt im Regler die Markierung der Null. {@code -1}, wenn
     * alle Werte dasselbe Vorzeichen haben (dann gibt es nichts zu markieren).
     */
    public static float percentOfZero(long[] sorted) {
        if (sorted == null || sorted.length < 2 || sorted[0] >= 0
                || sorted[sorted.length - 1] < 0) {
            return -1f;
        }
        // Die Null liegt zwischen dem letzten negativen und dem ersten nicht-negativen Wert – der
        // Strich gehört also in die Mitte zwischen die beiden Ränge, nicht auf einen von ihnen.
        int negatives = upperBound(sorted, -1);       // Zahl der Werte < 0
        float percent = 100f * (negatives - 0.5f) / (sorted.length - 1);
        return Math.max(0f, Math.min(100f, percent));
    }

    /** Zahl der Werte {@code <= value} (erste Position dahinter). */
    private static int upperBound(long[] sorted, long value) {
        int pos = Arrays.binarySearch(sorted, value);
        if (pos < 0) {
            return -pos - 1;                          // Einfügestelle = Zahl der kleineren Werte
        }
        while (pos < sorted.length && sorted[pos] == value) {
            pos++;                                    // hinter die letzte Wiederholung
        }
        return pos;
    }
}
