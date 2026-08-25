package de.spahr.ausgaben.statement;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.settings.StatementTemplates;
import de.spahr.ausgaben.util.TextValues;

/**
 * Die Abrechnungslogik gegen einen echten Bestand: 2600 anonymisierte Belege aus über hundert Banken,
 * samt der Sollwerte, die Portfolio Performance in seinen eigenen Tests mitführt.
 *
 * <p>Geprüft wird der Weg, den die App wirklich geht — <b>erst lernen, dann erkennen</b>: aus jeder
 * Abrechnung wird gelernt, als hätte der Nutzer sie von Hand erfasst, und danach wird jede noch einmal
 * ohne Sollwerte gelesen und das Ergebnis dagegen gehalten.</p>
 *
 * <p>Der Bestand liegt <b>daneben</b>, nicht im Repository: Portfolio Performance steht unter EPL-1.0,
 * diese App unter GPL-3.0, und die beiden vertragen sich nicht. Ohne den Klon überspringt sich der Test —
 * dasselbe Verfahren wie bei {@code KmyCorpusTest} und {@code CsvCorpusTest} mit dem KMyMoney-Repo.</p>
 *
 * <pre>
 * git clone --depth 1 --filter=blob:none --sparse \
 *     https://github.com/portfolio-performance/portfolio.git ~/git/portfolio
 * cd ~/git/portfolio &amp;&amp; git sparse-checkout set \
 *     name.abuchen.portfolio.tests/src/name/abuchen/portfolio/datatransfer/pdf
 * </pre>
 *
 * <p>Der ausführliche Bericht landet in {@code app/build/reports/statement-corpus.txt}.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatementCorpusTest {

    private static final String DEFAULT_DIR = System.getProperty("user.home")
            + "/git/portfolio/name.abuchen.portfolio.tests/src/name/abuchen/portfolio/datatransfer/pdf";

    /**
     * Wie viele Abrechnungen nach einmaligem Lernen wieder richtig gelesen werden müssen.
     *
     * <p>Keine Zahl aus dem Lehrbuch, sondern der gemessene Stand: sie hält fest, was erreicht ist, und
     * schlägt an, wenn eine Änderung es verschlechtert. Wer sie hebt, hat etwas verbessert.</p>
     */
    private static final double MIN_QUOTE = 0.30;

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private List<PpCorpus.Case> corpus() throws IOException {
        File dir = new File(System.getProperty("pp.corpus", DEFAULT_DIR));
        Assume.assumeTrue("Abrechnungsbestand nicht vorhanden: " + dir, dir.isDirectory());
        List<PpCorpus.Case> cases = PpCorpus.load(dir);
        Assume.assumeFalse(cases.isEmpty());
        return cases;
    }

    // ---- Durchlauf über die Zahlen ----

    /**
     * Jede Zeichenfolge, die zweifelsfrei ein Geldbetrag ist, muss lesbar sein.
     *
     * <p>Das ist die schärfste Aussage, die ohne Sollwerte zu treffen ist, und sie trifft genau die
     * gefährliche Stelle: {@code AnchorRule} nimmt die <b>letzte</b> Zahl einer Zeile: ist die unlesbar,
     * gewinnt lautlos eine frühere.</p>
     */
    @Test
    public void jederGeldbetragImBestandLaesstSichLesen() throws Exception {
        List<String> schlecht = new ArrayList<>();
        int geprueft = 0;
        for (PpCorpus.Case c : corpus()) {
            for (String line : PpCorpus.zeilen(c.source)) {
                for (String token : line.split("\\s+")) {
                    if (!zweifelsfreiGeld(token)) {
                        continue;
                    }
                    geprueft++;
                    if (TextValues.toCents(token) == null && schlecht.size() < 40) {
                        schlecht.add(c.bank + ": " + token + "   [" + line.trim() + ']');
                    }
                }
            }
        }
        assertTrue("Zeichenfolgen geprüft: " + geprueft, geprueft > 10000);
        assertTrue("Diese Beträge werden nicht gelesen:\n  " + String.join("\n  ", schlecht),
                schlecht.isEmpty());
    }

    /**
     * Ob eine Zeichenfolge zweifelsfrei ein Geldbetrag ist: Währungszeichen, Vorzeichen, eine einzige
     * Gruppierungsart, ein davon verschiedener Dezimaltrenner und genau zwei Nachkommastellen.
     *
     * <p>Eng gefasst mit Absicht. Der Bestand ist voll von Zeichenfolgen, die einer Zahl gleichen und
     * keine sind — {@code 495752/48.00} (Auftragsnummer), {@code 1.234.567.890} (Depotnummer),
     * {@code 0.123.123.01} (Kontonummer, Punkt als Gruppierung <i>und</i> Trenner),
     * {@code 11.6.2022-01:30:01} (Zeitstempel). Die dürfen nicht lesbar sein, und der Parser lehnt sie
     * ab; diese Prüfung darf sie deshalb gar nicht erst verlangen.</p>
     */
    private static boolean zweifelsfreiGeld(String token) {
        String s = token;
        if (s.length() > 2 && s.charAt(0) == '(' && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1);
        }
        if (!s.isEmpty() && "€$£¥".indexOf(s.charAt(0)) >= 0) {
            s = s.substring(1);
        }
        if (!s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
            s = s.substring(1);
        }
        if (s.length() > 3 && s.substring(s.length() - 3).matches("[A-Z]{3}")) {
            s = s.substring(0, s.length() - 3);
        }
        if (!s.isEmpty() && (s.endsWith("-") || s.endsWith("+"))) {
            s = s.substring(0, s.length() - 1);
        }
        if (!s.matches("\\d[\\d.,'’]*[.,](\\d{2}|--)")) {
            return false;
        }
        int cut = s.length() - 3;
        char trenner = s.charAt(cut);
        String vorn = s.substring(0, cut);
        if (!vorn.matches("\\d{1,3}([.,'’]\\d{3})*") && !vorn.matches("\\d+")) {
            return false;
        }
        // Gruppierung und Dezimaltrenner müssen sich unterscheiden – sonst ist es keine Zahl, sondern
        // eine Konto- oder Depotnummer.
        for (char c : vorn.toCharArray()) {
            if (c == trenner) {
                return false;
            }
        }
        return true;
    }

    // ---- Lernen und wiedererkennen ----

    @Test
    public void ausJederAbrechnungGelerntUndWiederGelesen() throws Exception {
        List<PpCorpus.Case> cases = corpus();
        Map<String, String> jeBank = durchlauf(cases, false);
        Map<String, String> gemeinsam = durchlauf(cases, true);

        StringBuilder bericht = new StringBuilder();
        int okBank = 0;
        int okAlle = 0;
        Map<String, int[]> proBank = new TreeMap<>();
        for (PpCorpus.Case c : cases) {
            String key = c.bank + '/' + c.file;
            boolean a = jeBank.get(key) == null;
            boolean b = gemeinsam.get(key) == null;
            if (a) {
                okBank++;
            }
            if (b) {
                okAlle++;
            }
            int[] z = proBank.computeIfAbsent(c.bank, k -> new int[3]);
            z[0]++;
            if (a) {
                z[1]++;
            }
            if (b) {
                z[2]++;
            }
        }
        bericht.append("Abrechnungen: ").append(cases.size())
                .append("   Banken: ").append(proBank.size()).append("\n")
                .append("gelesen mit eigenem Vorlagenspeicher je Bank: ").append(okBank)
                .append(String.format("  (%.1f %%)%n", 100.0 * okBank / cases.size()))
                .append("gelesen mit einem Speicher für alle Banken:   ").append(okAlle)
                .append(String.format("  (%.1f %%)%n%n", 100.0 * okAlle / cases.size()));
        for (Map.Entry<String, int[]> e : proBank.entrySet()) {
            int[] z = e.getValue();
            bericht.append(String.format("%-38s %3d Abrechnungen   je Bank %3d   gemeinsam %3d%n",
                    e.getKey(), z[0], z[1], z[2]));
        }
        bericht.append("\n--- was im Einzelnen fehlschlug (je Bank) ---\n");
        for (PpCorpus.Case c : cases) {
            String grund = jeBank.get(c.bank + '/' + c.file);
            if (grund != null) {
                bericht.append(String.format("%-34s %-28s %s%n", c.bank, c.file, grund));
            }
        }
        schreibeBericht(bericht.toString());

        double quote = 1.0 * okBank / cases.size();
        assertTrue(String.format("Nur %.1f %% gelesen, erwartet mindestens %.1f %% "
                        + "(Bericht: app/build/reports/statement-corpus.txt)",
                100 * quote, 100 * MIN_QUOTE), quote >= MIN_QUOTE);
    }

    /**
     * Die Obergrenze des Modells: kann eine Ankerregel überhaupt ausdrücken, was auf dem Beleg steht?
     *
     * <p>Gemessen an der Abrechnung selbst — aus ihr lernen und sie sofort wieder lesen. Was hier
     * gelingt, ließe sich auf der Regelseite auch von Hand einstellen; was hier scheitert, bekommt
     * <b>kein</b> Nutzer hin, egal wie lange er an den Beschriftungen dreht. Das ist die Zahl, die sagt,
     * ob das Ankermodell für eine Bank taugt.</p>
     */
    @Test
    public void wasSichUeberhauptAusdrueckenLaesst() throws Exception {
        List<PpCorpus.Case> cases = corpus();
        Map<String, int[]> proBank = new TreeMap<>();
        StringBuilder gruende = new StringBuilder();
        int ok = 0;
        for (PpCorpus.Case c : cases) {
            PdfText text = PpCorpus.text(c);
            StatementTemplate eigen = lerne(c, text, null);
            String grund = eigen.isEmpty() ? "nichts gelernt" : pruefe(c, eigen, text);
            int[] z = proBank.computeIfAbsent(c.bank, k -> new int[2]);
            z[0]++;
            if (grund == null) {
                ok++;
                z[1]++;
            } else {
                gruende.append(String.format("%-34s %-28s %s%n", c.bank, c.file, grund));
            }
        }
        StringBuilder bericht = new StringBuilder();
        bericht.append("Was das Ankermodell ausdrücken kann (aus der Abrechnung selbst gelernt)\n")
                .append("Abrechnungen: ").append(cases.size())
                .append("   davon ausdrückbar: ").append(ok)
                .append(String.format("  (%.1f %%)%n%n", 100.0 * ok / cases.size()));
        int volle = 0;
        for (Map.Entry<String, int[]> e : proBank.entrySet()) {
            int[] z = e.getValue();
            if (z[0] == z[1]) {
                volle++;
            }
            bericht.append(String.format("%-38s %3d von %3d%n", e.getKey(), z[1], z[0]));
        }
        bericht.append("\nBanken, deren Abrechnungen sich alle ausdrücken lassen: ")
                .append(volle).append(" von ").append(proBank.size()).append("\n")
                .append("\n--- was sich nicht ausdrücken lässt ---\n").append(gruende);
        schreibeBericht(bericht.toString(), "statement-modell.txt");
        assertTrue("Ausdrückbar: " + ok, ok > 0);
    }

    /**
     * Dasselbe, aber nur mit den <b>jüngsten</b> Abrechnungen je Bank und Art.
     *
     * <p>Der Bestand deckt zehn Jahre ab, und eine Bank steht darin mit fünf Beleggenerationen. Ein
     * Nutzer bekommt aber nur, was seine Bank heute verschickt — er lernt an einem aktuellen Beleg und
     * liest damit den nächsten. Diese Zahl sagt, wie gut das geht; die über den ganzen Bestand sagt, wie
     * gut eine Vorlage auch die Belege von vorgestern noch trägt.</p>
     */
    @Test
    public void nurDieJuengstenAbrechnungen() throws Exception {
        List<PpCorpus.Case> juengste = juengste(corpus(), 3);
        Map<String, String> jeBank = durchlauf(juengste, false);
        int ok = 0;
        Map<String, int[]> proBank = new TreeMap<>();
        StringBuilder gruende = new StringBuilder();
        for (PpCorpus.Case c : juengste) {
            String grund = jeBank.get(c.bank + '/' + c.file);
            int[] z = proBank.computeIfAbsent(c.bank, k -> new int[2]);
            z[0]++;
            if (grund == null) {
                ok++;
                z[1]++;
            } else {
                gruende.append(String.format("%-34s %-28s %s%n", c.bank, c.file, grund));
            }
        }
        int voll = 0;
        int gar = 0;
        StringBuilder bericht = new StringBuilder();
        for (Map.Entry<String, int[]> e : proBank.entrySet()) {
            int[] z = e.getValue();
            if (z[0] == z[1]) {
                voll++;
            }
            if (z[1] == 0) {
                gar++;
            }
            bericht.append(String.format("%-38s %3d von %3d%n", e.getKey(), z[1], z[0]));
        }
        String kopf = "Nur die juengsten drei Abrechnungen je Bank und Art\n"
                + "Abrechnungen: " + juengste.size()
                + String.format("   gelesen: %d  (%.1f %%)%n", ok, 100.0 * ok / juengste.size())
                + "Banken vollstaendig: " + voll + "   gar nicht: " + gar
                + "   von " + proBank.size() + "\n\n";
        schreibeBericht(kopf + bericht + "\n--- Fehlschlaege ---\n" + gruende,
                "statement-aktuell.txt");
        assertTrue(ok > 0);
    }

    /** Je Bank und Art die {@code n} jüngsten Abrechnungen. */
    private static List<PpCorpus.Case> juengste(List<PpCorpus.Case> cases, int n) {
        Map<String, List<PpCorpus.Case>> gruppen = new LinkedHashMap<>();
        for (PpCorpus.Case c : cases) {
            gruppen.computeIfAbsent(c.bank + '|' + c.kind, k -> new ArrayList<>()).add(c);
        }
        List<PpCorpus.Case> out = new ArrayList<>();
        for (List<PpCorpus.Case> gruppe : gruppen.values()) {
            gruppe.sort((a, b) -> Long.compare(b.dateMillis, a.dateMillis));
            out.addAll(gruppe.subList(0, Math.min(n, gruppe.size())));
        }
        return out;
    }

    /**
     * Erst lernen, dann erkennen.
     *
     * @param gemeinsam alle Banken in einem Vorlagenspeicher — dann zeigt sich, ob die Vorlagen einander
     *                  in die Quere kommen. Sonst je Bank ein eigener, was allein die Anker- und
     *                  Zahlenlogik misst.
     * @return je Abrechnung der Grund des Fehlschlags, oder {@code null} bei Erfolg
     */
    private Map<String, String> durchlauf(List<PpCorpus.Case> cases, boolean gemeinsam)
            throws IOException {
        Map<String, String> ergebnis = new LinkedHashMap<>();
        Map<String, List<PpCorpus.Case>> banken = new LinkedHashMap<>();
        for (PpCorpus.Case c : cases) {
            banken.computeIfAbsent(gemeinsam ? "*" : c.bank, k -> new ArrayList<>()).add(c);
        }
        for (List<PpCorpus.Case> gruppe : banken.values()) {
            StatementTemplates store = new StatementTemplates(ctx);
            store.clearAll();
            Map<String, PdfText> texte = new LinkedHashMap<>();

            // Durchlauf 1: lernen, wie es die Maske beim Speichern tut.
            for (PpCorpus.Case c : gruppe) {
                PdfText text = PpCorpus.text(c);
                texte.put(c.bank + '/' + c.file, text);
                StatementTemplate gelernt = lerne(c, text, store.match(text));
                if (!gelernt.isEmpty()) {
                    store.save(gelernt);
                }
            }
            // Einmal laden statt je Abrechnung: das JSON-Format prüft StatementRoundtripTest.
            List<StatementTemplate> vorlagen = store.all();

            // Durchlauf 2: erkennen, ohne die Sollwerte zu kennen.
            for (PpCorpus.Case c : gruppe) {
                PdfText text = texte.get(c.bank + '/' + c.file);
                ergebnis.put(c.bank + '/' + c.file,
                        pruefe(c, StatementTemplates.best(vorlagen, text), text));
            }
        }
        return ergebnis;
    }

    private static StatementTemplate lerne(PpCorpus.Case c, PdfText text, StatementTemplate vorhanden) {
        boolean dividende = "dividend".equals(c.kind);
        TemplateLearner.Known k = new TemplateLearner.Known();
        k.action = c.kind;
        // Bei einer Dividende liest die App weder Stückzahl noch Stückpreis – der Bestand am Ex-Tag steht
        // nicht in der Abrechnung.
        k.shares = dividende ? null : c.shares;
        k.price = dividende ? null : c.price();
        k.feeCents = c.chargeCents();
        k.netCents = c.netCents;
        k.dateMillis = c.dateMillis;
        return TemplateLearner.learn(text, k).mergedOver(vorhanden);
    }

    /** Was beim Wiederlesen herauskam, gegen die Sollwerte; {@code null}, wenn alles stimmt. */
    private static String pruefe(PpCorpus.Case c, StatementTemplate t, PdfText text) {
        if (t == null) {
            return "keine Vorlage trifft";
        }
        if (!c.kind.equals(t.action)) {
            return "Art " + t.action + " statt " + c.kind;
        }
        StatementTemplate.Extraction e = t.apply(text);
        String fehler = geld("Netto", e.netCents, c.netCents);
        if (fehler != null) {
            return fehler;
        }
        Long last = c.chargeCents();
        if (last != null && last != 0) {
            fehler = geld("Gebühr/Steuer", e.feeCents, last);
            if (fehler != null) {
                return fehler;
            }
        }
        if (!"dividend".equals(c.kind) && c.shares != null) {
            if (e.shares == null) {
                return "Stückzahl nicht gelesen";
            }
            if (Math.abs(e.shares - c.shares) > 1e-6) {
                return "Stückzahl " + e.shares + " statt " + c.shares;
            }
        }
        if (c.dateMillis > 0 && e.dateMillis != c.dateMillis) {
            return e.dateMillis <= 0 ? "Datum nicht gelesen" : "Datum abweichend";
        }
        return null;
    }

    private static String geld(String feld, Long ist, Long soll) {
        if (soll == null) {
            return null;
        }
        if (ist == null) {
            return feld + " nicht gelesen (erwartet " + soll + ')';
        }
        // Ein Cent Abweichung darf sein: Kurs mal Stückzahl rundet je nach Reihenfolge anders.
        return Math.abs(ist - soll) <= 1 ? null : feld + ' ' + ist + " statt " + soll;
    }

    private static void schreibeBericht(String text) {
        schreibeBericht(text, "statement-corpus.txt");
    }

    private static void schreibeBericht(String text, String name) {
        try {
            File out = new File("build/reports/" + name);
            //noinspection ResultOfMethodCallIgnored
            out.getParentFile().mkdirs();
            Files.write(out.toPath(), text.getBytes(StandardCharsets.UTF_8));
            System.out.println(text);
        } catch (IOException ignored) {
            System.out.println(text);
        }
    }
}
