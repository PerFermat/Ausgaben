package de.spahr.ausgaben.db;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prüft, ob eine Buchung (auch über ihre Splits) zu einer Kategorie gehört, und ermittelt den dabei
 * anzuzeigenden Betrag. Gleiche Logik wie {@code MainActivity}s Kategorie-Filter (dort instanzgebunden),
 * hier als eigenständige Utility für den Buchungs-Drilldown in Kategorien- und Budget-Ansicht.
 */
public final class CategoryBookingFilter {

    private CategoryBookingFilter() {
    }

    /** Hauptkategorie: exakt oder als Präfix inkl. Unterkategorien. Unterkategorie: nur exakt. */
    public static boolean matches(String cat, String filterCategory, boolean isMain) {
        if (cat == null) {
            cat = "";
        }
        if (isMain) {
            return cat.equalsIgnoreCase(filterCategory)
                    || cat.toLowerCase(Locale.GERMANY).startsWith(
                            filterCategory.toLowerCase(Locale.GERMANY) + ":");
        }
        return cat.equalsIgnoreCase(filterCategory);
    }

    /**
     * {@code true}, wenn die Zeile vom erwarteten Typ ist. Maßgeblich ist der Typ <b>der Zeile selbst</b>
     * ({@code rowIsIncome}, siehe {@link Booking#categoryIsIncome}/{@link BookingSplit#categoryIsIncome})
     * – nur wenn der unbekannt ist (NULL, Zeile vor dieser Migration), fällt der Check auf den globalen
     * {@code categoryTypes}-Typ zurück. Der globale Typ allein reicht nicht: kMyMoney erlaubt dieselbe
     * Kategorie-Bezeichnung unabhängig im Einnahme- und im Ausgabe-Baum (z. B. „Versicherung:
     * Krankenzusatz"), die globale Map kennt aber nur einen Typ pro Text und würde sonst Buchungen des
     * jeweils anderen Typs fälschlich mit zuordnen. Ohne erwarteten Typ ({@code null}) gilt kein Ausschluss.
     */
    private static boolean typeMatches(Boolean rowIsIncome, String cat, Map<String, Boolean> categoryTypes,
            Boolean expectIncome) {
        if (expectIncome == null) {
            return true;
        }
        Boolean actual = rowIsIncome;
        if (actual == null && categoryTypes != null) {
            actual = categoryTypes.get(cat);
        }
        return actual == null || actual.equals(expectIncome);
    }

    /** Treffer, wenn die (Haupt-)Kategorie der Buchung oder eine Teilkategorie eines Splits passt. */
    public static boolean matchesBooking(Booking b, Map<Long, List<BookingSplit>> splitsByBooking,
            String filterCategory, boolean isMain) {
        return matchesBooking(b, splitsByBooking, filterCategory, isMain, null, null);
    }

    /** Wie {@link #matchesBooking(Booking, Map, String, boolean)}, zusätzlich typgeprüft (Einnahme/Ausgabe). */
    public static boolean matchesBooking(Booking b, Map<Long, List<BookingSplit>> splitsByBooking,
            String filterCategory, boolean isMain, Map<String, Boolean> categoryTypes, Boolean expectIncome) {
        if (matches(b.category, filterCategory, isMain)
                && typeMatches(b.categoryIsIncome, b.category, categoryTypes, expectIncome)) {
            return true;
        }
        List<BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts != null) {
            for (BookingSplit p : parts) {
                if (matches(p.category, filterCategory, isMain)
                        && typeMatches(p.categoryIsIncome, p.category, categoryTypes, expectIncome)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Voller vorzeichenbehafteter Betrag, oder bei einer Splitbuchung nur die Summe der passenden
     * Teilbeträge (wichtig, damit die Summe der Drilldown-Zeilen der Kategorie-Summe entspricht).
     */
    public static long displaySigned(Booking b, Map<Long, List<BookingSplit>> splitsByBooking,
            String filterCategory, boolean isMain, long fullSigned) {
        return displaySigned(b, splitsByBooking, filterCategory, isMain, fullSigned, null, null);
    }

    /** Wie {@link #displaySigned(Booking, Map, String, boolean, long)}, zusätzlich typgeprüft. */
    public static long displaySigned(Booking b, Map<Long, List<BookingSplit>> splitsByBooking,
            String filterCategory, boolean isMain, long fullSigned,
            Map<String, Boolean> categoryTypes, Boolean expectIncome) {
        List<BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts == null || parts.isEmpty()) {
            return fullSigned;
        }
        long sum = 0;
        boolean any = false;
        for (BookingSplit p : parts) {
            if (matches(p.category, filterCategory, isMain)
                    && typeMatches(p.categoryIsIncome, p.category, categoryTypes, expectIncome)) {
                sum += b.isIncome ? p.amountCents : -p.amountCents;
                any = true;
            }
        }
        return any ? sum : fullSigned;
    }

    /** {@code true}, wenn der angezeigte Betrag nur aus passenden Splits stammt (nicht der volle Betrag). */
    public static boolean isPartial(Booking b, Map<Long, List<BookingSplit>> splitsByBooking,
            String filterCategory, boolean isMain) {
        return isPartial(b, splitsByBooking, filterCategory, isMain, null, null);
    }

    /** Wie {@link #isPartial(Booking, Map, String, boolean)}, zusätzlich typgeprüft. */
    public static boolean isPartial(Booking b, Map<Long, List<BookingSplit>> splitsByBooking,
            String filterCategory, boolean isMain, Map<String, Boolean> categoryTypes, Boolean expectIncome) {
        List<BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts == null || parts.isEmpty()) {
            return false;
        }
        for (BookingSplit p : parts) {
            if (!(matches(p.category, filterCategory, isMain)
                    && typeMatches(p.categoryIsIncome, p.category, categoryTypes, expectIncome))) {
                return true;   // mindestens ein Split gehört zu einer anderen Kategorie oder einem anderen Typ
            }
        }
        return false;
    }
}
