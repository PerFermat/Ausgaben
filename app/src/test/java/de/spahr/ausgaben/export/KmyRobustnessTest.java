package de.spahr.ausgaben.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.BookingSplit;
import de.spahr.ausgaben.db.KmyPendingDelete;

/**
 * Eigenschaften, die eine <b>fremde</b> KMyMoney-Datei mitbringen kann und die die App bisher nicht
 * vertragen hat: leere (selbstschließende) Blöcke, Splits mit Kindelementen, Konten in Fremdwährung
 * und doppelte Kontonamen. Die Dateien dazu liegen in {@code src/test/resources/kmy}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KmyRobustnessTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    static byte[] fixture(String name) throws IOException {
        try (java.io.InputStream in = KmyRobustnessTest.class.getResourceAsStream("/kmy/" + name)) {
            assertNotNull("Testdatei " + name + " fehlt", in);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private KmyDocument doc(String fixture) throws IOException {
        return new KmyDocument(fixture(fixture), ctx);
    }

    private static Booking expense(String account, String category, long cents, String payee) {
        Booking b = new Booking();
        b.id = 1;
        b.account = account;
        b.category = category;
        b.payee = payee;
        b.amountCents = cents;
        b.isIncome = false;
        b.createdAt = KmyDocument.parseKmyDate("2026-02-01");
        return b;
    }

    // ---- Leere Blöcke ----

    /**
     * Frische Datei: {@code <PAYEES/>} und {@code <TRANSACTIONS count="0"/>}. Vorher fiel das Einfügen
     * still aus, während das count-Attribut trotzdem hochgezählt wurde.
     */
    @Test
    public void emptyBlocksAreOpenedUp() throws Exception {
        KmyDocument d = doc("empty-blocks.xml");
        KmyExporter.Result r = new KmyExporter(d, ctx)
                .build(Collections.singletonList(expense("Bargeld", "Essen", 250, "Bäcker")));

        assertEquals(Collections.emptyList(), r.skipped);
        assertEquals(1, r.writtenIds.size());
        assertEquals(1, r.newPayees);
        assertTrue(r.xml.contains("<TRANSACTIONS count=\"1\">"));
        // <PAYEES/> hatte kein count-Attribut – dann wird auch keins erfunden, nur aufgeklappt.
        assertTrue(r.xml.contains("<PAYEES>"));
        assertEquals(1, countOf(r.xml, "<PAYEE "));
        assertTrue(r.xml.contains("account=\"A000001\""));
        assertEquals(1, countOf(r.xml, "<TRANSACTION "));

        // Die geschriebene Buchung muss auch wieder einlesbar sein.
        List<Booking> back = new KmyImporter(new KmyDocument(r.xml.getBytes(StandardCharsets.UTF_8), ctx),
                ctx).bookingsForAccount("Bargeld");
        assertEquals(1, back.size());
        assertEquals(250, back.get(0).amountCents);
        assertEquals("Essen", back.get(0).category);
        assertEquals("Bäcker", back.get(0).payee);
    }

    // ---- Splits mit Kindelementen ----

    /** Buchung mit Schlagwort löschen: der SPLIT ist nicht selbstschließend. */
    @Test
    public void taggedTransactionCanBeRemoved() throws Exception {
        KmyDocument d = doc("tagged-split.xml");
        KmyPendingDelete del = new KmyPendingDelete();
        del.id = 7;
        del.account = "Bargeld";
        del.createdAt = KmyDocument.parseKmyDate("2026-01-05");
        del.signedCents = -250;

        KmyExporter.DeleteResult r = new KmyExporter(d, ctx)
                .removeTransactions(d.xml(), Collections.singletonList(del));

        assertEquals(Collections.singletonList(7L), r.resolvedIds);
        assertFalse(r.xml.contains("T000000000000000001"));
        assertTrue(r.xml.contains("<TRANSACTIONS count=\"0\">"));
    }

    // ---- Fremdwährung ----

    /** Konto in USD, Transaktion in EUR: maßgeblich ist {@code shares} (USD), nicht {@code value}. */
    @Test
    public void foreignAccountUsesShares() throws Exception {
        KmyImporter imp = new KmyImporter(doc("foreign-currency.xml"), ctx);
        List<Booking> usd = imp.bookingsForAccount("Konto USD");
        assertEquals(1, usd.size());
        assertEquals(874708, usd.get(0).amountCents); // 218677/25 USD, nicht 10123 EUR
        assertFalse(usd.get(0).isIncome);
    }

    /** Export schreibt die Währung des Kontos – ein hartes „EUR" wäre in dieser Datei falsch. */
    @Test
    public void exportUsesAccountCurrency() throws Exception {
        KmyDocument d = doc("foreign-currency.xml");
        KmyExporter.Result r = new KmyExporter(d, ctx)
                .build(Collections.singletonList(expense("Konto USD", "Essen USD", 500, "")));
        assertEquals(Collections.emptyList(), r.skipped);
        assertTrue(r.xml.contains("commodity=\"USD\""));
    }

    /** Kategorie in anderer Währung als das Konto: ohne Kurs nicht schreibbar → übersprungen. */
    @Test
    public void currencyMismatchIsSkipped() throws Exception {
        KmyDocument d = doc("foreign-currency.xml");
        KmyExporter.Result r = new KmyExporter(d, ctx)
                .build(Collections.singletonList(expense("Konto USD", "Essen EUR", 500, "")));
        assertEquals(0, r.writtenIds.size());
        assertEquals(1, r.skipped.size());
        assertTrue(r.skipped.get(0).contains("USD"));
        assertTrue(r.skipped.get(0).contains("EUR"));
    }

    // ---- Doppelte Namen ----

    /** Zwei Konten „Girokonto" müssen einzeln ansprechbar bleiben (sonst bucht der Export daneben). */
    @Test
    public void duplicateAccountNamesStayDistinct() throws Exception {
        KmyDocument d = doc("duplicate-names.xml");
        List<String> names = d.accountNames();
        assertEquals(4, names.size()); // Bank A, Bank A:Girokonto, Bank B, Bank B:Girokonto
        assertTrue(names.contains("Bank A:Girokonto"));
        assertTrue(names.contains("Bank B:Girokonto"));
        assertFalse(d.accountId("Bank A:Girokonto").equals(d.accountId("Bank B:Girokonto")));

        // Kategorien: der Pfad trifft, der mehrdeutige Blattname nicht mehr.
        assertEquals("A000006", d.categoryId("Auto:Sonstiges"));
        assertEquals("A000008", d.categoryId("Haus:Sonstiges"));
        assertEquals(null, d.categoryId("Sonstiges"));
    }

    // ---- Splitbuchung über die ganze Kette ----

    /** Splitbuchung schreiben und wieder einlesen: Teilbeträge und Kopf-Kategorie bleiben erhalten. */
    @Test
    public void splitBookingRoundTrips() throws Exception {
        KmyDocument d = doc("empty-blocks.xml");
        Booking b = expense("Bargeld", "Essen", 1000, "Laden");
        Map<Long, List<BookingSplit>> parts = new HashMap<>();
        List<BookingSplit> list = new ArrayList<>();
        list.add(new BookingSplit(b.id, "Essen", 700, false));
        list.add(new BookingSplit(b.id, "Essen", 300, false));
        parts.put(b.id, list);

        KmyExporter.Result r = new KmyExporter(d, ctx).build(Collections.singletonList(b), parts);
        assertEquals(1, r.writtenIds.size());

        List<Booking> back = new KmyImporter(new KmyDocument(r.xml.getBytes(StandardCharsets.UTF_8), ctx),
                ctx).bookingsForAccount("Bargeld");
        assertEquals(1, back.size());
        assertEquals(1000, back.get(0).amountCents);
        assertNotNull(back.get(0).parts);
        assertEquals(2, back.get(0).parts.size());
    }

    /** Keine KMyMoney-Datei (z. B. GPG-verschlüsselt): klare Meldung statt Parserfehler. */
    @Test(expected = IOException.class)
    public void nonKmyFileIsRejected() throws Exception {
        new KmyDocument("-----BEGIN PGP MESSAGE-----\nabcdef\n".getBytes(StandardCharsets.UTF_8), ctx);
    }

    static int countOf(String haystack, String needle) {
        int n = 0;
        int i = haystack.indexOf(needle);
        while (i >= 0) {
            n++;
            i = haystack.indexOf(needle, i + needle.length());
        }
        return n;
    }
}
