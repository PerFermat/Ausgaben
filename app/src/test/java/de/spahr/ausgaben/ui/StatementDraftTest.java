package de.spahr.ausgaben.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.SecurityTx;

/**
 * Ein Eintrag der Erkennungsliste ({@link StatementDraft}): was aus einer Abrechnung wurde, was noch
 * fehlt, und was daraus gebucht wird.
 *
 * <p>Der Prüfstein ist die Farbe der Zeile — sie hängt allein an {@link StatementDraft#problem()}, und
 * die Reihenfolge der Gründe entscheidet, was der Nutzer zu lesen bekommt.</p>
 */
public class StatementDraftTest {

    /** Ein Kauf, wie ihn eine Vorlage liefert: Stückzahl, Kurs, Gebühr und Gesamtsumme. */
    private static StatementDraft kauf() {
        StatementDraft d = new StatementDraft();
        d.isin = "IE00B3RBWM25";
        d.depot = "Depot";
        d.kmyId = "S000001";
        d.securityName = "Vanguard All-World";
        d.action = StatementDraft.BUY;
        d.dateMillis = 1_755_000_000_000L;
        d.shares = 6.09607;
        d.price = 164.04;
        d.feeCents = 0L;
        d.netCents = 100_000L;
        d.moneyAccount = "Giro";
        return d;
    }

    @Test
    public void kaufIstVollstaendigUndErgibtDenBetragAusDerGesamtsumme() {
        StatementDraft d = kauf();
        d.resolve();

        // Ohne Gebühr sind Betrag und Gesamtsumme dasselbe – gerechnet wird aus der abgelesenen Summe,
        // nicht aus Anzahl × Kurs (das gäbe 100.000,05 Cent).
        assertEquals(Long.valueOf(100_000L), d.grossCents);
        assertEquals(Long.valueOf(100_000L), d.netCents);
        assertEquals(6.09607, d.shares, 1e-9);
        assertEquals(164.04, d.price, 1e-9);
        assertEquals(0, d.problem());
        assertTrue(d.isBookable());
    }

    @Test
    public void kaufMitGebuehrZiehtSieVonDerGesamtsummeAb() {
        StatementDraft d = kauf();
        d.feeCents = 490L;
        d.resolve();

        assertEquals(Long.valueOf(99_510L), d.grossCents);
        assertEquals(Long.valueOf(100_000L), d.netCents);
    }

    @Test
    public void dividendeErgaenztDasBruttoAusNettoUndSteuer() {
        StatementDraft d = new StatementDraft();
        d.kmyId = "S000002";
        d.isin = "IE00B6YX5D40";
        d.action = StatementDraft.DIVIDEND;
        d.dateMillis = 1_755_000_000_000L;
        d.feeCents = 16_699L;
        d.netCents = 73_953L;
        d.moneyAccount = "Giro";
        d.resolve();

        assertEquals(Long.valueOf(90_652L), d.grossCents);
        assertEquals(0, d.problem());
        // Bei einer Dividende bleibt die Stückzahl leer: der Bestand am Ex-Tag steht nicht im Dokument.
        assertEquals(null, d.shares);
    }

    /**
     * Eine Dividende innerhalb des Freibetrags: die Bank zieht nichts ab, die Steuerregel hat gesucht und
     * nichts gefunden, also steht dort 0. Gerechnet wird daraus das Brutto — und das ist der Nettobetrag.
     *
     * <p>Ein Steuersatz aus den Einstellungen hat hier nichts zu suchen: er würde ein Brutto erfinden,
     * das in der Abrechnung nirgends steht.</p>
     */
    @Test
    public void ohneAbgezogeneSteuerIstDasBruttoDasNetto() {
        StatementDraft d = new StatementDraft();
        d.kmyId = "S000003";
        d.isin = "LU1242369327";
        d.action = StatementDraft.DIVIDEND;
        d.dateMillis = 1_772_000_000_000L;
        d.feeCents = 0L;
        d.netCents = 6316L;
        d.moneyAccount = "Giro";
        d.resolve();

        assertEquals(Long.valueOf(6316L), d.grossCents);
        assertEquals(Long.valueOf(0L), d.feeCents);
        assertEquals(0, d.problem());
        assertEquals(6316L, d.toTx().amountCents);
        assertEquals(6316L, d.toBooking().amountCents);
    }

