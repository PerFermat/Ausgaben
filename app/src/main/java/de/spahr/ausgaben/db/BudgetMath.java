package de.spahr.ausgaben.db;

/**
 * Reine Vorzeichen- und Clamp-Logik der Budget-Einordnung – ohne DB/Android, damit JVM-testbar.
 *
 * <p>Grundregel: Der <b>Typ</b> (Einnahme/Ausgabe) einer Kategorie kommt aus der kmy-Datei; das
 * <b>Vorzeichen</b> einer Buchung bestimmt nur den Wert, nie den Typ. Eine Ausgabekategorie in einer
 * Einnahmebuchung (und umgekehrt) landet in ihrer eigenen Kategorie mit gedrehtem Vorzeichen.</p>
 */
public final class BudgetMath {

    private BudgetMath() {
    }

    /**
     * Geldzufluss einer Buchungszeile aus Kontosicht ({@code +} = rein, {@code −} = raus).
     * Für Einzelbuchungen ist {@code signedAmount} der (positive) Betrag, für Split-Teile der
     * vorzeichenbehaftete Teilbetrag. Spiegelt das {@code CASE WHEN is_income …}-Vorzeichen der
     * {@code getCategoryActuals}-Query wider.
     */
    public static long signedMoneyIn(boolean bookingIsIncome, long signedAmount) {
        return bookingIsIncome ? signedAmount : -signedAmount;
    }

    /**
     * Ist einer Kategorie in ihrer eigenen Typ-Richtung, nie negativ (Clamp auf 0).
     * {@code signedNet} ist die Summe der {@link #signedMoneyIn}-Werte aller Zeilen der Kategorie.
     * Einnahmekategorie: {@code +net}; Ausgabekategorie: {@code −net}; negativ → 0.
     */
    public static long ist(boolean categoryIsIncome, long signedNet) {
        long toward = categoryIsIncome ? signedNet : -signedNet;
        return Math.max(0, toward);
    }
}
