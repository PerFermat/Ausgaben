package de.spahr.ausgaben.export;

import static org.junit.Assert.assertEquals;
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
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.spahr.ausgaben.db.SecurityTx;

/**
 * Der Rückweg: in der App erfasste Depot-Bewegungen werden als vollständige Wertpapier-Transaktion in die
 * KMyMoney-Datei geschrieben. Geprüft wird beides – dass die Datei in sich stimmt (jede Transaktion
 * summiert sich auf 0) und dass der eigene Import genau die Zahlen zurückliefert, die hineingegeben wurden.
 *
 * <p>Grundlage ist {@code security-tx.xml} mit dem Depot „Depot", dem Wertpapier {@code E000001}
 * („Musterfonds"), dem Geldkonto „Verrechnungskonto", der Ausgabekategorie „Bankgebühren" und der
 * Ertragskategorie „Dividenden".</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class KmySecurityExportTest {

    private static final Pattern VALUE = Pattern.compile("<SPLIT\\b[^>]*\\bvalue=\"([^\"]*)\"");
    private static final Pattern TX_BLOCK =
            Pattern.compile("<TRANSACTION\\b.*?</TRANSACTION>", Pattern.DOTALL);

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private KmyDocument doc() throws IOException {
        return new KmyDocument(KmyRobustnessTest.fixture("security-tx.xml"), ctx);
    }

    private static SecurityTx pending(String action, double shares, long gross, long net, long fee) {
        SecurityTx tx = new SecurityTx();
        tx.id = 42;
        tx.depot = "Depot";
        tx.securityKmyId = "E000001";
        tx.securityName = "Musterfonds";
        tx.date = 1772409600000L;
        tx.action = action;
        tx.shares = shares;
        tx.amountCents = gross;
        tx.netCents = net;
        tx.feeCents = fee;
        tx.pending = true;
        tx.moneyAccount = "Verrechnungskonto";
        tx.feeCategory = "Bankgebühren";
        return tx;
    }

    /** Schreibt die Bewegung und liest die Datei mit dem eigenen Importer wieder ein. */
    private List<SecurityTx> roundtrip(SecurityTx tx) throws IOException {
        KmyDocument original = doc();
        KmyExporter exporter = new KmyExporter(original, ctx);
        KmyExporter.SecurityResult res = exporter.buildSecurityTransactions(
                original.xml(), Collections.singletonList(tx));
        assertEquals("Bewegung wurde übersprungen: " + res.skipped, 1, res.writtenIds.size());
        assertBalanced(res.xml);

        KmyDocument written = new KmyDocument(res.xml.getBytes(StandardCharsets.UTF_8), ctx);
        return new KmyImporter(written, ctx).importDepot("Depot").transactions;
    }

    /** Jede Transaktion der Datei muss sich auf 0 summieren – sonst lehnt KMyMoney sie als unausgeglichen ab. */
    private static void assertBalanced(String xml) {
        Matcher tx = TX_BLOCK.matcher(xml);
        int seen = 0;
        while (tx.find()) {
            seen++;
            long sum = 0;
            Matcher v = VALUE.matcher(tx.group());
            while (v.find()) {
                sum += valueToCents(v.group(1));
            }
            assertEquals("Transaktion nicht ausgeglichen: " + tx.group(), 0, sum);
        }
        assertTrue("keine Transaktion gefunden", seen >= 2);
    }

    private static long valueToCents(String fraction) {
        int slash = fraction.indexOf('/');
        if (slash < 0) {
            return Math.round(Double.parseDouble(fraction) * 100);
        }
        double num = Double.parseDouble(fraction.substring(0, slash));
        double den = Double.parseDouble(fraction.substring(slash + 1));
        return den == 0 ? 0 : Math.round(num / den * 100);
    }

    /**
     * Die selbst geschriebene Bewegung – also die, die nicht schon in der Testdatei stand. Dort steht ein
     * Kauf über 20 Stück zu 1000,00 €; alles andere stammt aus diesem Test.
     */
    private static SecurityTx findWritten(List<SecurityTx> all) {
        for (SecurityTx t : all) {
            boolean fromFixture = "buy".equals(t.action) && Math.abs(t.shares - 20.0) < 1e-9
                    && t.amountCents == 100000L;
            if (!fromFixture) {
                return t;
            }
        }
        return null;
    }

    @Test
    public void kaufKommtMitStückzahlBetragUndGebührZurück() throws IOException {
        // 10 Stück zu 25,00 € plus 5,00 € Gebühr → 255,00 € verlassen das Konto.
        List<SecurityTx> back = roundtrip(pending("buy", 10.0, 25000L, 25000L, 500L));
        assertEquals(2, back.size());
        SecurityTx tx = findWritten(back);
        assertNotNull(tx);
        assertEquals("buy", tx.action);
        assertEquals(10.0, tx.shares, 1e-9);
        assertEquals(25000L, tx.amountCents);
        assertEquals(500L, tx.feeCents);
    }

    @Test
    public void verkaufKommtMitNegativerStückzahlZurück() throws IOException {
        // 4 Stück zu 30,00 € abzüglich 3,00 € Gebühr → 117,00 € kommen an.
        List<SecurityTx> back = roundtrip(pending("sell", -4.0, 12000L, 12000L, 300L));
        SecurityTx tx = findWritten(back);
        assertNotNull(tx);
        assertEquals("sell", tx.action);
        assertEquals(-4.0, tx.shares, 1e-9);
        assertEquals(12000L, tx.amountCents);
        assertEquals(300L, tx.feeCents);
    }

    @Test
    public void dividendeKommtMitBruttoUndNettoZurück() throws IOException {
        // 100,00 € brutto, 26,38 € Steuer → 73,62 € werden gutgeschrieben.
        SecurityTx div = pending("dividend", 0, 10000L, 7362L, 0L);
        div.incomeCategory = "Dividenden";
        List<SecurityTx> back = roundtrip(div);
        SecurityTx tx = findWritten(back);
        assertNotNull(tx);
        assertEquals("dividend", tx.action);
        assertEquals(10000L, tx.amountCents);
        assertEquals(7362L, tx.netCents);
        // Eine Dividende bewegt keine Stücke – sonst verfälschte sie den Bestand.
        assertEquals(0.0, tx.shares, 1e-9);
    }

    @Test
    public void dividendeOhneSteuerBleibtAusgeglichen() throws IOException {
        SecurityTx div = pending("dividend", 0, 5000L, 5000L, 0L);
        div.incomeCategory = "Dividenden";
        SecurityTx tx = findWritten(roundtrip(div));
        assertNotNull(tx);
        assertEquals(5000L, tx.amountCents);
        assertEquals(5000L, tx.netCents);
    }

    @Test
    public void krummeStückzahlÜberstehtDenBruch() throws IOException {
        // 3,4567 Stück – die Stückzahl wird als Bruch geschrieben, nicht als Dezimalzahl.
        SecurityTx tx = findWritten(roundtrip(pending("buy", 3.4567, 12345L, 12345L, 0L)));
        assertNotNull(tx);
        assertEquals(3.4567, tx.shares, 1e-9);
        assertEquals(12345L, tx.amountCents);
    }

    /**
     * Sparplan-Ausführung mit den Zahlen einer echten ING-Abrechnung: 6,09607 Stück für 1.000,00 €.
     * Fünf Nachkommastellen – mit einem gröberen Bruch verschwände die letzte still, und der Bestand
     * liefe über die Jahre auseinander.
     */
    @Test
    public void sparplanAnteileBehaltenFuenfNachkommastellen() throws IOException {
        SecurityTx tx = findWritten(roundtrip(pending("buy", 6.09607, 100000L, 100000L, 0L)));
        assertNotNull(tx);
        assertEquals(6.09607, tx.shares, 1e-9);
        assertEquals(100000L, tx.amountCents);
    }

    /** Im Wertpapier-Split muss {@code shares × price} genau den {@code value} ergeben. */
    @Test
    public void stueckzahlMalKursErgibtDenSplitbetrag() throws IOException {
        KmyDocument original = doc();
        KmyExporter.SecurityResult res = new KmyExporter(original, ctx).buildSecurityTransactions(
                original.xml(), Collections.singletonList(pending("buy", 6.09607, 100000L, 100000L, 0L)));
        assertEquals(1, res.writtenIds.size());

        // Die Testdatei bringt selbst einen Kauf mit; der neue wird hinten angehängt, also den letzten nehmen.
        Matcher m = Pattern.compile("<SPLIT\\b[^>]*action=\"Buy\"[^>]*/>").matcher(res.xml);
        String split = null;
        while (m.find()) {
            split = m.group();
        }
        assertNotNull("kein Buy-Split gefunden", split);
        double shares = fractionOf(split, "shares");
        double price = fractionOf(split, "price");
        double value = fractionOf(split, "value");
        assertEquals(6.09607, shares, 1e-9);
        assertEquals(value, shares * price, 1e-6);
    }

    private static double fractionOf(String splitXml, String attribute) {
        Matcher m = Pattern.compile("\\b" + attribute + "=\"([^\"]*)\"").matcher(splitXml);
        assertTrue(attribute + " fehlt", m.find());
        String f = m.group(1);
        int slash = f.indexOf('/');
        if (slash < 0) {
            return Double.parseDouble(f);
        }
        return Double.parseDouble(f.substring(0, slash)) / Double.parseDouble(f.substring(slash + 1));
    }

    @Test
    public void unbekanntesGeldkontoWirdÜbersprungenStattHalbGeschrieben() throws IOException {
        SecurityTx tx = pending("buy", 1.0, 1000L, 1000L, 0L);
        tx.moneyAccount = "Gibt es nicht";
        KmyDocument original = doc();
        KmyExporter.SecurityResult res = new KmyExporter(original, ctx)
                .buildSecurityTransactions(original.xml(), Collections.singletonList(tx));
        assertTrue(res.writtenIds.isEmpty());
        assertEquals(1, res.skipped.size());
        assertEquals("die Datei darf unverändert bleiben", original.xml(), res.xml);
    }

    @Test
    public void neueTransaktionBekommtEineFreieNummer() throws IOException {
        KmyDocument original = doc();
        KmyExporter.SecurityResult res = new KmyExporter(original, ctx).buildSecurityTransactions(
                original.xml(), Collections.singletonList(pending("buy", 1.0, 1000L, 1000L, 0L)));
        // Die Datei bringt T…001 mit; die neue muss darüber liegen und darf sie nicht überschreiben.
        assertTrue(res.xml.contains("T000000000000000001"));
        assertTrue(res.xml.contains("T000000000000000002"));
        assertEquals(2, KmyExporter.maxTxNumberIn(res.xml));
    }
}