    @Test
    public void unlesbareDateiNenntNurDenEinenGrund() {
        StatementDraft d = new StatementDraft();
        d.fileName = "abrechnung.pdf";
        d.failure = R.string.statement_unreadable;
        // Ohne ISIN, ohne Wertpapier, ohne alles – genannt wird trotzdem nur, woran es wirklich liegt.
        assertEquals(R.string.statement_unreadable, d.problem());
    }

    @Test
    public void ohneWertpapierIstDasDieErsteFrage() {
        StatementDraft d = kauf();
        d.kmyId = "";
        d.resolve();
        assertEquals(R.string.statement_problem_security, d.problem());
    }

    @Test
    public void ohneIsinNenntEsDieIsinUndNichtDasWertpapier() {
        StatementDraft d = kauf();
        d.isin = null;
        d.kmyId = "";
        assertEquals(R.string.statement_problem_isin, d.problem());
    }

    @Test
    public void fehlendeAngabenWerdenDerReiheNachGenannt() {
        StatementDraft d = kauf();
        d.action = null;
        assertEquals(R.string.statement_problem_action, d.problem());

        d.action = StatementDraft.BUY;
        d.dateMillis = -1;
        assertEquals(R.string.statement_problem_date, d.problem());

        // Ohne Gesamtsumme, aber mit Anzahl und Kurs fehlt nichts – der Betrag ergibt sich daraus.
        d.dateMillis = 1_755_000_000_000L;
        d.netCents = null;
        d.resolve();
        assertEquals(0, d.problem());

        // Erst wenn auch die zweite Gleichung nichts hergibt, ist wirklich kein Betrag zu bekommen.
        d.netCents = null;
        d.grossCents = null;
        d.shares = null;
        d.price = null;
        d.resolve();
        assertEquals(R.string.statement_problem_amounts, d.problem());
    }

    @Test
    public void kaufOhneStueckzahlIstNichtBuchbar() {
        StatementDraft d = kauf();
        d.shares = null;
        d.price = null;
        d.resolve();
        assertEquals(R.string.statement_problem_shares, d.problem());
        assertFalse(d.isBookable());
    }

    @Test
    public void ohneGegenkontoFehltDieGegenbuchung() {
        StatementDraft d = kauf();
        d.moneyAccount = "  ";
        d.resolve();
        assertEquals(R.string.statement_problem_account, d.problem());
    }

    @Test
    public void widerspruechlicheGeldfelderWerdenNichtStillGerechnet() {
        StatementDraft d = new StatementDraft();
        d.kmyId = "S000002";
        d.isin = "IE00B6YX5D40";
        d.action = StatementDraft.DIVIDEND;
        d.dateMillis = 1_755_000_000_000L;
        d.grossCents = 90_652L;
        d.feeCents = 16_699L;
        d.netCents = 50_000L;
        d.moneyAccount = "Giro";
        d.resolve();

        assertTrue(d.conflict);
        assertEquals(R.string.security_tx_conflict, d.problem());
        // Nichts angetastet: berichtigt wird in der Maske, nicht hinter dem Rücken des Nutzers.
        assertEquals(Long.valueOf(50_000L), d.netCents);
    }

    @Test
    public void verkaufBuchtDieStueckeNegativUndDasGeldAlsEingang() {
        StatementDraft d = kauf();
        d.action = StatementDraft.SELL;
        d.feeCents = 490L;
        d.resolve();

        SecurityTx tx = d.toTx();
        assertEquals(-6.09607, tx.shares, 1e-9);
        assertEquals(100_490L, tx.amountCents);
        assertEquals(490L, tx.feeCents);

        Booking b = d.toBooking();
        assertTrue(b.isIncome);
        assertTrue(b.isTransfer);
        assertEquals(100_000L, b.amountCents);
    }

    @Test
    public void kaufBuchtDasGeldAlsAusgangUndInHoeheDerGesamtsumme() {
        StatementDraft d = kauf();
        d.feeCents = 490L;
        d.resolve();

        Booking b = d.toBooking();
        assertFalse(b.isIncome);
        assertEquals(100_000L, b.amountCents);
    }

