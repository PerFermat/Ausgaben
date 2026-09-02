package de.spahr.ausgaben.receipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Die Reihenfolge beim Anhängen einer Abrechnung: erst buchen, dann ablegen.
 *
 * <p>Bis 1.12 schob die Erkennungsliste die Abrechnung in den Jahresordner, meldete sie zum Hochladen
 * an und vergaß dabei ihren vorläufigen Pfad — alles <b>bevor</b> die Bewegungen gespeichert waren.
 * Scheiterte die Transaktion, lag die Datei in der Ablage, der Upload war angelaufen, und im Depot
 * stand nichts; aufräumen konnte danach niemand mehr, denn woher die Datei kam, wusste keiner.</p>
 *
 * <p>Geprüft wird deshalb {@link SingleReceipt} selbst und nicht die Maske: die Zusicherung ist, dass
 * {@link SingleReceipt#plan} den Namen vergibt, ohne etwas anzufassen, und dass erst
 * {@link SingleReceipt#attach} bewegt.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SingleReceiptOrderTest {

    private static final long AM_1_MAERZ_2026 = 1772323200000L;

    private final Context ctx = ApplicationProvider.getApplicationContext();

    @Before
    public void leereAblage() {
        Receipts.reset(ctx);
    }

    /** Eine vorläufig abgelegte Abrechnung, wie {@code SingleReceipt.stage} sie hinterlässt. */
    private File vorlaeufigeDatei() throws Exception {
        File f = new File(Receipts.dir(ctx), "pend_test" + NoteReceipt.PDF);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    @Test
    public void planenBewegtNochNichts() throws Exception {
        File vorlaeufig = vorlaeufigeDatei();

        SingleReceipt.Planned geplant = SingleReceipt.plan(vorlaeufig, "Kauf", AM_1_MAERZ_2026);

        assertTrue("es gibt etwas anzuhängen", geplant.hatBeleg());
        assertNotNull("die Notiz trägt den Beleg-Tag", NoteReceipt.pdfName(geplant.note));
        assertTrue("die Notiz behält ihren Text", geplant.note.startsWith("Kauf"));
        assertTrue("die Datei liegt noch an ihrem vorläufigen Platz", vorlaeufig.exists());
        assertTrue("und ist zu nichts angemeldet", Receipts.pending(ctx).isEmpty());
    }

    @Test
    public void erstDasAblegenVerschiebtUndMeldetAn() throws Exception {
        File vorlaeufig = vorlaeufigeDatei();
        SingleReceipt.Planned geplant = SingleReceipt.plan(vorlaeufig, "Kauf", AM_1_MAERZ_2026);

        assertTrue(SingleReceipt.attach(ctx, geplant));

        assertFalse("die vorläufige Kopie ist weg", vorlaeufig.exists());
        Set<String> offen = Receipts.pending(ctx);
        assertEquals("genau ein Beleg wartet auf den Upload", 1, offen.size());
        String eintrag = offen.iterator().next();
        assertEquals("im Jahresordner der Buchung", 2026, Receipts.entryYear(eintrag));
        assertTrue("die Datei liegt unter ihrem endgültigen Namen",
                Receipts.localFile(ctx, Receipts.entryFile(eintrag)).exists());
    }

    /**
     * Und der Name in der Notiz meint dieselbe Datei, die abgelegt wurde — sonst zeigte die Buchung
     * hinterher auf einen Beleg, den es nicht gibt.
     */
    @Test
    public void notizUndAbgelegteDateiGehoerenZusammen() throws Exception {
        SingleReceipt.Planned geplant =
                SingleReceipt.plan(vorlaeufigeDatei(), "Kauf", AM_1_MAERZ_2026);
        String ausDerNotiz = NoteReceipt.pdfName(geplant.note);

        SingleReceipt.attach(ctx, geplant);

        String abgelegt = Receipts.entryFile(Receipts.pending(ctx).iterator().next());
        assertEquals(NoteReceipt.tagOf(abgelegt), ausDerNotiz);
    }

    /** Ohne Abrechnung bleibt die Notiz, wie sie war, und es passiert nichts. */
    @Test
    public void ohneAbrechnungPassiertNichts() {
        SingleReceipt.Planned geplant = SingleReceipt.plan(null, "Kauf", AM_1_MAERZ_2026);

        assertFalse(geplant.hatBeleg());
        assertEquals("Kauf", geplant.note);
        assertTrue(SingleReceipt.attach(ctx, geplant));
        assertTrue(Receipts.pending(ctx).isEmpty());
    }
}
