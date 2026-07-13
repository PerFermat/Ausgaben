package de.spahr.ausgaben.db;

/**
 * Reine Logik der Budget-Balkenfarbe: „erwarteter Fortschritt" aus der Zahlungshistorie und die
 * Ampelentscheidung. Ohne Android/DB – JVM-testbar.
 */
public final class BudgetProgress {

    private BudgetProgress() {
    }

    /**
     * Kumulierter Anteil der Magnitude bis einschließlich {@code thresholdBucket} an der Gesamtsumme.
     * {@code bucketTotals} ist nach Bucket indiziert (Index 0 = Bucket 1 usw.). Ergebnis in [0,1];
     * {@code -1}, wenn keine Historie vorliegt (Gesamtsumme 0) → Aufrufer nutzt den linearen Rückfall.
     */
    public static double expectedFraction(long[] bucketTotals, int thresholdBucket) {
        if (bucketTotals == null) {
            return -1;
        }
        long total = 0;
        long upto = 0;
        for (int i = 0; i < bucketTotals.length; i++) {
            long v = bucketTotals[i];
            total += v;
            if (i + 1 <= thresholdBucket) {   // Index 0 = Bucket 1
                upto += v;
            }
        }
        if (total <= 0) {
            return -1;
        }
        return (double) upto / total;
    }

    /**
     * Ampel: {@code true} = grün (im Plan). Ausgabe ist grün, wenn nicht mehr ausgeschöpft als bis jetzt
     * erwartet ({@code used <= base + tol}); Einnahme, wenn mindestens so viel wie erwartet
     * ({@code used >= base - tol}). {@code base} ist der erwartete Anteil (aus dem Verlauf oder linear).
     */
    public static boolean onTrack(boolean income, double used, double base, double tol) {
        if (income) {
            return used >= base - tol;
        }
        return used <= base + tol;
    }
}