    @Test
    public void beiDerDividendeGehtNurDasNettoAufsKonto() {
        StatementDraft d = new StatementDraft();
        d.action = StatementDraft.DIVIDEND;
        d.securityName = "SPDR";
        d.moneyAccount = "Giro";
        d.feeCents = 16_699L;
        d.netCents = 73_953L;
        d.dateMillis = 1_755_000_000_000L;
        d.resolve();

        SecurityTx tx = d.toTx();
        assertEquals(0.0, tx.shares, 1e-9);
        assertEquals(90_652L, tx.amountCents);
        assertEquals(73_953L, tx.netCents);
        // Die Steuer steht bei einer Dividende in der Ertragsbuchung, nicht als Gebühr der Bewegung.
        assertEquals(0L, tx.feeCents);
        assertEquals(73_953L, d.toBooking().amountCents);
    }

    // ---- Doppelt eingelesene Abrechnungen ----

    /**
     * Die <b>erste</b> bleibt unmarkiert, jede weitere gilt als Wiederholung. Andersherum wüsste der
     * Nutzer nicht, welche der beiden Zeilen er löschen soll.
     */
    @Test
    public void nurDieWiederholungWirdMarkiert() {
        StatementDraft a = kauf();
        StatementDraft b = kauf();
        a.resolve();
        b.resolve();
        java.util.List<StatementDraft> liste = java.util.Arrays.asList(a, b);

        StatementDraft.markSelectionDuplicates(liste);

        assertFalse(a.dupSelected);
        assertTrue(b.dupSelected);
        assertEquals(R.string.statement_dup_selected, b.duplicateHint());
        assertEquals(0, a.duplicateHint());
    }

    /** Ein Hinweis, keine Sperre: gespeichert werden darf die Doppelung trotzdem. */
    @Test
    public void eineDoppelungBleibtBuchbar() {
        StatementDraft a = kauf();
        StatementDraft b = kauf();
        a.resolve();
        b.resolve();
        StatementDraft.markSelectionDuplicates(java.util.Arrays.asList(a, b));

        assertTrue(b.isBookable());
        assertEquals(0, b.problem());
    }

    /**
     * Wird die erste Zeile gelöscht, ist die zweite keine Wiederholung mehr – die Markierung darf nicht
     * kleben bleiben, sonst stünde die letzte verbliebene Zeile grundlos in Gelb.
     */
    @Test
    public void ohneVorgaengerFaelltDieMarkierungWiederWeg() {
        StatementDraft a = kauf();
        StatementDraft b = kauf();
        a.resolve();
        b.resolve();
        StatementDraft.markSelectionDuplicates(java.util.Arrays.asList(a, b));
        assertTrue(b.dupSelected);

        StatementDraft.markSelectionDuplicates(java.util.Collections.singletonList(b));

        assertFalse(b.dupSelected);
        assertEquals(0, b.duplicateHint());
    }

    /** Ein anderer Betrag ist eine andere Buchung – zwei Käufe am selben Tag sind erlaubt. */
    @Test
    public void andererBetragIstKeineDoppelung() {
        StatementDraft a = kauf();
        StatementDraft b = kauf();
        b.netCents = 100_001L;
        a.resolve();
        b.resolve();

        StatementDraft.markSelectionDuplicates(java.util.Arrays.asList(a, b));

        assertFalse(b.dupSelected);
    }

    /** Was noch gar nicht buchbar ist, kann auch keine Doppelung sein. */
    @Test
    public void unvollstaendigeEintraegeBleibenAussenVor() {
        StatementDraft a = kauf();
        StatementDraft b = kauf();
        a.moneyAccount = "";
        b.moneyAccount = "";
        a.resolve();
        b.resolve();

        StatementDraft.markSelectionDuplicates(java.util.Arrays.asList(a, b));

        assertFalse(b.dupSelected);
        assertEquals(R.string.statement_problem_account, b.problem());
    }

    /** Die schon gebuchte Doppelung ist der schwerere Fall und sticht die in der Auswahl. */
    @Test
    public void schonGebuchtStichtDieAuswahl() {
        StatementDraft d = kauf();
        d.dupSelected = true;
        d.dupBooked = true;

        assertEquals(R.string.statement_dup_booked, d.duplicateHint());
    }
}
