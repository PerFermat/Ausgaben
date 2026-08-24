package de.spahr.ausgaben.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Aus Wörtern mit Koordinaten werden Zeilen. Im PDF gibt es keine Zeilen, nur Textstücke an Positionen —
 * die Bündelung ist damit die erste Stelle, an der etwas schiefgehen kann, und die einzige Logik in
 * {@link PdfText}.
 */
public class PdfTextTest {

    private static PdfText.Builder builder() {
        return new PdfText.Builder();
    }

    @Test
    public void wörterGleicherHöheWerdenEineZeile() {
        PdfText t = builder()
                .add(0, "Kurswert", 60, 120, 300)
                .add(0, "EUR", 300, 330, 300)
                .add(0, "1.000,00", 400, 460, 300)
                .build();
        assertEquals(1, t.lines().size());
        assertEquals("Kurswert EUR 1.000,00", t.lines().get(0).text());
    }

    @Test
    public void wörterWerdenVonLinksNachRechtsGeordnet() {
        // Reihenfolge der Aufnahme ist beliebig – PdfBox liefert nicht immer sortiert.
        PdfText t = builder()
                .add(0, "1.000,00", 400, 460, 300)
                .add(0, "Kurswert", 60, 120, 300)
                .add(0, "EUR", 300, 330, 300)
                .build();
        assertEquals("Kurswert EUR 1.000,00", t.lines().get(0).text());
    }

    @Test
    public void leichterVersatzGiltNochAlsDieselbeZeile() {
        PdfText t = builder()
                .add(0, "Nominale", 60, 120, 300)
                .add(0, "6,09607", 400, 450, 302)
                .build();
        assertEquals(1, t.lines().size());
    }

    @Test
    public void deutlicherVersatzTrenntDieZeilen() {
        PdfText t = builder()
                .add(0, "Kurswert", 60, 120, 300)
                .add(0, "Zwischensumme", 60, 140, 320)
                .build();
        assertEquals(2, t.lines().size());
        assertEquals("Kurswert", t.lines().get(0).text());
        assertEquals("Zwischensumme", t.lines().get(1).text());
    }

    @Test
    public void zeilenStehenVonObenNachUntenUndSeiteFürSeite() {
        PdfText t = builder()
                .add(1, "zweite Seite", 60, 140, 100)
                .add(0, "unten", 60, 100, 700)
                .add(0, "oben", 60, 100, 100)
                .build();
        assertEquals(3, t.lines().size());
        assertEquals("oben", t.lines().get(0).text());
        assertEquals("unten", t.lines().get(1).text());
        assertEquals("zweite Seite", t.lines().get(2).text());
        assertEquals(2, t.pageCount());
    }

    /** Gleiche Höhe, aber verschiedene Seiten – das ist nie dieselbe Zeile. */
    @Test
    public void gleicheHöheAufZweiSeitenBleibtGetrennt() {
        PdfText t = builder()
                .add(0, "Seite eins", 60, 120, 300)
                .add(1, "Seite zwei", 60, 120, 300)
                .build();
        assertEquals(2, t.lines().size());
    }

    @Test
    public void leereWörterFallenWeg() {
        PdfText t = builder()
                .add(0, "  ", 60, 70, 300)
                .add(0, "Kurs", 80, 120, 300)
                .add(0, null, 130, 140, 300)
                .build();
        assertEquals("Kurs", t.lines().get(0).text());
    }

    @Test
    public void einGescanntesDokumentMeldetKeinenText() {
        PdfText leer = builder().build();
        assertFalse(leer.hasText());
        // Ein einzelnes Wort ist auch noch kein Text – solche Reste liefert ein Scan durchaus.
        assertFalse(builder().add(0, "x", 0, 5, 0).build().hasText());
    }

    @Test
    public void einEchtesDokumentMeldetText() {
        PdfText.Builder b = builder();
        for (int i = 0; i < 20; i++) {
            b.add(0, "Wort" + i, 60, 100, 100 + i * 20);
        }
        assertTrue(b.build().hasText());
    }

    @Test
    public void wortpositionenBleibenErhalten() {
        PdfText t = builder()
                .add(0, "Endbetrag", 60, 130, 300)
                .add(0, "1.000,00", 400, 460, 300)
                .build();
        List<PdfText.Word> words = t.lines().get(0).words;
        assertEquals(60f, words.get(0).x, 0.001);
        assertEquals(130f, words.get(0).endX, 0.001);
        assertEquals(400f, words.get(1).x, 0.001);
    }
}
