package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Die Vorzeichenregel für geplante Buchungen in der Auswertung „Wofür geht mein Geld"
 * ({@link PlannedExpense}).
 */
public class PlannedExpenseTest {

    private static final int AUSZAHLUNG = ScheduledTransaction.KIND_EXPENSE;
    private static final int EINZAHLUNG = ScheduledTransaction.KIND_INCOME;
    private static final int UMBUCHUNG = ScheduledTransaction.KIND_TRANSFER;

    @Test
    public void geplanteAuszahlungIstEineAusgabe() {
        assertEquals(5000, PlannedExpense.expenseCents(AUSZAHLUNG, 5000));
    }

    /**
     * Der Fehlerfall: die Kapitalertragssteuer steht als negativer Teilbetrag in einer geplanten
     * Dividende. Vorher fiel die ganze Einzahlung weg und die Steuer mit ihr.
     */
    @Test
    public void steuerInEinerGeplantenEinzahlungIstEineAusgabe() {
        assertEquals(1825, PlannedExpense.expenseCents(EINZAHLUNG, -1825));
    }

    @Test
    public void dieDividendeSelbstIstKeineAusgabe() {
        assertEquals(0, PlannedExpense.expenseCents(EINZAHLUNG, 10000));
    }

    /** Spiegelbild der Steuer: ein negativer Teil in einer Auszahlung ist ein Zufluß, keine Ausgabe. */
    @Test
    public void erstattungInEinerGeplantenAuszahlungIstKeineAusgabe() {
        assertEquals(0, PlannedExpense.expenseCents(AUSZAHLUNG, -2000));
    }

    @Test
    public void umbuchungenZaehlenNie() {
        assertEquals(0, PlannedExpense.expenseCents(UMBUCHUNG, 5000));
        assertEquals(0, PlannedExpense.expenseCents(UMBUCHUNG, -5000));
    }

    @Test
    public void keinBetragIstKeineAusgabe() {
        assertEquals(0, PlannedExpense.expenseCents(AUSZAHLUNG, 0));
        assertEquals(0, PlannedExpense.expenseCents(EINZAHLUNG, 0));
    }
}
