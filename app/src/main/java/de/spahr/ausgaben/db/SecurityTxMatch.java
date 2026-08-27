package de.spahr.ausgaben.db;

import java.util.List;
import java.util.Locale;

/**
 * Welche Depot-Bewegung gehört zu dieser Geldbuchung?
 *
 * <p>Nötig, weil eine Wertpapier-Buchung im Geldkonto und die Bewegung im Depot zwei Zeilen sind, die
 * gemeinsam entstehen und gemeinsam verschwinden müssen. Bliebe eine ohne die andere zurück, stimmte der
 * Depotwert nicht mehr mit dem Kontostand zusammen.</p>
 *
 * <p>Zwei Fälle, und nur der erste hat eine Verknüpfung:</p>
 * <ul>
 *   <li><b>In der App erfasst:</b> {@link SecurityTx#bookingId} zeigt auf die Buchung. Eindeutig.</li>
 *   <li><b>Aus KMyMoney eingelesen:</b> dort steht {@code bookingId = 0} — Bewegung und Buchung kommen
 *       aus zwei getrennten Importen und wissen nichts voneinander. Dann bleibt nur der Inhalt:
 *       Wertpapier, Geldkonto, Kalendertag und Betrag.</li>
 * </ul>
 *
 * <p>Reine Rechenklasse ohne Android-Bezug.</p>
 */
public final class SecurityTxMatch {

    private SecurityTxMatch() {
    }

    /**
     * Die Bewegung aus {@code candidates}, die zu {@code booking} gehört — oder {@code null}.
     *
     * <p><b>Bekanntes Risiko:</b> Zwei gleiche Bewegungen desselben Papiers am selben Tag über denselben
     * Betrag sind über den Inhalt nicht zu unterscheiden; dann gewinnt die erste. Dasselbe Risiko trägt
     * die Lösch-Synchronisierung in {@code KmyExporter.removeTransactions} seit jeher, und aus demselben
     * Grund: die App kennt die Transaktions-id der Datei nicht.</p>
     */
    public static SecurityTx forBooking(Booking booking, List<SecurityTx> candidates) {
        if (booking == null || candidates == null) {
            return null;
        }
        // Die Verknüpfung schlägt den Inhalt: sie ist eindeutig, auch wenn zufällig zwei Bewegungen
        // gleich aussehen.
        for (SecurityTx tx : candidates) {
            if (tx != null && tx.bookingId == booking.id && booking.id > 0) {
                return tx;
            }
        }
        for (SecurityTx tx : candidates) {
            if (matchesByContent(booking, tx)) {
                return tx;
            }
        }
        return null;
    }

    /**
     * Ob Bewegung und Buchung inhaltlich dieselbe Sache beschreiben.
     *
     * <p>Verglichen wird der Betrag gegen {@link SecurityTx#netCents} — das ist das Geld, das aufs Konto
     * geht, und genau das steht in der Buchung. Der Bruttobetrag einer Dividende steht dort nicht.</p>
     */
    private static boolean matchesByContent(Booking booking, SecurityTx tx) {
        if (tx == null || !booking.isTransfer) {
            return false;
        }
        // Eine Bewegung, die schon zu einer anderen Buchung gehört, kommt nicht in Frage.
        if (tx.bookingId > 0 && tx.bookingId != booking.id) {
            return false;
        }
        return equalsIgnoreCase(tx.securityName, booking.transferAccount)
                && equalsIgnoreCase(tx.moneyAccount, booking.account)
                && SecurityTx.sameDay(tx.date, booking.createdAt)
                && Math.abs(tx.netCents) == Math.abs(booking.amountCents);
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }
}
