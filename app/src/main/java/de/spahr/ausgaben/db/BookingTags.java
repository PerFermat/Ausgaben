package de.spahr.ausgaben.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Stichwörter einer Buchung – in KMyMoney die „Tags", dort eine gepflegte Liste, aus der jede
 * Buchung beliebig viele tragen kann.
 *
 * <p>Gehalten werden sie als ein einziges Textfeld, die Namen durch {@code |} getrennt – dieselbe
 * Bauart wie die Ortsliste eines Alias. Diese Klasse kennt kein Android und ist damit ohne Emulator
 * prüfbar.</p>
 */
public final class BookingTags {

    /** Trennzeichen zwischen zwei Stichwörtern; in einem Namen selbst ist es nicht zugelassen. */
    public static final char SEP = '|';

    private BookingTags() {
    }

    /** Die einzelnen Stichwörter in der gespeicherten Reihenfolge; leere Teile fallen weg. */
    public static List<String> parse(String tags) {
        List<String> out = new ArrayList<>();
        if (tags == null) {
            return out;
        }
        for (String part : tags.split("\\" + SEP)) {
            String name = part.trim();
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out;
    }

    /** Fügt die Stichwörter zum Speicherwert zusammen; leere Namen bleiben außen vor. */
    public static String join(List<String> names) {
        if (names == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            String clean = sanitize(name);
            if (clean.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(SEP);
            }
            sb.append(clean);
        }
        return sb.toString();
    }

    /**
     * Hängt ein Stichwort an, falls es noch nicht dabei ist. Der Vergleich geht ohne Rücksicht auf
     * Groß- und Kleinschreibung, damit nicht „Urlaub" und „urlaub" nebeneinander stehen.
     */
    public static String add(String tags, String name) {
        String clean = sanitize(name);
        if (clean.isEmpty() || contains(tags, clean)) {
            return tags == null ? "" : tags;
        }
        List<String> all = parse(tags);
        all.add(clean);
        return join(all);
    }

    /** Entfernt ein Stichwort; ein nicht vorhandenes lässt den Wert unverändert. */
    public static String remove(String tags, String name) {
        String clean = sanitize(name);
        List<String> out = new ArrayList<>();
        for (String have : parse(tags)) {
            if (!have.equalsIgnoreCase(clean)) {
                out.add(have);
            }
        }
        return join(out);
    }

    /** {@code true}, wenn die Buchung dieses Stichwort trägt – das ist der Filter. */
    public static boolean contains(String tags, String name) {
        String clean = sanitize(name);
        if (clean.isEmpty()) {
            return true; // kein Stichwort gewählt: alles passt
        }
        for (String have : parse(tags)) {
            if (have.equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    /** Zahl der Stichwörter. */
    public static int count(String tags) {
        return parse(tags).size();
    }

    /** Alle Namen hintereinander, durch Komma getrennt. */
    public static String names(String tags) {
        List<String> all = parse(tags);
        StringBuilder sb = new StringBuilder();
        for (String name : all) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    /**
     * Der Text für die Zeile in der Buchungsmaske: die Namen, solange sie in {@code maxChars}
     * passen, sonst nur die Anzahl. So sieht man im Alltag, worum es geht, ohne dass die Zeile bei
     * vielen Stichwörtern ausufert.
     */
    public static String label(String tags, int maxChars) {
        int n = count(tags);
        if (n == 0) {
            return "";
        }
        String names = names(tags);
        return names.length() <= maxChars ? names : String.valueOf(n);
    }

    /**
     * Räumt einen Namen auf: Rand-Leerzeichen weg, und das Trennzeichen wird zum Leerzeichen.
     * KMyMoney ließe ein {@code |} im Namen zu; die App bietet ein solches Stichwort dann eben
     * unter dem bereinigten Namen an, statt an der eigenen Trennung zu zerbrechen.
     */
    public static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        return name.replace(SEP, ' ').trim();
    }

    /** Kleingeschriebene Fassung für Mengenvergleiche (etwa „schon vergeben?"). */
    public static String key(String name) {
        return sanitize(name).toLowerCase(Locale.GERMANY);
    }
}
