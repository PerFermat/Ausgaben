package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Die Vorzeichen- und Dividendenregeln einer Bewegung — jetzt an einer Stelle.
 *
 * <p>Sie standen dreimal fast gleichlautend im Code: beim Speichern der Erfassungsmaske, in ihrer
 * Dublettenprüfung und im Entwurf der Erkennungsliste. Das ist keine Schönheitsfrage: Läuft eine der
 * Fassungen auseinander, bucht die Maske etwas anderes, als ihre eigene Dublettenprüfung sucht — und
 * der Hinweis „schon gebucht" bliebe aus, obwohl die Bewegung schon dasteht.</p>
 */
public class SecurityTxRulesTest {

    private static SecurityTx bewegung() {
        SecurityTx tx = new SecurityTx();
        tx.depot = "Depot";
        tx.securityKmyId = "S1";
        tx.securityName = "Musterfonds";
        tx.moneyAccount = "Girokonto";
        tx.date = 1_700_000_000_000L;
        return tx;
    }

    @Test
    public void einKaufTraegtSeineStueckePositiv() {
        SecurityTx tx = bewegung();
        tx.applyAmounts(SecurityTx.BUY, 30.0, 100_000L, 100_490L, 490L);

        assertEquals(30.0, tx.shares, 1e-9);
        assertEquals("Brutto ist Anzahl mal Stückpreis, ohne Gebühr", 100_000L, tx.amountCents);
        assertEquals("bezahlt wird Brutto plus Gebühr", 100_490L, tx.netCents);
        assertEquals("die Gebühr steht zusätzlich für sich", 490L, tx.feeCents);
    }

    /** Ein Verkauf bewegt die Stücke aus dem Depot heraus – auch wenn die Eingabe positiv war. */
    @Test
    public void einVerkaufTraegtSeineStueckeNegativ() {
        SecurityTx tx = bewegung();
        tx.applyAmounts(SecurityTx.SELL, 30.0, 100_000L, 99_510L, 490L);

        assertEquals(-30.0, tx.shares, 1e-9);
    }

    /** Und auch dann, wenn sie schon mit Minus hereinkam – zweimal Minus wäre wieder Plus. */
    @Test
    public void einVerkaufDrehtEineSchonNegativeAnzahlNichtZurueck() {
        SecurityTx tx = bewegung();
        tx.applyAmounts(SecurityTx.SELL, -30.0, 100_000L, 99_510L, 490L);

        assertEquals(-30.0, tx.shares, 1e-9);
    }

    /**
     * Eine Dividende bewegt keine Stücke — der Bestand am Ex-Tag steht nicht in der Abrechnung — und
     * führt keine Gebühr: Ihre Abzüge stecken in der Differenz zwischen Brutto und Netto.
     */
    @Test
    public void eineDividendeBewegtKeineStueckeUndFuehrtKeineGebuehr() {
        SecurityTx tx = bewegung();
        tx.applyAmounts(SecurityTx.DIVIDEND, 30.0, 10_000L, 7_400L, 2_600L);

        assertEquals(0.0, tx.shares, 1e-9);
        assertEquals("Brutto", 10_000L, tx.amountCents);
        assertEquals("Netto ist hier das gutgeschriebene Geld", 7_400L, tx.netCents);
        assertEquals("keine Gebühr", 0L, tx.feeCents);
    }

    @Test
    public void ohneAnzahlZaehltNull() {
        SecurityTx tx = bewegung();
        tx.applyAmounts(SecurityTx.BUY, null, 100_000L, 100_000L, 0L);

        assertEquals(0.0, tx.shares, 1e-9);
    }

    // ---- Die Geldbuchung ----

    /** Beim Kauf verlässt das Geld das Konto. */
    @Test
    public void derKaufIstEineAusgabeUeberDenGesamtbetrag() {
        SecurityTx tx = bewegung();
        tx.applyAmounts(SecurityTx.BUY, 30.0, 100_000L, 100_490L, 490L);

        Booking b = tx.toMoneyBooking(100_490L);

        assertFalse("Geld geht weg", b.isIncome);
        assertTrue("als Umbuchung aufs Wertpapier", b.isTransfer);
        assertEquals("Girokonto", b.account);
        assertEquals("Musterfonds", b.transferAccount);
        assertEquals("der Gesamtbetrag samt Gebühr, nicht das Brutto", 100_490L, b.amountCents);
        assertEquals("und genau das steht auch in netCents", tx.netCents, b.amountCents);
        assertEquals(tx.date, b.createdAt);
    }

    /** Bei Verkauf und Dividende kommt es an. */
    @Test
    public void verkaufUndDividendeSindEinnahmen() {
        SecurityTx verkauf = bewegung();
        verkauf.applyAmounts(SecurityTx.SELL, 30.0, 100_000L, 99_510L, 490L);
        assertTrue(verkauf.toMoneyBooking(99_510L).isIncome);

        SecurityTx dividende = bewegung();
        dividende.applyAmounts(SecurityTx.DIVIDEND, null, 10_000L, 7_400L, 0L);
        Booking b = dividende.toMoneyBooking(7_400L);
        assertTrue(b.isIncome);
        assertEquals("gutgeschrieben wird das Netto", 7_400L, b.amountCents);
    }

    /**
     * Die eigentliche Zusicherung: {@code netCents} <b>ist</b> der Betrag der Geldbuchung.
     *
     * <p>{@code SecurityTxMatch} vergleicht genau diese beiden Zahlen, um Bewegung und Buchung
     * einander zuzuordnen. Bis 1.12 stand in {@code netCents} bei Kauf und Verkauf noch einmal der
     * Bruttobetrag; bei jeder Bewegung <b>mit Gebühr</b> wichen sie voneinander ab, die Zuordnung
     * scheiterte, und eine gelöschte Buchung ließ ihre Bewegung stehen.</p>
     */
    @Test
    public void netCentsIstDerBetragDerGeldbuchung() {
        SecurityTx kauf = bewegung();
        kauf.applyAmounts(SecurityTx.BUY, 30.0, 100_000L, 0L, 490L);
        assertEquals(kauf.netCents, kauf.toMoneyBooking(kauf.netCents).amountCents);
        assertEquals(100_490L, kauf.netCents);

        SecurityTx verkauf = bewegung();
        verkauf.applyAmounts(SecurityTx.SELL, 30.0, 100_000L, 0L, 490L);
        assertEquals("beim Verkauf geht die Gebühr von der Gutschrift ab", 99_510L, verkauf.netCents);

        SecurityTx dividende = bewegung();
        dividende.applyAmounts(SecurityTx.DIVIDEND, null, 10_000L, 7_400L, 0L);
        assertEquals("bei der Dividende ist es die Gutschrift", 7_400L, dividende.netCents);
    }

    /** Ohne Gebühr fallen Brutto und bewegtes Geld zusammen – dann war auch früher nichts zu sehen. */
    @Test
    public void ohneGebuehrSindBruttoUndBewegtesGeldGleich() {
        SecurityTx tx = bewegung();
        tx.applyAmounts(SecurityTx.SELL, 30.0, 100_000L, 0L, 0L);

        assertEquals(100_000L, tx.amountCents);
        assertEquals(100_000L, tx.netCents);
    }
}
