package de.spahr.ausgaben.pdf;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Woher der Text kommt, steht ihm an: {@link PdfText#hasWordPositions()}.
 *
 * <p>Das Merkmal ist keine Buchhaltung um ihrer selbst willen. Es entscheidet, ob sich aus dem Text die
 * <b>Spaltenlage</b> ablesen lässt — und damit, ob der Lerner eine Tabelle überhaupt als solche erkennen
 * kann. Ohne diese Unterscheidung würde an einer Lage gelernt, die es im Dokument gar nicht gibt.</p>
 */
public class PdfTextPositionsTest {

    /** Was aus einem PDF kommt, trägt Positionen — der Builder ist der Weg des Auslesers. */
    @Test
    public void ausDemBuilderMitPositionen() {
        PdfText text = new PdfText.Builder()
                .add(0, "Gesamt", 100f, 140f, 10f)
                .add(0, "648,36", 100f, 145f, 24f)
                .build();
        assertTrue(text.hasWordPositions());
    }

    /** Was aus fertigen Zeilen zurückgebaut wird, trägt keine. */
    @Test
    public void ausFertigenZeilenOhne() {
        assertFalse(PdfText.fromLines("Gesamt 648,36\nValuta 01.07.2026").hasWordPositions());
    }

    /**
     * Der Weg, um den es geht: der Zwischenspeicher der Maske. Aus dem PDF gelesen, als Text abgelegt,
     * wieder eingelesen — dabei geht die Lage verloren, und der Text sagt das jetzt auch.
     */
    @Test
    public void derUmwegUeberDenTextVerliertSie() {
        PdfText ausPdf = new PdfText.Builder()
                .add(0, "Buchung", 50f, 90f, 10f)
                .add(0, "Wertstellung", 150f, 210f, 10f)
                .add(0, "30.06.2026", 50f, 100f, 24f)
                .add(0, "01.07.2026", 150f, 200f, 24f)
                .add(0, "Dividende", 250f, 300f, 24f)
                .build();
        assertTrue(ausPdf.hasWordPositions());
        assertFalse(PdfText.fromLines(ausPdf.text()).hasWordPositions());
    }
}
