package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertEquals;
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
import java.nio.file.StandardCopyOption;
import java.util.Calendar;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.pdf.PdfTextExtractor;

/**
 * Das Datum einer Dividendenabrechnung, die es in eine <b>Tabelle</b> setzt — am echten PDF.
 *
 * <p>Der Beleg (anonymisierter Nachbau, Wort für Wort an den Koordinaten des Originals) stellt die
 * Kontobewegung so dar:</p>
 *
 * <pre>
 * Buchung       Wertstellung   Typ              Betrag / Stk.   Berechtigte Anzahl    Gesamt
 *                                                Wechselkurs
 * 30.06.2026 01.07.2026        Gutschrift        0,905474 USD              816,652   648,36 EUR
 * </pre>
 *
 * <p>Die Zeile mit den Daten trägt keine eigene Beschriftung; die steht als Spaltenüberschrift zwei
 * Zeilen darüber. Dazwischen liegt die einzelne Zelle „Wechselkurs" — und genau die hat die App früher
 * als Überschrift genommen, weil sie die <b>nächste</b> Zeile darüber war. Gelernt wurde
 * {@code [Wechselkurs] (LINE_BELOW 1, in der Spalte)}: liest zufällig richtig, benennt aber etwas
 * anderes, und beim nächsten Beleg dieser Bank ist es Glückssache.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LearnerDateColumnTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private PdfText lies(String name) throws IOException {
        File ziel = new File(ctx.getCacheDir(), name);
        try (InputStream in = getClass().getResourceAsStream("/pdf/" + name)) {
            assertNotNull("Testdatei fehlt: " + name, in);
            Files.copy(in, ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return PdfTextExtractor.read(ctx, Uri.fromFile(ziel));
    }

    private static long tag(int jahr, int monat, int tag) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag);
        return c.getTimeInMillis();
    }

    private static TemplateLearner.Known dividende(long dateMillis) {
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = StatementScan.DIVIDEND;
        k.netCents = 52210L;
        k.feeCents = 12626L;
        k.dateMillis = dateMillis;
        return k;
    }

    /** Gelernt wird die wirkliche Spaltenüberschrift, nicht die nächste Zelle darüber. */
    @Test
    public void dieUeberschriftGewinntGegenDieNaechsteZelle() throws IOException {
        PdfText text = lies("dividende_tabelle.pdf");
        AnchorRule regel = TemplateLearner.learn(text, dividende(tag(2026, 7, 1)))
                .rule(StatementTemplate.Field.DATE);

        assertNotNull("ohne Regel bliebe das Datum bei jedem Beleg leer", regel);
        assertEquals("Wertstellung", regel.anchors.get(0));
        assertEquals(AnchorRule.Position.COLUMN, regel.position);
        assertEquals(tag(2026, 7, 1), regel.readDate(text));
    }

    /** Die Auswahl legt beide Datumsspalten vor — und die anderen Datumsangaben des Belegs auch. */
    @Test
    public void beideSpaltenStehenZurWahl() throws IOException {
        PdfText text = lies("dividende_tabelle.pdf");
        assertTrue("die Wertstellung fehlte in der Auswahl",
                hat(text, "Wertstellung", tag(2026, 7, 1)));
        assertTrue("und der Buchungstag daneben ebenso",
                hat(text, "Buchung", tag(2026, 6, 30)));
        assertTrue("die Angaben mit eigener Beschriftung bleiben, wie sie waren",
                hat(text, "Ex Tag", tag(2026, 6, 18)));
    }

    /**
     * Die Wahl des Nutzers gewinnt: wer den Buchungstag antippt, bekommt dessen Spalte gelernt — obwohl
     * die Suche von sich aus die Wertstellung nähme.
     */
    @Test
    public void dieAngetippteRegelGiltVorDerSuche() throws IOException {
        PdfText text = lies("dividende_tabelle.pdf");
        StatementScan.DateCandidate buchung = kandidat(text, "Buchung", tag(2026, 6, 30));
        assertNotNull(buchung);

        TemplateLearner.Known k = dividende(tag(2026, 6, 30));
        k.dateAnchor = buchung.label;
        k.dateRule = buchung.rule;

        AnchorRule regel = TemplateLearner.learn(text, k).rule(StatementTemplate.Field.DATE);
        assertNotNull(regel);
        assertEquals("Buchung", regel.anchors.get(0));
        assertEquals(tag(2026, 6, 30), regel.readDate(text));
    }

    /** Und die Zahlen: „Gesamt" wird als eigene Spalte angeboten, nicht die ganze Kopfzeile. */
    @Test
    public void auchDieZahlenspaltenStehenZurWahl() throws IOException {
        PdfText text = lies("dividende_tabelle.pdf");
        boolean gefunden = false;
        for (StatementScan.ValueCandidate c : StatementScan.values(text,
                AnchorRule.Direction.SAME_LINE, 0, AnchorRule.Position.LAST, 1, "")) {
            if ("Gesamt".equals(c.label) && Math.abs(c.value - 648.36) < 0.001) {
                gefunden = true;
                assertEquals(AnchorRule.Position.COLUMN, c.rule.position);
            }
        }
        assertTrue("die Spalte „Gesamt“ fehlte in der Auswahl", gefunden);
    }

    private boolean hat(PdfText text, String label, long millis) {
        return kandidat(text, label, millis) != null;
    }

    private StatementScan.DateCandidate kandidat(PdfText text, String label, long millis) {
        for (StatementScan.DateCandidate c : StatementScan.dates(text)) {
            if (label.equals(c.label) && c.millis == millis) {
                return c;
            }
        }
        return null;
    }
}
