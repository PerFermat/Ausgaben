package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Charakterisierungstests der reinen Budget-Einordnung ({@link BudgetMath}): Der Typ (Einnahme/Ausgabe)
 * steuert die Zuordnung, das Vorzeichen nur den Wert; negativer Netto-Ist wird auf 0 geklemmt. Deckt die
 * Fälle der Planungstabelle ab (Dividenden-Split, Erstattung, Gewinnspiel, negative Einnahme).
 */
public class BudgetMathTest {

    private static final boolean INCOME = true;
    private static final boolean EXPENSE = false;

    // ---- signedMoneyIn: Geldrichtung einer einzelnen Zeile ----

    @Test
    public void signedMoneyIn_incomeBookingIsPositive() {
        assertEquals(100, BudgetMath.signedMoneyIn(INCOME, 100));   // Einzelbuchung Einnahme
    }

    @Test
    public void signedMoneyIn_expenseBookingIsNegative() {
        assertEquals(-100, BudgetMath.signedMoneyIn(EXPENSE, 100)); // Einzelbuchung Ausgabe
    }

    @Test
    public void signedMoneyIn_negativeSplitPartInIncomeBookingFlowsOut() {
        // Steuer-Teil (−20) in einer Dividenden-Einnahmebuchung → Geld raus.
        assertEquals(-20, BudgetMath.signedMoneyIn(INCOME, -20));
    }

    // ---- Fall 1/3: normale Ausgabe/Einnahme ----

    @Test
    public void normalExpense() {
        long net = BudgetMath.signedMoneyIn(EXPENSE, 100);          // −100
        assertEquals(100, BudgetMath.ist(EXPENSE, net));            // +100 Ausgabe
    }

    @Test
    public void normalIncome() {
        long net = BudgetMath.signedMoneyIn(INCOME, 100);           // +100
        assertEquals(100, BudgetMath.ist(INCOME, net));             // +100 Einnahme
    }

    // ---- Fall 5: Steuer:Kapitalertragsteuer (Ausgabe) als Split-Teil in Dividenden-Einnahme ----
    // Netto 80€ = Zinsen:Dividende 100€ + Steuer:Kapitalertragsteuer −20€ (Einnahmebuchung).

    @Test
    public void dividendSplit_taxLandsAsExpenseTwenty() {
        long dividendNet = BudgetMath.signedMoneyIn(INCOME, 100);   // Zinsen:Dividende
        assertEquals(100, BudgetMath.ist(INCOME, dividendNet));

        long taxNet = BudgetMath.signedMoneyIn(INCOME, -20);        // Steuer:Kapitalertragsteuer, amount −20
        assertEquals("Ausgabekategorie in Einnahmebuchung → +20 Ausgabe", 20, BudgetMath.ist(EXPENSE, taxNet));
    }

    // ---- Fall 6: Einnahmekategorie als Split-Teil in einer Ausgabebuchung ----

    @Test
    public void rebateLineInExpenseBooking_landsAsIncome() {
        // Ausgabebuchung mit negativem Teil (Bonus/Rabatt) auf einer Einnahmekategorie.
        long net = BudgetMath.signedMoneyIn(EXPENSE, -15);          // +15 (Geld rein)
        assertEquals(15, BudgetMath.ist(INCOME, net));
    }

    // ---- Fall 2: Erstattung mindert die Ausgabekategorie, bleibt Ausgabe ----

    @Test
    public void refundReducesExpense_staysExpense() {
        long spent = BudgetMath.signedMoneyIn(EXPENSE, 100);        // −100
        long refund = BudgetMath.signedMoneyIn(INCOME, 30);         // +30 (Erstattung als Einnahme gebucht)
        assertEquals(70, BudgetMath.ist(EXPENSE, spent + refund));  // 100 − 30 = 70, bleibt Ausgabe
    }

    // ---- Fall 7: Gewinnspiel:Lose – zugeordnete Gewinne übersteigen die Ausgaben → 0 ----

    @Test
    public void lottery_winningsExceedTickets_clampedToZero() {
        long tickets = BudgetMath.signedMoneyIn(EXPENSE, 10);       // −10 (Lose gekauft)
        long winnings = BudgetMath.signedMoneyIn(INCOME, 50);       // +50 (Gewinn, derselben Ausgabekat. zugeordnet)
        assertEquals(0, BudgetMath.ist(EXPENSE, tickets + winnings)); // −40 → auf 0 geklemmt
    }

    // ---- Fall 4: negative Einnahme bleibt Einnahme, Clamp auf 0 ----

    @Test
    public void negativeIncome_staysIncome_clampedToZero() {
        long got = BudgetMath.signedMoneyIn(INCOME, 40);            // +40
        long returned = BudgetMath.signedMoneyIn(EXPENSE, 100);     // −100 (zurückgezahlt)
        assertEquals(0, BudgetMath.ist(INCOME, got + returned));    // 40 − 100 = −60 → 0
    }
}
