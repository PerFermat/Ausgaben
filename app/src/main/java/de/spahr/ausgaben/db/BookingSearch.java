package de.spahr.ausgaben.db;

import java.util.Locale;

/**
 * Freitext-Suche über eine Buchung: trifft auf <b>Empfänger, Notiz oder Kategorie</b> (Teilstring, Groß-/
 * Kleinschreibung egal). Bewusst an einer Stelle, damit Liste und Auswertung nicht auseinanderlaufen –
 * beide filtern über dieselbe Methode.
 */
public final class BookingSearch {

    private BookingSearch() {
    }

    /** {@code true}, wenn {@code needle} leer ist oder in Empfänger/Notiz/Kategorie vorkommt. */
    public static boolean matches(Booking b, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        String n = needle.toLowerCase(Locale.GERMANY);
        return contains(b.payee, n) || contains(b.note, n) || contains(b.category, n);
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.GERMANY).contains(lowerNeedle);
    }
}
