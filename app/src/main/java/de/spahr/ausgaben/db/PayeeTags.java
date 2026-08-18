package de.spahr.ausgaben.db;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Stichwörter, die zu einem Empfänger gehören – Vorbelegung einer neuen Buchung und Vorspann der
 * Auswahlliste im Stichwort-Fenster. Das Gegenstück zu {@link PayeeCategories}, nur ohne die
 * Trennung nach Einnahme und Ausgabe: ein Stichwort kennt sie nicht.
 *
 * <p>Gefragt wird in derselben Reihenfolge: erst die <b>bevorzugten Aliase</b> (★), dann die
 * <b>Buchungen</b> dieses Empfängers (jüngste zuerst), zuletzt die <b>übrigen Aliase</b>.</p>
 *
 * <p>Rein rechnend, ohne Android: die Datenbankabfragen macht der Aufrufer.</p>
 */
public final class PayeeTags {

    /** So viele Stichwörter stehen höchstens im Vorspann – wie bei den Kategorien. */
    public static final int LIMIT = 6;

    private PayeeTags() {
    }

    /**
     * Die Stichwörter dieses Empfängers in der Reihenfolge, in der sie oben stehen sollen.
     *
     * @param aliases Aliase, deren Zielname der Empfänger ist (jüngste zuerst)
     * @param uses    die Stichwortfelder seiner Buchungen, jüngste zuerst (je Eintrag eine Liste)
     * @param limit   Länge der Liste
     */
    public static List<String> rank(List<PayeeCorrection> aliases, List<String> uses, int limit) {
        // Reihenfolge der ersten Eintragung zählt, Doppelte fallen weg (Groß-/Kleinschreibung egal).
        Map<String, String> gefunden = new LinkedHashMap<>();
        vonAliasen(gefunden, aliases, true);
        if (uses != null) {
            for (String tags : uses) {
                merke(gefunden, tags);
            }
        }
        vonAliasen(gefunden, aliases, false);

        List<String> out = new ArrayList<>();
        for (String tag : gefunden.values()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(tag);
        }
        return out;
    }

    /** Wie {@link #rank} mit {@link #LIMIT}. */
    public static List<String> rank(List<PayeeCorrection> aliases, List<String> uses) {
        return rank(aliases, uses, LIMIT);
    }

    /**
     * Womit eine <b>neue</b> Buchung für diesen Empfänger vorbelegt wird: die Stichwörter des ersten
     * Alias, der welche trägt – bevorzugte (★) zuerst. Bewußt <b>nicht</b> aus den Buchungen: was
     * einmal an einem Beleg hing, soll nicht ungefragt an jedem weiteren hängen. Der Alias ist die
     * gesetzte Absicht, die Buchung nur ein Vorkommen.
     *
     * @return der Speicherwert für {@code booking.tags}; leer, wenn nichts vorzubelegen ist
     */
    public static String preset(List<PayeeCorrection> aliases) {
        String fromPreferred = firstWithTags(aliases, true);
        return !fromPreferred.isEmpty() ? fromPreferred : firstWithTags(aliases, false);
    }

    private static String firstWithTags(List<PayeeCorrection> aliases, boolean preferred) {
        if (aliases == null) {
            return "";
        }
        for (PayeeCorrection a : aliases) {
            if (a == null || a.preferred != preferred) {
                continue;
            }
            String tags = BookingTags.join(BookingTags.parse(a.tags));
            if (!tags.isEmpty()) {
                return tags;
            }
        }
        return "";
    }

    /** Die Stichwörter der bevorzugten bzw. der übrigen Aliase. */
    private static void vonAliasen(Map<String, String> gefunden, List<PayeeCorrection> aliases,
                                   boolean preferred) {
        if (aliases == null) {
            return;
        }
        for (PayeeCorrection a : aliases) {
            if (a != null && a.preferred == preferred) {
                merke(gefunden, a.tags);
            }
        }
    }

    /** Nimmt ein ganzes Stichwortfeld auf – es hält mehrere Namen. */
    private static void merke(Map<String, String> gefunden, String tags) {
        for (String tag : BookingTags.parse(tags)) {
            String key = tag.toLowerCase(Locale.ROOT);
            if (!gefunden.containsKey(key)) {
                gefunden.put(key, tag);
            }
        }
    }
}
