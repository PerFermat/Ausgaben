package de.spahr.ausgaben.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.BookingSplit;

/**
 * CSV-Import: was er annehmen und was er ablehnen muss.
 *
 * <p>Angenommen wird nur der <b>Ledger-Export</b> (KMyMoney in jeder Sprache, dazu der eigene Export der
 * App). Die CSV-Dateien im KMyMoney-Repo sind etwas anderes – Berichts-Exporte (querytable/pivottable) und
 * Bank-Kontoauszüge für den CSV-Importer-Assistenten. Die dürfen keinesfalls halb eingelesen werden,
 * sondern müssen eine verständliche Meldung ergeben.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CsvCorpusTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private static String fixture(String name) throws Exception {
        try (java.io.InputStream in = CsvCorpusTest.class.getResourceAsStream("/csv/" + name)) {
            assertNotNull("Testdatei " + name + " fehlt", in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ---- Was angenommen werden muss ----

    /** KMyMoney-Ledger-Export, englisch, Komma-getrennt, Punkt als Dezimalzeichen. */
    @Test
    public void englishLedgerImports() throws Exception {
        CsvImporter imp = new CsvImporter(ctx);
        List<Booking> b = imp.parse(fixture("ledger-en.csv"));
        assertEquals("My Visa", imp.getParsedAccount());
        assertEquals(3, b.size());
        assertEquals(4000, b.get(0).amountCents);
        assertFalse(b.get(0).isIncome);
        assertEquals("Entertainment", b.get(0).category);
        assertEquals("Cinema", b.get(0).note);
        assertEquals(125000, b.get(1).amountCents); // Tausenderpunkt/-komma korrekt gelesen
        assertTrue(b.get(1).isIncome);
    }

    /** Derselbe Export auf Deutsch: Semikolon, Komma als Dezimalzeichen, Punkt als Tausender. */
    @Test
    public void germanLedgerImports() throws Exception {
        CsvImporter imp = new CsvImporter(ctx);
        List<Booking> b = imp.parse(fixture("ledger-de.csv"));
        assertEquals("Sparkasse", imp.getParsedAccount());
        assertEquals(3, b.size());
        assertEquals(4000, b.get(0).amountCents);
        assertEquals("Unterhaltung", b.get(0).category);
        assertEquals(125000, b.get(1).amountCents);
        assertTrue(b.get(1).isIncome);
    }

    /** Der eigene Export muss sich wieder einlesen lassen (anderes Spalten-Layout als KMyMoney). */
    @Test
    public void ownExportRoundTrips() throws Exception {
        Booking b = new Booking();
        b.id = 1;
        b.account = "Bargeld";
        b.category = "Essen";
        b.payee = "Bäcker";
        b.note = "Brötchen";
        b.amountCents = 250;
        b.isIncome = false;
        b.createdAt = KmyDocument.parseKmyDate("2026-02-01");
        String csv = new CsvExporter().build("Bargeld", Collections.singletonList(b),
                new HashMap<>(), ctx);

        CsvImporter imp = new CsvImporter(ctx);
        List<Booking> back = imp.parse(csv);
        assertEquals("Bargeld", imp.getParsedAccount());
        assertEquals(1, back.size());
        assertEquals(250, back.get(0).amountCents);
        assertFalse(back.get(0).isIncome);
        assertEquals("Bäcker", back.get(0).payee);
        assertEquals("Essen", back.get(0).category);
        assertEquals("Brötchen", back.get(0).note);
    }

    /** Splitbuchung im eigenen Export: je Teil eine Zeile, die Summe muss wieder stimmen. */
    @Test
    public void ownExportWithSplitsRoundTrips() throws Exception {
        Booking b = new Booking();
        b.id = 1;
        b.account = "Bargeld";
        b.payee = "Laden";
        b.amountCents = 1000;
        b.createdAt = KmyDocument.parseKmyDate("2026-02-01");
        Map<Long, List<BookingSplit>> parts = new HashMap<>();
        List<BookingSplit> list = new ArrayList<>();
        list.add(new BookingSplit(1, "Essen", 700, false));
        list.add(new BookingSplit(1, "Haushalt", 300, false));
        parts.put(1L, list);

        List<Booking> back = new CsvImporter(ctx).parse(
                new CsvExporter().build("Bargeld", Collections.singletonList(b), parts, ctx));
        assertEquals(2, back.size());
        assertEquals(700, back.get(0).amountCents);
        assertEquals("Essen", back.get(0).category);
        assertEquals(300, back.get(1).amountCents);
        assertEquals("Haushalt", back.get(1).category);
    }

    // ---- Was abgelehnt werden muss ----

    /**
     * Alle CSV-Dateien des KMyMoney-Repos (Berichts-Exporte und Bank-Kontoauszüge) müssen mit einer
     * verständlichen Meldung abgelehnt werden – keine darf still „0 Buchungen" liefern oder Unsinn
     * einlesen. Ohne das Nachbar-Repo wird der Test übersprungen ({@code -Dcsv.corpus=…}).
     */
    @Test
    public void foreignCsvIsRejectedWithAMessage() throws Exception {
        File root = new File(System.getProperty("csv.corpus", "/home/michael/git/kmymoney"));
        Assume.assumeTrue("KMyMoney-Repo nicht vorhanden: " + root, root.isDirectory());
        List<File> files = new ArrayList<>();
        collect(root, files);
        Collections.sort(files);
        Assume.assumeFalse(files.isEmpty());

        for (File f : files) {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            try {
                List<Booking> b = new CsvImporter(ctx).parse(content);
                fail(f.getName() + ": wurde als Ledger-Export akzeptiert (" + b.size() + " Buchungen)");
            } catch (IllegalArgumentException e) {
                assertNotNull(f.getName() + ": Ablehnung ohne Meldung", e.getMessage());
                assertFalse(f.getName() + ": Ablehnung ohne Meldung", e.getMessage().isEmpty());
            }
        }
        assertTrue("nur " + files.size() + " CSV-Dateien geprüft", files.size() >= 100);
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        Arrays.sort(kids);
        for (File f : kids) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.getName().endsWith(".csv")) {
                out.add(f);
            }
        }
    }
}
