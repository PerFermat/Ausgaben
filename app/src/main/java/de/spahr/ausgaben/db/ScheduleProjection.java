package de.spahr.ausgaben.db;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Faltet eine geplante Buchung anhand ihrer KMyMoney-Wiederholung ({@code occurence} × Multiplikator)
 * in die einzelnen Fälligkeitstermine innerhalb eines Zeitfensters auf. Jeder Termin wird aus dem
 * Basisdatum berechnet (kein Aufsummieren → keine Drift bei Monats-/Jahresterminen). Reine Logik.
 */
public final class ScheduleProjection {

    private static final int MAX_ITER = 2000; // Sicherheitsgrenze gegen Endlosschleifen

    private ScheduleProjection() {
    }

    /**
     * Fälligkeitstermine (ms) im Fenster {@code [fromMs, toMs]}, aufsteigend, höchstens {@code maxCount}.
     * @param baseMs   nächster Fälligkeitstermin aus der Datei ({@code <= 0} → keiner → leer)
     * @param endMs    Enddatum der Planung ({@code 0} = unbegrenzt); begrenzt das Fenster nach oben
     */
    public static List<Long> occurrences(long baseMs, int occurrence, int multiplier, long endMs,
                                         long fromMs, long toMs, int maxCount) {
        List<Long> res = new ArrayList<>();
        if (baseMs <= 0 || maxCount <= 0) {
            return res;
        }
        long effTo = endMs > 0 ? Math.min(toMs, endMs) : toMs;
        if (fromMs > effTo) {
            return res;
        }
        int[] step = stepFor(occurrence);
        if (step == null) {
            // Einmalig/unbekannt: nur der eine Termin, falls im Fenster.
            if (baseMs >= fromMs && baseMs <= effTo) {
                res.add(baseMs);
            }
            return res;
        }
        int field = step[0];
        int per = step[1] * (multiplier <= 0 ? 1 : multiplier);
        // Nur vorwärts ab der gespeicherten nächsten Fälligkeit (kein Rückwärts-Projizieren).
        for (int k = 0; k < MAX_ITER; k++) {
            long d = shift(baseMs, field, per * k);
            if (d > effTo) {
                break;
            }
            if (d >= fromMs) {
                res.add(d);
            }
        }
        Collections.sort(res);
        if (res.size() > maxCount) {
            res = new ArrayList<>(res.subList(0, maxCount));
        }
        return res;
    }

    private static long shift(long baseMs, int field, int amount) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(baseMs);
        c.add(field, amount);
        return c.getTimeInMillis();
    }

    /** {@code {Calendar-Feld, Betrag je Multiplikatorschritt}} oder {@code null} bei Einmalig/unbekannt. */
    private static int[] stepFor(int occurrence) {
        switch (occurrence) {
            case 2:     return new int[]{Calendar.DAY_OF_MONTH, 1};   // Daily
            case 4:     return new int[]{Calendar.DAY_OF_MONTH, 7};   // Weekly
            case 8:     return new int[]{Calendar.DAY_OF_MONTH, 14};  // Fortnightly
            case 16:    return new int[]{Calendar.DAY_OF_MONTH, 14};  // EveryOtherWeek
            case 18:    return new int[]{Calendar.DAY_OF_MONTH, 15};  // EveryHalfMonth (≈)
            case 20:    return new int[]{Calendar.DAY_OF_MONTH, 21};  // EveryThreeWeeks
            case 30:    return new int[]{Calendar.DAY_OF_MONTH, 30};  // EveryThirtyDays
            case 32:    return new int[]{Calendar.MONTH, 1};          // Monthly
            case 64:    return new int[]{Calendar.DAY_OF_MONTH, 28};  // EveryFourWeeks
            case 126:   return new int[]{Calendar.DAY_OF_MONTH, 56};  // EveryEightWeeks
            case 128:   return new int[]{Calendar.MONTH, 2};          // EveryOtherMonth
            case 256:   return new int[]{Calendar.MONTH, 3};          // EveryThreeMonths
            case 1024:  return new int[]{Calendar.MONTH, 6};          // TwiceYearly
            case 2048:  return new int[]{Calendar.YEAR, 2};           // EveryOtherYear
            case 4096:  return new int[]{Calendar.MONTH, 3};          // Quarterly
            case 8192:  return new int[]{Calendar.MONTH, 4};          // EveryFourMonths
            case 16384: return new int[]{Calendar.YEAR, 1};           // Yearly
            default:    return null;                                  // Once / unbekannt
        }
    }
}
