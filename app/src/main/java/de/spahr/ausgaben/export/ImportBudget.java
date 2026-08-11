package de.spahr.ausgaben.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Teilt den Rest des Fortschrittsbalkens (hinter {@link ImportPhase#READ_FILE_TO}) nach der
 * <b>gemessenen Arbeitsmenge</b> auf die Phasen eines Laufs auf.
 *
 * <p>Feste Grenzen taugen nicht, weil jeder Lauf anders aussieht: ein Depot-Neueinlesen hat gar keine
 * Buchungsphase, ein Konto ohne Depot keine Kurse. Erst wenn die Datei gelesen ist, stehen die Mengen
 * fest (Zahl der Buchungen, Zahl der Kurszeilen) – daraus ergeben sich die Bänder.</p>
 *
 * <p>Die Reihenfolge der {@link #add}-Aufrufe ist die Reihenfolge des Ablaufs.</p>
 */
public final class ImportBudget {

    /** Herunterladen und Lesen behalten ihre festen Grenzen; hier beginnt die Aufteilung. */
    public static final int HEAD_END = ImportPhase.READ_FILE_TO;

    /** Die 100 gehört dem Ende, nicht der letzten Phase. */
    public static final int TAIL_END = 99;

    // Arbeitseinheiten je Mengeneinheit – an einer Stelle. Lesen und Schreiben einer Buchung kosten
    // etwa gleich viel; eine Kurszeile ist, in einer Transaktion geschrieben, deutlich billiger.
    public static final double BOOKING_READ = 1.0;
    public static final double BOOKING_WRITE = 1.0;
    /** Je Hauptbuch-Transaktion: das Depot liest das Hauptbuch einmal komplett durch. */
    public static final double DEPOT_READ = 1.0;
    public static final double PRICE_WRITE = 0.2;
    /** Die geplanten Buchungen melden nichts – pauschal etwa so teuer wie 200 Buchungen. */
    public static final double SCHEDULES = 200.0;

    private final List<String> order = new ArrayList<>();
    private final Map<String, Double> units = new LinkedHashMap<>();
    private final Set<String> begun = new LinkedHashSet<>();

    /** Nimmt einen Schritt auf. Ein zweiter Aufruf mit demselben Schlüssel überschreibt die Menge. */
    public void add(String key, double u) {
        if (!units.containsKey(key)) {
            order.add(key);
        }
        units.put(key, Math.max(0, u));
    }

    /**
     * Korrigiert die Menge eines noch nicht begonnenen Schritts (etwa: die wirklich gebaute Zahl der
     * Buchungen steht erst nach dem Lesen fest). Hat der Schritt schon angefangen, bleibt sein Band
     * stehen – die Anzeige darf nicht zurückspringen.
     */
    public void resize(String key, double u) {
        if (units.containsKey(key) && !begun.contains(key)) {
            units.put(key, Math.max(0, u));
        }
    }

    /** Untergrenze des Bands; der Aufruf gilt als Beginn dieses Schritts. */
    public int from(String key) {
        begun.add(key);
        return bound(key, false);
    }

    /** Obergrenze des Bands. */
    public int to(String key) {
        return bound(key, true);
    }

    private int bound(String key, boolean after) {
        double total = 0;
        for (double u : units.values()) {
            total += u;
        }
        if (total <= 0 || !units.containsKey(key)) {
            return TAIL_END;
        }
        double before = 0;
        for (String k : order) {
            if (k.equals(key)) {
                break;
            }
            before += units.get(k);
        }
        double at = after ? before + units.get(key) : before;
        return HEAD_END + (int) Math.round((TAIL_END - HEAD_END) * at / total);
    }
}
