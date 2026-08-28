package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.pdf.PdfTextExtractor;

/**
 * Die Spaltenlogik an einem <b>echten PDF</b> — mit den Koordinaten, die im Dokument stehen.
 *
 * <p>Der Grund für diesen Test: alle anderen Belege der Testreihe sind Text. Dort setzt
 * {@link PdfText#fromLines} die x-Position auf die <b>Zeichenspalte</b>, und damit misst man am Ende
 * bloss, wie viele Leerzeichen jemand getippt hat. Ob „steht in derselben Spalte" trägt, entscheidet
 * sich erst an einer Seite mit Proportionalschrift, auf der die Überschriften linksbündig und die Zahlen
 * rechtsbündig gesetzt sind — dort liegt keine Zahl genau unter ihrer Überschrift.</p>
 *
 * <p>Die PDFs unter {@code src/test/resources/pdf} sind selbst erzeugt: ein Kauf mit sechs Spalten,
 * derselbe Kauf ohne die Provisionsspalte, und ein Dividendenbeleg mit Kontobewegungs-Tabelle. Der
 * letzte ist Wort für Wort <b>an den Koordinaten eines echten Belegs</b> nachgebaut — Name, Anschrift
 * und Kontonummer sind ersetzt, die Spaltenlage ist die des Originals. Gegengeprüft: am Original liest
 * die App dieselben vier Regeln und dieselben Werte.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PdfColumnTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private PdfText lies(String name) throws IOException {
        File ziel = new File(ctx.getCacheDir(), name);
        try (InputStream in = getClass().getResourceAsStream("/pdf/" + name)) {
            assertNotNull("Testdatei fehlt: " + name, in);
            Files.copy(in, ziel.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return PdfTextExtractor.read(ctx, Uri.fromFile(ziel));
    }

    private static AnchorRule spalte(String anchor, int distance) {
        return new AnchorRule(Collections.singletonList(anchor), AnchorRule.Direction.LINE_BELOW,
                false, "", AnchorRule.Position.COLUMN, 1, distance);
    }

    private static AnchorRule gezaehlt(String anchor, int nth) {
        return new AnchorRule(Collections.singletonList(anchor), AnchorRule.Direction.LINE_BELOW,
                false, "", AnchorRule.Position.FIRST, nth, 1);
    }

    /** Erst die Voraussetzung: das PDF liefert überhaupt Text, und die Zahlen stehen in einer Zeile. */
    @Test
    public void dasPdfLiefertDieTabellenzeile() throws IOException {
        PdfText text = lies("tabelle.pdf");
        assertTrue(text.hasText());
        boolean gefunden = false;
        for (PdfText.Line line : text.lines()) {
            if (line.text().contains("999,00") && line.text().contains("100,00")) {
                gefunden = true;
            }
        }
        assertTrue("die Datenzeile steht als eine Zeile da", gefunden);
    }

    /**
     * Die Zahlen stehen rechtsbündig, die Überschriften linksbündig — <b>keine</b> Zahl beginnt an der
     * Position ihrer Überschrift. Genau dafür gibt es den Rückfall auf den geringsten Abstand.
     */
    @Test
    public void keineZahlBeginntGenauUnterIhrerUeberschrift() throws IOException {
        PdfText text = lies("tabelle.pdf");
        PdfText.Line kopf = zeileMit(text, "Provision");
        PdfText.Line daten = zeileMit(text, "999,00");
        float[] gesamt = AnchorRule.anchorSpan(kopf, "Gesamt");
        assertNotNull(gesamt);
        for (PdfText.Word word : daten.words) {
            assertNotEquals("kein Wert beginnt exakt an der Spalte der Überschrift",
                    gesamt[0], word.x, 0.01f);
        }
    }

    /** Und trotzdem trifft die Spaltenregel den richtigen Wert. */
    @Test
    public void dieSpaltenregelTrifftImEchtenPdf() throws IOException {
        assertEquals(999.0, spalte("Gesamt", 1).read(lies("tabelle.pdf")), 0.0001);
        assertEquals(100.0, spalte("Kurs", 1).read(lies("tabelle.pdf")), 0.0001);
        assertEquals(2.20, spalte("Steuer", 1).read(lies("tabelle.pdf")), 0.0001);
    }

    /**
     * Der Unterschied, um den es geht: fehlt eine Spalte <b>vor</b> dem gesuchten Wert, zählt jede
     * gezählte Stelle daneben — die Spaltenregel nicht.
     *
     * <p>Zwei Belege derselben Bank: einmal mit Provision, einmal ohne (bei einem Sparplan fällt sie
     * weg). Die am ersten gelernte Stelle „die vierte von links" liest im zweiten den Steuerbetrag als
     * Gesamtbetrag — 2,20 statt 999,00, still und ohne Anlass zur Nachfrage.</p>
     */
    @Test
    public void ohneEineSpalteVerzaehltSichNurDasAbzaehlen() throws IOException {
        PdfText voll = lies("tabelle.pdf");
        PdfText ohne = lies("tabelle_ohne_provision.pdf");

        AnchorRule gezaehlt = gezaehlt("Stueck", 4);
        assertEquals("am vollen Beleg: die vierte Zahl von links ist der Gesamtbetrag",
                999.0, gezaehlt.read(voll), 0.0001);
        assertEquals("fehlt die Provision, ist es die Steuer – falsch und unauffällig",
                2.20, gezaehlt.read(ohne), 0.0001);

        assertEquals(999.0, spalte("Gesamt", 1).read(voll), 0.0001);
        assertEquals("über die Position stimmt es in beiden",
                999.0, spalte("Gesamt", 1).read(ohne), 0.0001);
    }

    // ---- Ein nachgebauter Dividendenbeleg mit Kontobewegungs-Tabelle ----

    /**
     * Der Lerner findet die Stückzahl an einem echten Tabellenlayout <b>von selbst über die Spalte</b>.
     *
     * <p>Die Zeile mit den Daten trägt keine eigene Beschriftung, zwischen ihr und der Kopfzeile steht
     * noch „Wechselkurs", und die Zahlen sind rechtsbündig unter linksbündigen Überschriften gesetzt.
     * Die gelernte Regel hängt deshalb an der Position von „Anzahl", nicht an einer gezählten Stelle.</p>
     */
    @Test
    public void derLernerFindetDieStueckzahlUeberDieSpalte() throws IOException {
        PdfText beleg = lies("dividende_tabelle.pdf");

        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.DIVIDEND;
        bekannt.shares = 816.652;
        bekannt.netCents = 52210L;
        bekannt.feeCents = 12626L;
        bekannt.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("01.07.2026");

        AnchorRule stueck = TemplateLearner.learn(beleg, bekannt)
                .rule(StatementTemplate.Field.SHARES);
        assertNotNull(stueck);
        assertEquals(AnchorRule.Position.COLUMN, stueck.position);
        assertEquals("Anzahl", stueck.anchors.get(0));
        assertEquals(2, stueck.lineDistance);
        assertEquals(816.652, stueck.read(beleg), 1e-9);
    }

    /** Und die ganze Abrechnung kommt aus der einmal gelernten Vorlage wieder heraus. */
    @Test
    public void dieGanzeDividendeWirdWiederGelesen() throws IOException {
        PdfText beleg = lies("dividende_tabelle.pdf");

        TemplateLearner.Known bekannt = new TemplateLearner.Known();
        bekannt.action = StatementScan.DIVIDEND;
        bekannt.shares = 816.652;
        bekannt.netCents = 52210L;
        bekannt.feeCents = 12626L;
        bekannt.dateMillis = de.spahr.ausgaben.util.TextValues.toDateMillis("01.07.2026");

        StatementTemplate.Extraction e = TemplateLearner.learn(beleg, bekannt).apply(beleg);
        assertEquals(Long.valueOf(52210L), e.netCents);
        assertEquals(Long.valueOf(12626L), e.feeCents);
        assertEquals(816.652, e.shares, 1e-9);
        assertEquals(de.spahr.ausgaben.util.TextValues.toDateMillis("01.07.2026"), e.dateMillis);
    }

    /** Von Hand: die Spalten „Gesamt" und „Wertstellung" der Kontobewegung. */
    @Test
    public void gesamtUndWertstellungStehenInIhrenSpalten() throws IOException {
        PdfText beleg = lies("dividende_tabelle.pdf");
        assertEquals(648.36, spalte("Gesamt", 2).read(beleg), 0.0001);
        assertEquals(de.spahr.ausgaben.util.TextValues.toDateMillis("01.07.2026"),
                spalte("Wertstellung", 2).readDate(beleg));
    }

    private static PdfText.Line zeileMit(PdfText text, String teil) {
        for (PdfText.Line line : text.lines()) {
            if (line.text().contains(teil)) {
                return line;
            }
        }
        throw new AssertionError("Zeile mit „" + teil + "\" nicht gefunden");
    }
}
