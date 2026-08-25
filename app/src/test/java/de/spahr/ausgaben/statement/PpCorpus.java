package de.spahr.ausgaben.statement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.util.TextValues;

/**
 * Liest den Abrechnungsbestand von <a href="https://github.com/portfolio-performance/portfolio">Portfolio
 * Performance</a> — die Texte <b>und</b> die Sollwerte, die dort in den eigenen Tests stehen.
 *
 * <p>Nur zum Prüfen. Nichts davon liegt im Repository: Portfolio Performance steht unter EPL-1.0, diese
 * App unter GPL-3.0, und die beiden vertragen sich nicht. Der Bestand wird daneben geklont und zur
 * Laufzeit gelesen (siehe {@link StatementCorpusTest}).</p>
 *
 * <p>Die Sollwerte stehen dort in zwei Schreibweisen, beide streng regelmäßig:</p>
 * <pre>
 * // ältere Tests
 * assertThat(transaction.getMonetaryAmount(), is(Money.of("EUR", Values.Amount.factorize(144.52))));
 *
 * // neuere Tests
 * assertThat(results, hasItem(purchase(hasDate("2007-05-19T00:00"), hasShares(100.00),
 *                 hasAmount("EUR", 8627.90), hasFees("EUR", 26.40 + 1.50))));
 * </pre>
 *
 * <p>Ausgewertet werden nur Methoden, die <b>genau eine</b> Datei laden und deren Art eindeutig Kauf,
 * Verkauf oder Dividende ist. Alles andere — Kontoauszüge, Vorabpauschalen, Steuerbehandlungen,
 * Überträge — wird übergangen; dort gäbe es nichts zu vergleichen.</p>
 */
final class PpCorpus {

    /** Eine Abrechnung samt dem, was Portfolio Performance daraus erwartet. */
    static final class Case {
        String bank;
        String file;
        String method;
        /** {@code buy}, {@code sell} oder {@code dividend}. */
        String kind;
        String isin;
        String currency = "";
        long dateMillis = -1;
        Double shares;
        Long netCents;
        Long grossCents;
        Long taxCents;
        Long feeCents;
        File source;

        /**
         * Was in dieser App das Gebühren-/Steuerfeld trägt: bei einer Dividende die Steuer, bei Kauf und
         * Verkauf die Gebühren <b>und</b> die Steuer — dort führt die Maske nur ein Feld.
         */
        Long chargeCents() {
            if ("dividend".equals(kind)) {
                return taxCents;
            }
            if (feeCents == null && taxCents == null) {
                return null;
            }
            return (feeCents == null ? 0 : feeCents) + (taxCents == null ? 0 : taxCents);
        }

        /** Der Stückpreis, wie er in der Abrechnung stehen sollte. */
        Double price() {
            if (grossCents == null || shares == null || Math.abs(shares) < 1e-9) {
                return null;
            }
            return grossCents / 100.0 / shares;
        }

        @Override
        public String toString() {
            return bank + '/' + file;
        }
    }

    private static final Pattern METHOD = Pattern.compile("\\n {4}public void (test\\w+)\\(\\)");
    private static final Pattern FILE = Pattern.compile("loadTestCase\\(getClass\\(\\),\\s*\"([^\"]+)\"");
    private static final Pattern NEW_KIND = Pattern.compile("hasItem\\((purchase|sale|dividend)\\(");
    private static final Pattern NEW_DATE = Pattern.compile("hasDate\\(\"([^\"]+)\"\\)");
    private static final Pattern NEW_SHARES = Pattern.compile("hasShares\\(([^)]+)\\)");
    private static final Pattern NEW_ISIN = Pattern.compile("hasIsin\\(\"([^\"]+)\"\\)");
    private static final Pattern OLD_DATE = Pattern.compile(
            "get(?:DateTime|Date)\\(\\),\\s*is\\(LocalDateTime\\.parse\\(\"([^\"]+)\"\\)");
    private static final Pattern OLD_SHARES = Pattern.compile(
            "getShares\\(\\),\\s*is\\(Values\\.Share\\.factorize\\(([^)]+)\\)");
    private static final Pattern OLD_ISIN = Pattern.compile("getIsin\\(\\),\\s*is\\(\"([^\"]+)\"\\)");

    private PpCorpus() {
    }

    /** Alle auswertbaren Abrechnungen des Bestands, nach Bank und Datei sortiert. */
    static List<Case> load(File root) throws IOException {
        List<Case> cases = new ArrayList<>();
        File[] banks = root.listFiles(File::isDirectory);
        if (banks == null) {
            return cases;
        }
        Arrays.sort(banks, Comparator.comparing(File::getName));
        for (File bank : banks) {
            File[] tests = bank.listFiles((d, n) -> n.endsWith("PDFExtractorTest.java"));
            if (tests == null) {
                continue;
            }
            for (File test : tests) {
                readTests(bank, test, cases);
            }
        }
        return cases;
    }

