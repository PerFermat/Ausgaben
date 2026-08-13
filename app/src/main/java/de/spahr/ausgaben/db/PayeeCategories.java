package de.spahr.ausgaben.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Kategorien, die zu einem Empfänger gehören – Vorbelegung der ersten Kategoriezeile und
 * Favoritenblock der Auswahlliste im Buchungs-Editor.
 *
 * <p>Gefragt wird in derselben Reihenfolge, in der auch die Sprach-Erfassung ihre Vorlage sucht
 * ({@code AliasResolver.resolvePass}): erst die <b>bevorzugten Aliase</b> (★), dann die
 * <b>Buchungen</b> dieses Empfängers (jüngste zuerst), zuletzt die <b>übrigen Aliase</b>. Der erste
 * Fund ist die Vorbelegung, die weiteren stehen im Block dahinter.</p>
 *
 * <p>Rein rechnend, ohne Android: die Datenbankabfragen macht der Aufrufer.</p>
 */
public final class PayeeCategories {

    /** So viele Kategorien stehen höchstens im Block – wie beim Vorspann der Empfängerliste. */
    public static final int LIMIT = 6;

    private PayeeCategories() {
    }

    /**
     * Die Kategorien dieses Empfängers in der Reihenfolge, in der sie oben stehen sollen.
     *
     * @param aliases Aliase, deren Zielname der Empfänger ist (jüngste zuerst)
     * @param uses    Kategorien seiner Buchungen samt Teilzeilen, jüngste zuerst
     * @param income  {@code true} = Einnahme-Kategorien, {@code false} = Ausgabe-Kategorien
     * @param limit   Länge der Liste
     */
    public static List<String> rank(List<PayeeCorrection> aliases, List<String> uses,
                                    boolean income, int limit) {
        // Reihenfolge der ersten Eintragung zählt, Doppelte fallen weg (Groß-/Kleinschreibung egal).
        Map<String, String> gefunden = new LinkedHashMap<>();
        vonAliasen(gefunden, aliases, income, true);
        if (uses != null) {
            for (String cat : uses) {
                merke(gefunden, cat);
            }
        }
        vonAliasen(gefunden, aliases, income, false);

        List<String> out = new ArrayList<>();
        for (String cat : gefunden.values()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(cat);
        }
        return out;
    }

    /** Wie {@link #rank} mit {@link #LIMIT}. */
    public static List<String> rank(List<PayeeCorrection> aliases, List<String> uses, boolean income) {
        return rank(aliases, uses, income, LIMIT);
    }

    /** Die zur Buchungsart passenden Kategorien der bevorzugten bzw. der übrigen Aliase. */
    private static void vonAliasen(Map<String, String> gefunden, List<PayeeCorrection> aliases,
                                   boolean income, boolean preferred) {
        if (aliases == null) {
            return;
        }
        for (PayeeCorrection a : aliases) {
            if (a == null || a.preferred != preferred) {
                continue;
            }
            merke(gefunden, income ? a.catIncome1 : a.catExpense1);
            merke(gefunden, income ? a.catIncome2 : a.catExpense2);
        }
    }

    private static void merke(Map<String, String> gefunden, String category) {
        if (category == null || category.trim().isEmpty()) {
            return;
        }
        String cat = category.trim();
        String key = cat.toLowerCase(Locale.ROOT);
        if (!gefunden.containsKey(key)) {
            gefunden.put(key, cat);
        }
    }
}
