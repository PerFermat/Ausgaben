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
        if (fee != 0) {
            tx.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                    0, false, "Bankgebühren", fee, "", 0));
        }
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

    /**
     * KMyMoney führt die ISIN am Wertpapier als {@code kmm-security-id}. Sie wird mitimportiert, damit
     * eine eingelesene Bankabrechnung von Anfang an dem richtigen Wertpapier zugeordnet werden kann.
     */
    @Test
    public void dieIsinKommtAusDerKmyDateiMit() throws IOException {
        KmyDocument d = doc();
        assertEquals("IE00B3RBWM25", d.securityIsin("E000001"));
        de.spahr.ausgaben.db.Security s =
                new KmyImporter(d, ctx).importDepot("Depot").securities.get(0);
        assertEquals("IE00B3RBWM25", s.isin);
        assertEquals("Musterfonds", s.name);
    }

    /** Ein Wertpapier ohne gepflegte Identifikation bekommt eine leere ISIN, keinen Fehler. */
    @Test
    public void fehlendeIsinBleibtLeer() throws IOException {
        assertEquals("", doc().securityIsin("E999999"));
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
        div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, true, "Dividenden", div.amountCents, "", 0));
        if (div.amountCents != div.netCents) {
            div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                    0, false, "Bankgebühren", div.amountCents - div.netCents, "", 1));
        }
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
        div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, true, "Dividenden", div.amountCents, "", 0));
        if (div.amountCents != div.netCents) {
            div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                    0, false, "Bankgebühren", div.amountCents - div.netCents, "", 1));
        }
        SecurityTx tx = findWritten(roundtrip(div));
        assertNotNull(tx);
        assertEquals(5000L, tx.amountCents);
        assertEquals(5000L, tx.netCents);
    }

    /**
     * Die aufgeteilte Steuer: je Kategoriezeile ein eigener Split, so wie KMyMoney es führt.
     *
     * <p>Das ist der Grund für die ganze Aufteilung — vorher mussten Kapitalertragsteuer und
     * Solidaritätszuschlag zu einer Zahl addiert und einer der beiden Kategorien zugeschlagen werden,
     * die damit in KMyMoney den falschen Wert trug.</p>
     */
    @Test
    public void dieAufgeteilteSteuerWirdZuMehrerenSplits() throws IOException {
        SecurityTx div = pending("dividend", 0, 90699L, 73953L, 0L);
        div.parts.clear();
        div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, true, "Dividenden", 90699L, "", 0));
        div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, false, "Kapitalertragsteuer", 15873L, "Kapitalertragsteuer", 1));
        div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, false, "Solidaritätszuschlag", 873L, "Solidaritätszuschlag", 2));

        KmyDocument original = doc();
        KmyExporter.SecurityResult res = new KmyExporter(original, ctx)
                .buildSecurityTransactions(original.xml(), Collections.singletonList(div));
        assertEquals("Bewegung wurde übersprungen: " + res.skipped, 1, res.writtenIds.size());
        // Die Transaktion muss sich weiterhin auf 0 summieren – daran hängt, ob KMyMoney sie annimmt.
        assertBalanced(res.xml);

        String geschrieben = neueTransaktion(res.xml);
        assertTrue("Kapitalertragsteuer als eigener Split",
                geschrieben.contains("account=\"A000006\"") && geschrieben.contains("15873/100"));
        assertTrue("Solidaritätszuschlag als eigener Split",
                geschrieben.contains("account=\"A000007\"") && geschrieben.contains("873/100"));
    }

    /** Fehlt eine der Kategorien in der Datei, wird die ganze Bewegung ausgelassen und gemeldet. */
    @Test
    public void eineUnbekannteKategorieLaesstDieBewegungAus() throws IOException {
        SecurityTx buy = pending("buy", 10.0, 25000L, 25000L, 500L);
        buy.parts.clear();
        buy.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, false, "Gibt es nicht", 500L, "", 0));
        assertEquals(0, exportieren(buy).writtenIds.size());
    }

    /**
     * Ein Betrag ganz ohne Kategoriezeile ebenso: ihm fehlt die Gegenseite, und die Transaktion ginge
     * nicht auf. Lieber ausgelassen und gemeldet als eine Datei, die KMyMoney zurückweist.
     */
    @Test
    public void einBetragOhneKategoriezeileLaesstDieBewegungAus() throws IOException {
        SecurityTx buy = pending("buy", 10.0, 25000L, 25000L, 500L);
        buy.parts.clear();
        KmyExporter.SecurityResult res = exportieren(buy);
        assertEquals(0, res.writtenIds.size());
        assertEquals(1, res.skipped.size());
    }

    private KmyExporter.SecurityResult exportieren(SecurityTx tx) throws IOException {
        KmyDocument original = doc();
        return new KmyExporter(original, ctx)
                .buildSecurityTransactions(original.xml(), Collections.singletonList(tx));
    }

    /** Die zuletzt angehängte Transaktion – die aus der Testdatei steht davor. */
    private static String neueTransaktion(String xml) {
        Matcher tx = TX_BLOCK.matcher(xml);
        String last = "";
        while (tx.find()) {
            last = tx.group();
        }
        return last;
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

    /**
     * Die Aktionen, die KMyMoney kennt — wörtlich aus {@code actionNamesLUT} in
     * {@code kmymoney/mymoney/mymoneysplit.cpp}. Dort steht auch der Satz, um den es hier geht:
     * <i>„SellShares is not present as action"</i>.
     */
    private static final java.util.Set<String> KMY_AKTIONEN = new java.util.HashSet<>(java.util.Arrays
            .asList("", "ATM", "Add", "Amortization", "Buy", "Check", "Deposit", "Dividend",
                    "Interest", "IntIncome", "Reinvest", "Split", "Transfer", "Withdrawal", "Yield"));

    /**
     * Ein Verkauf wird als {@code Buy} mit <b>negativer</b> Stückzahl geschrieben — so und nur so führt
     * KMyMoney ihn.
     *
     * <p>Bis 1.13 stand dort {@code action="Sell"}. Das kennt KMyMoney nicht:
     * {@code actionStringToAction} liefert dafür {@code Unknown}, und das Depotbuch fiel auf die
     * Anzeige „Anteile kaufen" zurück. Die Beträge stimmten dabei die ganze Zeit, weshalb es lange
     * niemandem auffiel — auch dem Rundlauf-Test nicht, denn der eigene Importer entscheidet ohnehin
     * am Vorzeichen ({@code KmyImporter.normalizeAction}) und verstand die eigene Schreibweise
     * anstandslos.</p>
     */
    @Test
    public void einVerkaufStehtAlsBuyMitNegativerStückzahlInDerDatei() throws IOException {
        KmyDocument original = doc();
        KmyExporter.SecurityResult res = new KmyExporter(original, ctx).buildSecurityTransactions(
                original.xml(),
                Collections.singletonList(pending("sell", -4.0, 12000L, 11700L, 300L)));
        assertEquals(1, res.writtenIds.size());

        // Die Testdatei bringt selbst einen Kauf mit; der neue wird angehängt, also den letzten nehmen.
        Matcher m = Pattern.compile("<SPLIT\\b[^>]*action=\"Buy\"[^>]*/>").matcher(res.xml);
        String split = null;
        while (m.find()) {
            split = m.group();
        }
        assertNotNull("kein Buy-Split gefunden – steht dort wieder „Sell\"?", split);
        assertEquals("die Stückzahl trägt die Unterscheidung, nicht die Aktion",
                -4.0, fractionOf(split, "shares"), 1e-9);
    }

    /**
     * Und allgemein: keine geschriebene Aktion darf außerhalb dessen liegen, was KMyMoney kennt.
     *
     * <p>Der eigentliche Prüfstein. „Sell" fiel nicht auf, weil die App ihre eigene Erfindung selbst
     * wieder las; ein erfundenes „Remove" für Ausbuchungen fiele genauso wenig auf. Diese Schranke
     * fängt beides.</p>
     */
    @Test
    public void keineGeschriebeneAktionIstKMyMoneyUnbekannt() throws IOException {
        for (SecurityTx tx : java.util.Arrays.asList(
                pending("buy", 10.0, 25000L, 25500L, 500L),
                pending("sell", -4.0, 12000L, 11700L, 300L),
                dividende())) {
            KmyDocument original = doc();
            KmyExporter.SecurityResult res = new KmyExporter(original, ctx).buildSecurityTransactions(
                    original.xml(), Collections.singletonList(tx));
            assertEquals("Bewegung übersprungen: " + res.skipped, 1, res.writtenIds.size());

            Matcher m = Pattern.compile("<SPLIT\\b[^>]*\\baction=\"([^\"]*)\"").matcher(res.xml);
            while (m.find()) {
                assertTrue("KMyMoney kennt die Aktion \"" + m.group(1) + "\" nicht (" + tx.action
                        + ")", KMY_AKTIONEN.contains(m.group(1)));
            }
        }
    }

    /** Eine Dividende mit Ertrags- und Steuerzeile — sonst lässt der Exporter sie aus. */
    private static SecurityTx dividende() {
        SecurityTx div = pending("dividend", 0, 10000L, 7362L, 0L);
        div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, true, "Dividenden", div.amountCents, "", 0));
        div.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, false, "Bankgebühren", div.amountCents - div.netCents, "", 1));
        return div;
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

    /**
     * Steht eine Kategorie in einer anderen Währung als das Verrechnungskonto, fehlt der Umrechnungskurs
     * — geschrieben würde der Split trotzdem, mit {@code price="1/1"}, und stünde betragsmäßig falsch in
     * der Datei. Bei den gewöhnlichen Buchungen wird das seit jeher geprüft; auf dem Wertpapierweg
     * fehlte die Prüfung.
     */
    @Test
    public void eineKategorieInFremderWaehrungWirdNichtStillGeschrieben() throws IOException {
        byte[] roh = KmyRobustnessTest.fixture("security-tx.xml");
        String xml = new String(roh, StandardCharsets.UTF_8).replace(
                "type=\"13\" name=\"Bankgebühren\" description=\"\" currency=\"EUR\"",
                "type=\"13\" name=\"Bankgebühren\" description=\"\" currency=\"USD\"");
        KmyDocument fremd = new KmyDocument(xml.getBytes(StandardCharsets.UTF_8), ctx);

        KmyExporter.SecurityResult res = new KmyExporter(fremd, ctx).buildSecurityTransactions(
                fremd.xml(), Collections.singletonList(pending("buy", 10, 100000L, 0L, 1000L)));

        assertTrue("die Bewegung wird ausgelassen", res.writtenIds.isEmpty());
        assertEquals("und der Grund steht dabei", 1, res.skipped.size());
    }

    /**
     * Eine Kategoriezeile darf gegen die Richtung ihrer Rolle laufen: Zinsertrag 100 €,
     * Kapitalertragsteuer −20 €, Gutschrift 80 €. So rechnet die Erfassungsmaske seit jeher
     * ({@code SplitRowController.isValid} summiert vorzeichenbehaftet), und so führt es auch
     * {@link de.spahr.ausgaben.db.BookingSplit} für gewöhnliche Buchungen.
     *
     * <p>Geprüft wird in <b>beiden Reihenfolgen</b>, und daran hängt alles: die letzte Zeile bekommt
     * ihr Vorzeichen ohnehin über den Rest, der Fehler zeigte sich also nur, wenn die gegenläufige
     * Zeile davor stand. Dann wurde ihr Vorzeichen eingeebnet und der Rest glich die Differenz an der
     * letzten Zeile wieder aus — die Transaktion ging auf null auf, die Beträge standen aber auf den
     * falschen Kategorien. Genau das macht ihn so schwer zu bemerken.</p>
     */
    @Test
    public void eineAbzugszeileBehaeltIhrVorzeichenInJederReihenfolge() throws IOException {
        assertEquals("Steuer zuletzt", -2000L, steuerSplitCents(true));
        assertEquals("Steuer zuerst", -2000L, steuerSplitCents(false));
    }

    /**
     * Baut eine Dividende mit Ertrag 100 € und einer Steuerzeile von −20 € im Ertragstopf und liefert
     * den Betrag zurück, der in der Datei auf der Steuerkategorie landet.
     *
     * <p>Erwartet werden −20 € in Cent: KMyMoney führt den Ertrag mit umgekehrtem Vorzeichen (er kommt
     * von der Kategorie und geht aufs Konto), ein Abzug innerhalb des Ertrags steht dort also positiv.
     * Hier wird über {@code valueToCents} gemessen, das den Bruch der Datei zurückrechnet — die
     * Steuerzeile trägt in der Datei {@code 2000/100}, gemessen also −2000 gegenüber dem Ertrag.</p>
     */
    private long steuerSplitCents(boolean steuerZuletzt) throws IOException {
        SecurityTx tx = pending("dividend", 10, 8000L, 8000L, 0L);
        tx.parts.clear();
        de.spahr.ausgaben.db.SecurityTxSplit ertrag =
                new de.spahr.ausgaben.db.SecurityTxSplit(0, true, "Dividenden", 10000L, "", 0);
        de.spahr.ausgaben.db.SecurityTxSplit steuer =
                new de.spahr.ausgaben.db.SecurityTxSplit(0, true, "Bankgebühren", -2000L, "", 1);
        tx.parts.add(steuerZuletzt ? ertrag : steuer);
        tx.parts.add(steuerZuletzt ? steuer : ertrag);

        KmyDocument original = doc();
        KmyExporter.SecurityResult res = new KmyExporter(original, ctx)
                .buildSecurityTransactions(original.xml(), Collections.singletonList(tx));
        assertEquals("Bewegung wurde übersprungen: " + res.skipped, 1, res.writtenIds.size());
        assertBalanced(res.xml);

        // Erst die eigene Transaktion heraussuchen: die Testdatei bucht selbst schon auf dieselbe
        // Kategorie, und ohne diese Eingrenzung misst der Test deren Betrag statt des geschriebenen.
        String block = null;
        Matcher tx2 = TX_BLOCK.matcher(res.xml);
        while (tx2.find()) {
            if (tx2.group().contains("action=\"Dividend\"")) {
                block = tx2.group();
            }
        }
        assertNotNull("keine Dividenden-Transaktion geschrieben", block);

        // Der Split auf der Kategorie „Bankgebühren" (A000004 in der Testdatei).
        Matcher m = Pattern.compile("<SPLIT\\b[^>]*\\baccount=\"A000004\"[^>]*>").matcher(block);
        assertTrue("kein Split auf der Steuerkategorie", m.find());
        Matcher v = VALUE.matcher(m.group());
        assertTrue("Split ohne Betrag", v.find());
        // Gegenüber dem Ertrag gemessen: der Ertrag steht negativ, der Abzug darin positiv.
        return -valueToCents(v.group(1));
    }

    /**
     * Der Regelweg, der sich <b>nicht</b> ändern darf: Bei einer Dividende gibt man die Steuer als
     * positiven Wert in ihr eigenes Feld ein, und sie wird vom Brutto abgezogen.
     *
     * <p>Brutto 100,00 €, Kapitalertragsteuer 26,00 €, Gutschrift 74,00 €. In der Datei muss danach
     * stehen: Geldkonto +74,00, Ertragskategorie −100,00 (KMyMoney führt den Ertrag mit umgekehrtem
     * Vorzeichen — er kommt von der Kategorie und geht aufs Konto), Steuerkategorie +26,00.</p>
     *
     * <p>Dieser Test hält den <b>unveränderten</b> Zustand fest und kann deshalb keine Gegenprobe
     * haben: er sichert nicht eine Korrektur ab, sondern dass die Umstellung auf vorzeichenbehaftete
     * Teilbeträge den üblichen Weg nicht angerührt hat. Bei positiven Werten ist
     * {@code Math.abs(2600) == 2600} — genau darum darf sich hier nichts bewegen.</p>
     */
    @Test
    public void diePositiveSteuerWirdWeiterhinVomBruttoAbgezogen() throws IOException {
        SecurityTx tx = pending("dividend", 0, 10000L, 7400L, 0L);
        tx.parts.clear();
        tx.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, true, "Dividenden", 10000L, "", 0));
        tx.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, false, "Bankgebühren", 2600L, "", 1));

        KmyDocument original = doc();
        KmyExporter.SecurityResult res = new KmyExporter(original, ctx)
                .buildSecurityTransactions(original.xml(), Collections.singletonList(tx));
        assertEquals("Bewegung wurde übersprungen: " + res.skipped, 1, res.writtenIds.size());
        assertBalanced(res.xml);

        String block = dividendenBlock(res.xml);
        assertEquals("Steuerkategorie", 2600L, splitCents(block, "A000004"));
        assertEquals("Ertragskategorie", -10000L, splitCents(block, "A000005"));
    }

    /** Die zuletzt geschriebene Dividenden-Transaktion. */
    private static String dividendenBlock(String xml) {
        String block = null;
        Matcher tx = TX_BLOCK.matcher(xml);
        while (tx.find()) {
            if (tx.group().contains("action=\"Dividend\"")) {
                block = tx.group();
            }
        }
        assertNotNull("keine Dividenden-Transaktion geschrieben", block);
        return block;
    }

    /** Der Betrag des Splits auf einem bestimmten Konto, in Cent. */
    private static long splitCents(String block, String accountId) {
        Matcher m = Pattern.compile("<SPLIT\\b[^>]*\\baccount=\"" + accountId + "\"[^>]*>")
                .matcher(block);
        assertTrue("kein Split auf " + accountId, m.find());
        Matcher v = VALUE.matcher(m.group());
        assertTrue("Split ohne Betrag", v.find());
        return valueToCents(v.group(1));
    }

    /**
     * Die beiden Schreibweisen derselben Zinsgutschrift treffen sich nach einem Rundlauf durch die
     * KMyMoney-Datei.
     *
     * <p>Eingegeben wird die gemischte Form: Brutto 80,00 €, und darunter im Ertragstopf zwei Zeilen,
     * Zinsertrag 100,00 € und Kapitalertragsteuer −20,00 €. Nach Export und Wiedereinlesen steht die
     * Bewegung in der <b>Regelform</b> da — Brutto 100,00 €, Steuer 20,00 € in ihrem eigenen Topf,
     * Netto 80,00 €. Wirtschaftlich dasselbe, und die Steuer ist wieder positiv.</p>
     *
     * <p>Das ist keine Panne, sondern die Normalisierung durch die Datei: dort steht die
     * Ertragskategorie mit dem vollen Bruttobetrag und die Steuer als eigene Ausgabekategorie. Beim
     * Einlesen ordnet {@code KmyImporter.fillOrigin} die Zeilen nach dem <b>Kontotyp</b> ein, nicht
     * danach, in welchem Feld sie einmal eingetippt wurden. Wer die gemischte Form eingibt, findet sie
     * nach dem nächsten Depot-Import also in der Regelform wieder.</p>
     */
    @Test
    public void dieGemischteFormKommtAlsRegelformZurueck() throws IOException {
        SecurityTx tx = pending("dividend", 0, 8000L, 8000L, 0L);
        tx.parts.clear();
        tx.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, true, "Dividenden", 10000L, "", 0));
        tx.parts.add(new de.spahr.ausgaben.db.SecurityTxSplit(
                0, true, "Bankgebühren", -2000L, "", 1));

        SecurityTx zurueck = null;
        for (SecurityTx t : roundtrip(tx)) {
            if ("dividend".equals(t.action)) {
                zurueck = t;
            }
        }
        assertNotNull("Dividende nicht wiedergefunden", zurueck);

        assertEquals("Brutto", 10000L, zurueck.amountCents);
        assertEquals("Netto", 8000L, zurueck.netCents);
        assertEquals("zwei Kategoriezeilen", 2, zurueck.parts.size());
        for (de.spahr.ausgaben.db.SecurityTxSplit teil : zurueck.parts) {
            if ("Dividenden".equals(teil.category)) {
                assertTrue("der Ertrag steht im Ertragstopf", teil.income);
                assertEquals("Ertrag", 10000L, teil.amountCents);
            } else {
                assertTrue("die Steuer steht im Gebührentopf", !teil.income);
                assertEquals("Steuer wieder positiv", 2000L, teil.amountCents);
            }
        }
    }
}
