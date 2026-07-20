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
     * {@code true}, wenn {@code cat} laut {@code categoryTypes} vom erwarteten Typ ist. Ohne Typ-Info
     * (Map/erwarteter Typ {@code null}, oder Kategorie nicht in der Map) gilt kein Ausschluss – wichtig,
     * da z. B. Einnahme- und Ausgabekategorien in kMyMoney denselben Namen tragen können (etwa „Geschenke"
     * als Einnahme und „Geschenke:Geschäft" als Ausgabe): ohne diesen Check würde die Präfix-Zuordnung der
     * Hauptkategorie fälschlich auch die gleichnamige Kategorie des jeweils anderen Typs einschließen.
     */
    private static boolean typeMatches(String cat, Map<String, Boolean> categoryTypes, Boolean expectIncome) {
        if (expectIncome == null || categoryTypes == null) {
            return true;
        }
        Boolean actual = categoryTypes.get(cat);
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
        if (matches(b.category, filterCategory, isMain) && typeMatches(b.category, categoryTypes, expectIncome)) {
            return true;
        }
        List<BookingSplit> parts = splitsByBooking.get(b.id);
        if (parts != null) {
            for (BookingSplit p : parts) {
                if (matches(p.category, filterCategory, isMain)
                        && typeMatches(p.category, categoryTypes, expectIncome)) {
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
            if (matches(p.category, filterCategory, isMain) && typeMatches(p.category, categoryTypes, expectIncome)) {
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
                    && typeMatches(p.category, categoryTypes, expectIncome))) {
                return true;   // mindestens ein Split gehört zu einer anderen Kategorie oder einem anderen Typ
            }
        }
        return false;
    }
}
