package de.spahr.ausgaben.db;

/**
 * Der Ausgabenanteil einer geplanten Buchungszeile – die Vorzeichenregel der Auswertung „Wofür geht mein
 * Geld", für geplante Buchungen ausgesprochen.
 *
 * <p>Die Ist-Zahlen dieser Auswertung entstehen in {@link BookingDao#getCategoryActuals} nach der Regel
 * {@code CASE WHEN is_income THEN amount ELSE -amount END}, und gezählt wird davon nur, was negativ
 * bleibt. Hier steht dieselbe Regel für {@link ScheduledTransaction} und {@link ScheduledSplit}, damit
 * geplante und gebuchte Beträge nicht nach zweierlei Maß gerechnet werden.</p>
 *
 * <p>Daran hängt ein Fall, der sonst durchfällt: die Kapitalertragssteuer steckt als <b>negativer</b>
 * Teilbetrag in einer <b>Einzahlung</b> (Dividende brutto, Steuer abgezogen). Sie ist eine Ausgabe,
 * obwohl die Buchung eine Einnahme ist – und umgekehrt ist eine Erstattung in einer Auszahlung keine.</p>
 */
public final class PlannedExpense {

    private PlannedExpense() {
    }

    /**
     * Der Ausgabenanteil in Cent, immer ≥ 0; {@code 0} heißt „keine Ausgabe" (Zufluß oder Umbuchung).
     *
     * @param kind        {@link ScheduledTransaction#kind}
     * @param amountCents Betrag der Zeile – bei einer geplanten Buchung immer positiv, bei einem
     *                    Splitteil vorzeichenbehaftet
     */
    public static long expenseCents(int kind, long amountCents) {
        if (kind == ScheduledTransaction.KIND_TRANSFER) {
            return 0;   // Umbuchungen verschieben nur, sie geben nichts aus
        }
        long signed = kind == ScheduledTransaction.KIND_INCOME ? amountCents : -amountCents;
        return signed < 0 ? -signed : 0;
    }
}
