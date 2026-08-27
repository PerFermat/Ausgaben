package de.spahr.ausgaben.db;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Die Zuordnung Geldbuchung ↔ Depot-Bewegung.
 *
 * <p>Sie entscheidet, was beim Löschen mitgeht. Trifft sie daneben, verschwindet entweder eine fremde
 * Bewegung oder es bleibt eine ohne Buchung stehen — und dann stimmt der Depotwert nicht mehr mit dem
 * Kontostand zusammen.</p>
 */
public class SecurityTxMatchTest {

    private static long tag(int jahr, int monat, int tag, int stunde) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag, stunde, 0);
        return c.getTimeInMillis();
    }

    /** Ein Wertpapierkauf, wie ihn die Maske im Geldkonto anlegt. */
    private static Booking kauf() {
        Booking b = new Booking();
        b.id = 42;
        b.account = "Girokonto";
        b.isTransfer = true;
        b.transferAccount = "Vanguard FTSE All-World";
        b.isIncome = false;
        b.amountCents = 100_250L;
        b.createdAt = tag(2026, 8, 19, 9);
        return b;
    }

    /** Die eingelesene Gegenbewegung: kein {@code bookingId}, nur Inhalt. */
    private static SecurityTx bewegung() {
        SecurityTx tx = new SecurityTx();
        tx.id = 7;
        tx.depot = "Depot";
        tx.securityName = "Vanguard FTSE All-World";
        tx.moneyAccount = "Girokonto";
        tx.date = tag(2026, 8, 19, 0);
        tx.netCents = 100_250L;
        tx.amountCents = 100_250L;
        tx.action = "buy";
        return tx;
    }

    @Test
    public void dieVerknuepfungGewinnt() {
        SecurityTx verknuepft = bewegung();
        verknuepft.id = 1;
        verknuepft.bookingId = 42;
        // Sieht inhaltlich gar nicht passend aus – die Verknüpfung zählt trotzdem.
        verknuepft.securityName = "Anderes Papier";
        verknuepft.netCents = 5;

        SecurityTx inhaltlich = bewegung();
        List<SecurityTx> alle = Arrays.asList(inhaltlich, verknuepft);

        assertSame(verknuepft, SecurityTxMatch.forBooking(kauf(), alle));
    }

    @Test
    public void ohneVerknuepfungEntscheidetDerInhalt() {
        SecurityTx tx = bewegung();
        assertSame(tx, SecurityTxMatch.forBooking(kauf(), Collections.singletonList(tx)));
    }

    /** Die Uhrzeit unterscheidet sich zwangsläufig – Bewegung und Buchung kommen aus zwei Quellen. */
    @Test
    public void dieUhrzeitStoertNicht() {
        SecurityTx tx = bewegung();
        tx.date = tag(2026, 8, 19, 23);
        assertSame(tx, SecurityTxMatch.forBooking(kauf(), Collections.singletonList(tx)));
    }

    /** Bei einem Verkauf ist die Buchung eine Einnahme; das Vorzeichen darf nicht dazwischenkommen. */
    @Test
    public void dasVorzeichenStoertNicht() {
        Booking verkauf = kauf();
        verkauf.isIncome = true;
        SecurityTx tx = bewegung();
        tx.action = "sell";
        assertSame(tx, SecurityTxMatch.forBooking(verkauf, Collections.singletonList(tx)));
    }

    @Test
    public void einAnderesPapierPasstNicht() {
        SecurityTx tx = bewegung();
        tx.securityName = "iShares Core MSCI World";
        assertNull(SecurityTxMatch.forBooking(kauf(), Collections.singletonList(tx)));
    }

    @Test
    public void einAnderesKontoPasstNicht() {
        SecurityTx tx = bewegung();
        tx.moneyAccount = "Verrechnungskonto";
        assertNull(SecurityTxMatch.forBooking(kauf(), Collections.singletonList(tx)));
    }

    @Test
    public void einAndererTagPasstNicht() {
        SecurityTx tx = bewegung();
        tx.date = tag(2026, 8, 20, 0);
        assertNull(SecurityTxMatch.forBooking(kauf(), Collections.singletonList(tx)));
    }

    @Test
    public void einAndererBetragPasstNicht() {
        SecurityTx tx = bewegung();
        tx.netCents = 100_000L;
        assertNull(SecurityTxMatch.forBooking(kauf(), Collections.singletonList(tx)));
    }

    /**
     * Verglichen wird gegen das Netto — das Geld, das aufs Konto geht. Bei einer Dividende steht das
     * Brutto in {@code amountCents} und darf nicht zufällig treffen.
     */
    @Test
    public void beiDerDividendeZaehltDasNetto() {
        Booking gutschrift = kauf();
        gutschrift.isIncome = true;
        gutschrift.amountCents = 73_953L;

        SecurityTx tx = bewegung();
        tx.action = "dividend";
        tx.amountCents = 90_699L;   // brutto
        tx.netCents = 73_953L;      // netto, so steht es in der Buchung

        assertSame(tx, SecurityTxMatch.forBooking(gutschrift, Collections.singletonList(tx)));
    }

    /** Eine Bewegung, die schon an einer anderen Buchung hängt, wird nicht zweitverwertet. */
    @Test
    public void eineFremdVerknuepfteBewegungBleibtDraussen() {
        SecurityTx tx = bewegung();
        tx.bookingId = 99;
        assertNull(SecurityTxMatch.forBooking(kauf(), Collections.singletonList(tx)));
    }

    /** Zwei ununterscheidbare Bewegungen: die erste gewinnt – das bekannte, dokumentierte Risiko. */
    @Test
    public void beiZweiGleichenGewinntDieErste() {
        SecurityTx erste = bewegung();
        erste.id = 1;
        SecurityTx zweite = bewegung();
        zweite.id = 2;
        assertSame(erste, SecurityTxMatch.forBooking(kauf(), Arrays.asList(erste, zweite)));
    }

    @Test
    public void eineGewoehnlicheBuchungFindetNichts() {
        Booking ausgabe = kauf();
        ausgabe.isTransfer = false;
        ausgabe.transferAccount = "";
        assertNull(SecurityTxMatch.forBooking(ausgabe, Collections.singletonList(bewegung())));
    }
}