    private static void readTests(File bank, File test, List<Case> out) throws IOException {
        String src = new String(Files.readAllBytes(test.toPath()), StandardCharsets.UTF_8);
        Matcher m = METHOD.matcher(src);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
            starts.add(m.end());
            names.add(m.group(1));
        }
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : src.length();
            Case c = parse(bank, names.get(i), src.substring(starts.get(i), end));
            if (c != null) {
                out.add(c);
            }
        }
    }

    private static Case parse(File bank, String method, String body) {
        Matcher files = FILE.matcher(body);
        if (!files.find()) {
            return null;
        }
        String file = files.group(1);
        if (files.find()) {
            return null;   // mehrere Dateien in einer Methode: nicht eindeutig zuzuordnen
        }
        Case c = new Case();
        c.bank = bank.getName();
        c.file = file;
        c.method = method;
        c.source = new File(bank, file);
        if (!c.source.isFile()) {
            return null;
        }
        Matcher kind = NEW_KIND.matcher(body);
        if (kind.find()) {
            c.kind = "purchase".equals(kind.group(1)) ? "buy"
                    : "sale".equals(kind.group(1)) ? "sell" : "dividend";
            c.dateMillis = date(find(NEW_DATE, body));
            c.shares = number(find(NEW_SHARES, body));
            c.netCents = amount(body, "hasAmount", c);
            c.grossCents = amount(body, "hasGrossValue", c);
            c.taxCents = amount(body, "hasTaxes", c);
            c.feeCents = amount(body, "hasFees", c);
            c.isin = find(NEW_ISIN, body);
        } else {
            if (body.contains("Type.DIVIDENDS")) {
                c.kind = "dividend";
            } else if (body.contains("PortfolioTransaction.Type.BUY")) {
                c.kind = "buy";
            } else if (body.contains("PortfolioTransaction.Type.SELL")) {
                c.kind = "sell";
            } else {
                return null;
            }
            c.dateMillis = date(find(OLD_DATE, body));
            c.shares = number(find(OLD_SHARES, body));
            c.netCents = oldAmount(body, "get(?:MonetaryAmount|Amount)", c);
            c.grossCents = oldAmount(body, "getGrossValue", c);
            c.taxCents = oldAmount(body, "getUnitSum\\(Unit\\.Type\\.TAX\\)", c);
            c.feeCents = oldAmount(body, "getUnitSum\\(Unit\\.Type\\.FEE\\)", c);
            c.isin = find(OLD_ISIN, body);
        }
        return c.netCents == null ? null : c;
    }

    private static Long amount(String body, String method, Case c) {
        Matcher m = Pattern.compile(method + "\\(\"(\\w+)\",\\s*([^)]+)\\)").matcher(body);
        if (!m.find()) {
            return null;
        }
        if (c.currency.isEmpty()) {
            c.currency = m.group(1);
        }
        Double value = sum(m.group(2));
        return value == null ? null : Math.round(value * 100.0);
    }

    private static Long oldAmount(String body, String method, Case c) {
        Matcher m = Pattern.compile(
                method + "\\(\\),?\\s*is\\(Money\\.of\\(\"(\\w+)\",\\s*Values\\.Amount\\.factorize\\(([^)]+)\\)",
                Pattern.DOTALL).matcher(body);
        if (!m.find()) {
            return null;
        }
        if (c.currency.isEmpty()) {
            c.currency = m.group(1);
        }
        Double value = sum(m.group(2));
        return value == null ? null : Math.round(value * 100.0);
    }

    private static String find(Pattern p, String body) {
        Matcher m = p.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** {@code 32.09 + 1.76 + 2.88} ausrechnen — so schreibt Portfolio Performance geteilte Steuern. */
    private static Double sum(String expression) {
        String s = expression.replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        double total = 0;
        int i = 0;
        int sign = 1;
        StringBuilder token = new StringBuilder();
        while (i <= s.length()) {
            char ch = i < s.length() ? s.charAt(i) : '+';
            if ((ch == '+' || ch == '-') && token.length() > 0) {
                Double part = number(token.toString());
                if (part == null) {
                    return null;
                }
                total += sign * part;
                sign = ch == '-' ? -1 : 1;
                token.setLength(0);
            } else if (ch == '-' && token.length() == 0) {
                sign = -sign;
            } else if (ch != '+') {
                token.append(ch);
            }
            i++;
        }
        return total;
    }

    private static Double number(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code 2016-01-04T00:00} → Millisekunden zur lokalen Mitternacht. */
    private static long date(String raw) {
        if (raw == null || raw.length() < 10) {
            return -1;
        }
        return TextValues.toDateMillis(raw.substring(0, 10));
    }

    /**
     * Der Abrechnungstext ohne den Kopf, den Portfolio Performance seinen Testdateien voranstellt
     * (PDFBox- und Programmversion, dann eine Trennlinie) — der gehört nicht zum Beleg.
     */
    static PdfText text(Case c) throws IOException {
        List<String> lines = zeilen(c.source);
        StringBuilder out = new StringBuilder();
        boolean started = false;
        for (String line : lines) {
            if (!started) {
                if (line.startsWith("-----")) {
                    started = true;
                }
                continue;
            }
            out.append(line).append('\n');
        }
        return PdfText.fromLines(started ? out.toString() : String.join("\n", lines));
    }

    /**
     * Zeilen einer Testdatei. Über die Bytes gelesen, nicht über {@code Files.readAllLines}: ein paar
     * Dateien des Bestands sind kein sauberes UTF-8 (eine Schweizer Bank schreibt „Münsingen" in
     * Latin-1), und daran soll der Durchlauf nicht scheitern — ein ersetztes Zeichen im Namen der Bank
     * ändert an den Zahlen nichts.
     */
    static List<String> zeilen(File file) throws IOException {
        String all = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return Arrays.asList(all.split("\r?\n", -1));
    }
}
