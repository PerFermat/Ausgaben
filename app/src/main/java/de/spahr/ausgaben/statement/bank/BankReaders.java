package de.spahr.ausgaben.statement.bank;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Alle von Hand geschriebenen Leser, in einer Liste.
 *
 * <p>Absichtlich eine schlichte Aufzählung: kein Registrieren zur Laufzeit, keine Konfiguration, keine
 * Reflexion. Eine Bank nachzurüsten soll eine Klasse, <b>eine Zeile hier</b> und ein Test sein — was
 * darüber hinaus an Mechanik entstünde, wäre für die Handvoll Leser, um die es geht, teurer als der
 * Nutzen.</p>
 */
public final class BankReaders {

    private static final List<BankReader> ALL = Collections.unmodifiableList(Arrays.asList(
            new IngReader()));

    private BankReaders() {
    }

    /** Der erste Leser, der diese Abrechnung als seine erkennt; {@code null}, wenn keiner zusagt. */
    public static BankReader find(PdfText text) {
        for (BankReader reader : ALL) {
            if (reader.matches(text)) {
                return reader;
            }
        }
        return null;
    }

    /** Alle Leser — für Tests und für die Frage, wie viele Banken fest bedient werden. */
    public static List<BankReader> all() {
        return ALL;
    }
}
