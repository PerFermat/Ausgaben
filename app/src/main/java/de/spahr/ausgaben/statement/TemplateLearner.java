package de.spahr.ausgaben.statement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.util.TextValues;

/**
 * Leitet aus einer <b>einmal selbst erfassten</b> Abrechnung ab, wo die Werte stehen.
 *
 * <p>Der Nutzer tippt die erste Abrechnung einer Bank ohnehin ab. Danach kennt die App seine Zahlen und
 * den Text des Dokuments — und kann die Beschriftungen selbst finden: sie sucht {@code 1.000,00} und
 * sieht links daneben „Endbetrag zu Ihren Lasten". Eine Markier-Oberfläche, in der man auf dem Handy
 * kleine Zahlen antippt, wird damit überflüssig, und das Ergebnis ist robuster als eine Markierung.</p>
 *
 * <p>Reine Rechenklasse ohne Android-Bezug.</p>
 */
public final class TemplateLearner {

    /** Stückzahlen müssen genau stimmen – 0,005 Stück wären bei einem Sparplan ein echter Unterschied. */
    private static final double SHARE_EPSILON = 0.0000005;

    /**
     * Geldbeträge und Kurse dürfen um einen halben Cent abweichen.
     *
     * <p>Nötig, weil die Maske einen fehlenden Wert zurückrechnet und dabei in der letzten Stelle von der
     * Bank abweicht: 1.000,00 ÷ 6,09607 ergibt 164,0401, im Dokument steht 164,04. Ohne die Toleranz
     * fände der Lerner die Zeile nicht und käme nie zu einer Kursregel — der Wert der Abrechnung gilt.</p>
     */
    private static final double MONEY_EPSILON = 0.005;

    /** Höchstens so viele Zeilen werden zu einer Summe zusammengesucht (Steuer + Soli + Kirchensteuer). */
    private static final int MAX_SUMMANDS = 3;

    /** Bis zu dieser Stelle wird in einer Zeile nach dem Wert gesucht (siehe {@code stelleIn}). */
    private static final int MAX_STELLE = 3;

    private TemplateLearner() {
    }

    /** Die Werte, die der Nutzer für dieses Dokument eingetragen hat. Nicht Gesetztes ist {@code null}. */
    public static final class Known {
        public String action;
        public Double shares;
        public Double price;
        public Long feeCents;
        /** Kategorie der Gebühr — nur gebraucht, wenn daraus eine feste Ordergebühr wird. */
        public String feeCategory = "";
        public Long netCents;
        public long dateMillis = -1;
        /**
         * Die Beschriftung, die der Nutzer für das Datum ausgewählt hat — sie hat Vorrang. Nötig, weil
         * mehrere Zeilen dasselbe Datum tragen können („Zahltag" und „Valuta" fallen oft zusammen); ohne
         * seine Wahl würde die unterste gelernt, und in der nächsten Abrechnung wäre es die falsche.
         */
        public String dateAnchor;
    }

    /**
     * Baut die Vorlage. Für jedes Feld, dessen Wert sich im Text eindeutig wiederfindet, entsteht eine
     * Regel; für die übrigen <b>keine</b> — eine geratene Regel wäre schlimmer als gar keine, denn sie
     * belegt künftig still das falsche Feld vor.
     */
    public static StatementTemplate learn(PdfText text, Known known) {
        Map<StatementTemplate.Field, AnchorRule> rules =
                new EnumMap<>(StatementTemplate.Field.class);
        if (text == null || known == null) {
            return new StatementTemplate(known == null ? null : known.action, rules);
        }
        List<String> used = new ArrayList<>();

        // Reihenfolge nach Unterscheidungskraft: die Gesamtsumme ist die kennzeichnendste Zahl, die
        // Stückzahl die unscheinbarste – eine glatte 10 findet sich in einer Abrechnung schnell mehrfach.
        // Die Währung wird nur dort festgehalten, wo sie feststeht: Gesamtsumme und Steuer gehen als
        // Buchung aufs Konto und sind deshalb immer in Kontowährung. Der Stückpreis dagegen ist die
        // Notierung des Wertpapiers – bei einem Dollar-Papier steht dort USD, bei einem Euro-Papier EUR.
        // Bände man ihn an die einmal gelernte Währung, ginge nach dem Lernen an einem Dollar-Papier
        // kein Euro-Papier mehr, und umgekehrt.
        AnchorRule net = forValue(text, cents(known.netCents), MONEY_EPSILON, true, used);
        put(rules, StatementTemplate.Field.NET, net, used);
        AnchorRule fee = forValue(text, cents(known.feeCents), MONEY_EPSILON, true, used);
        put(rules, StatementTemplate.Field.FEE, fee, used);

        // Weder Gesamtbetrag noch Gebühr gefunden? Dann kann beides denselben Grund haben: die Bank nimmt
        // eine feste Ordergebühr, druckt sie nicht aus – und ihr Gesamtbetrag ist der ohne diese Gebühr.
        long fixedFee = 0;
        if (net == null && fee == null) {
            AnchorRule ersatz = forValue(text, cents(printedTotal(known)), MONEY_EPSILON, true, used);
            if (ersatz != null) {
                put(rules, StatementTemplate.Field.NET, ersatz, used);
                fixedFee = known.feeCents;
            }
        }

        put(rules, StatementTemplate.Field.PRICE,
                forValue(text, known.price, MONEY_EPSILON, false, used), used);
        put(rules, StatementTemplate.Field.SHARES,
                forValue(text, known.shares, SHARE_EPSILON, false, used), used);
        put(rules, StatementTemplate.Field.DATE,
                forDate(text, known.dateMillis, known.dateAnchor, used), used);
        return new StatementTemplate(known.action, rules, fixedFee,
                fixedFee > 0 ? known.feeCategory : "", false);
    }

    /**
     * Der Betrag, den die Bank ausdrucken würde, wenn ihr Gesamtbetrag die feste Gebühr <b>nicht</b>
     * enthält — oder {@code null}, wenn diese Vermutung hier nicht in Frage kommt.
     *
     * <p>Beim Kauf erhöht die Gebühr die Belastung, beim Verkauf mindert sie die Gutschrift; der
     * ausgedruckte Betrag ist also der eingetragene ohne sie. Dasselbe Vorzeichen benutzt
     * {@code SecurityAmounts}.</p>
     *
     * <p><b>Nur bei Kauf und Verkauf.</b> Bei einer Dividende ist die „Gebühr" die Steuer, und
     * Brutto minus Steuer ergibt das Netto — die Rechnung träfe dort regelmäßig die Bruttozeile und
     * erfände eine Ordergebühr, die es nicht gibt.</p>
     */
    private static Long printedTotal(Known known) {
        boolean kauf = StatementScan.BUY.equals(known.action);
        boolean verkauf = StatementScan.SELL.equals(known.action);
        if ((!kauf && !verkauf) || known.netCents == null
                || known.feeCents == null || known.feeCents == 0) {
            return null;
        }
        return known.netCents - (kauf ? known.feeCents : -known.feeCents);
    }

    private static Double cents(Long value) {
        return value == null ? null : value / 100.0;
    }

    private static void put(Map<StatementTemplate.Field, AnchorRule> rules,
                            StatementTemplate.Field field, AnchorRule rule, List<String> used) {
        if (rule != null) {
            rules.put(field, rule);
            for (String anchor : rule.anchors) {
                used.add(taken(anchor, rule.position));
            }
        }
    }

    /**
     * Der Merkposten für eine schon vergebene Beschriftung — samt der Zahl, die sie meint.
     *
     * <p>Die Stelle gehört dazu, weil eine Zeile zwei Felder tragen kann: „St. 1.437 EUR 37,22" nennt
     * vorn die Stückzahl und hinten den Kurs. Ohne die Unterscheidung nähme sich der Kurs die
     * Beschriftung „St.", und für die Stückzahl bliebe keine übrig.</p>
     */
    private static String taken(String anchor, AnchorRule.Position position) {
        return anchor + '\u001F' + position;
    }

    /**
     * Sucht eine Regel für {@code value}. Zuerst in einer einzelnen Zeile; findet sich der Wert dort
     * nicht, wird er als <b>Summe</b> mehrerer Zeilen gesucht — so steht die Steuer bei der ING, auf
     * Kapitalertragsteuer und Solidaritätszuschlag verteilt.
     */
    private static AnchorRule forValue(PdfText text, Double value, double epsilon,
                                       boolean bindCurrency, List<String> used) {
        if (value == null) {
            return null;
        }
        AnchorRule single = singleLine(text, value, epsilon, bindCurrency, used);
        if (single != null) {
            return single;
        }
        return summedLines(text, value, epsilon, bindCurrency, used);
    }

    /** Der Wert steht als letzte Zahl einer Zeile — der Regelfall. */
    private static AnchorRule singleLine(PdfText text, double value, double epsilon,
                                         boolean bindCurrency, List<String> used) {
        String anchor = null;
        String currency = "";
        AnchorRule.Direction direction = AnchorRule.Direction.SAME_LINE;
        AnchorRule.Position position = AnchorRule.Position.LAST;
        int nthFound = 1;
        List<PdfText.Line> lines = text.lines();
        for (int i = 0; i < lines.size(); i++) {
            // Von aussen nach innen: erst die letzte Zahl – der Regelfall –, dann die erste (unter einer
            // Spaltenüberschrift führt der Wert die Zeile an), dann die zweitletzte und so fort. In einer
            // Tabellenzeile steht der gesuchte Wert weiter innen: „… MARGIN 5 $74.33000" nennt den Kurs
            // zuletzt und die Menge davor.
            int[] stelle = stelleIn(lines.get(i).text(), value, epsilon);
            if (stelle == null) {
                continue;
            }
            AnchorRule.Position where = stelle[0] == 1
                    ? AnchorRule.Position.FIRST : AnchorRule.Position.LAST;
            int nth = stelle[1];
            // Beschriftung in derselben Zeile — erst die unmittelbar vor dem Wert, dann die am
            // Zeilenanfang. Beides wird gebraucht: „Beispielstraße 1 DATUM 13.05.2019" gibt nur die erste
            // her, „Stückzinsen für 153 Tage per 26.11.2015 73,16-" nur die zweite. Hat die Zeile keine
            // brauchbare, steht sie eine Zeile darüber.
            String own = firstUsable(used, where,
                    labelBefore(lines.get(i).text(), where), labelOf(lines.get(i).text()));
            if (own != null) {
                anchor = own;
                direction = AnchorRule.Direction.SAME_LINE;
                // Die Währung aus dem Text hinter der Beschriftung – genau dem, den die Regel beim Lesen
                // ansieht. Nähme man die ganze Zeile, lernte „UNSOLICITED NET AMOUNT $371.65" die
                // Währung „NET" (drei Großbuchstaben) und fände sie nie wieder.
                currency = bindCurrency
                        ? AnchorRule.currencyOf(AnchorRule.afterAnchorText(lines.get(i).text(), own))
                        : "";
                position = where;
                nthFound = nth;
            } else {
                String above = labelAbove(lines, i, used, where);
                if (above != null) {
                    anchor = above;
                    direction = AnchorRule.Direction.LINE_BELOW;
                    currency = bindCurrency ? AnchorRule.currencyOf(lines.get(i).text()) : "";
                    position = where;
                    nthFound = nth;
                }
            }
        }
        // Mehrere Fundstellen: die unterste gewinnt (die Schleife überschreibt) – in einer Abrechnung
        // steht die Endsumme unten, und sie ist die Zahl, die auch mit Gebühren noch stimmt.
        return anchor == null ? null
                : AnchorRule.single(anchor, direction, currency, position, nthFound);
    }

    /**
     * Die Spaltenüberschrift über einer Wertzeile — {@code null}, wenn keine taugt.
     *
     * <p>Gesucht wird bis zu {@link AnchorRule#MAX_BELOW} Zeilen nach oben, denn zwischen Überschrift und
     * Daten steht oft noch eine zweite Kopfzeile: Scalable Capital schreibt „Buchung Wertstellung Typ …
     * Gesamt", darunter „Wechselkurs" und erst dann die Zahlen. Eine Zeile weit zu schauen fände dort
     * nur „Wechselkurs" — und das ist die Überschrift einer anderen Spalte.</p>
     *
     * <p>Die gelernte Regel merkt sich den Abstand <b>nicht</b>: sie sucht beim Lesen die nächste Zeile
     * mit einem Wert. Fällt beim nächsten Beleg eine Zwischenzeile weg (kein Wechselkurs bei einem
     * Euro-Papier), stimmt sie weiterhin.</p>
     */
    private static String labelAbove(List<PdfText.Line> lines, int i, List<String> used,
                                     AnchorRule.Position where) {
        for (int j = i - 1; j >= 0 && j >= i - AnchorRule.MAX_BELOW; j--) {
            String label = labelOf(lines.get(j).text());
            if (isUsable(label, used, where)) {
                return label;
            }
        }
        return null;
    }

    /**
     * Wo der Wert in der Zeile steht: {@code {0 = von rechts | 1 = von links, Stelle ab 1}}, oder
     * {@code null}, wenn er dort nicht vorkommt.
     *
     * <p>Gesucht wird von aussen nach innen und höchstens bis zur dritten Stelle. Weiter zu zählen wäre
     * gefährlich: je tiefer, desto grösser die Aussicht, dass irgendeine Zahl der Zeile zufällig passt
     * und eine Regel entsteht, die beim nächsten Beleg etwas anderes meint.</p>
     */
    private static int[] stelleIn(String line, double value, double epsilon) {
        for (int nth = 1; nth <= MAX_STELLE; nth++) {
            for (int vonLinks = 0; vonLinks <= 1; vonLinks++) {
                AnchorRule.Position where = vonLinks == 1
                        ? AnchorRule.Position.FIRST : AnchorRule.Position.LAST;
                Double found = AnchorRule.numberAt(line, where, nth);
                if (found != null && Math.abs(found - value) <= epsilon) {
                    return new int[]{vonLinks, nth};
                }
            }
        }
        return null;
    }

    /** Der Wert ist die Summe der letzten Zahlen mehrerer Zeilen (aufgeteilte Steuer). */
    private static AnchorRule summedLines(PdfText text, double value, double epsilon,
                                          boolean bindCurrency, List<String> used) {
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        List<String> currencies = new ArrayList<>();
        for (PdfText.Line line : text.lines()) {
            Double last = AnchorRule.lastNumber(line.text());
            String label = labelOf(line.text());
            if (last != null && last != 0 && isUsable(label, used, AnchorRule.Position.LAST)
                    && !labels.contains(label)) {
                labels.add(label);
                values.add(last);
                currencies.add(bindCurrency ? AnchorRule.currencyOf(line.text()) : "");
            }
        }
        List<Integer> pick = subsetSummingTo(values, value, epsilon, MAX_SUMMANDS);
        if (pick == null) {
            return null;
        }
        List<String> anchors = new ArrayList<>();
        String currency = null;
        for (int idx : pick) {
            anchors.add(labels.get(idx));
            // Nur eine einheitliche Währung wird gelernt; stehen die Summanden in verschiedenen, wäre
            // die Summe ohnehin fragwürdig, und ohne Kennzeichen bleibt es beim bisherigen Verhalten.
            if (currency == null) {
                currency = currencies.get(idx);
            } else if (!currency.equals(currencies.get(idx))) {
                currency = "";
            }
        }
        return AnchorRule.summed(anchors, AnchorRule.Direction.SAME_LINE,
                currency == null ? "" : currency);
    }

    /** Sucht bis zu {@code max} Zeilen, deren Werte zusammen {@code target} ergeben; null wenn keine. */
    private static List<Integer> subsetSummingTo(List<Double> values, double target, double epsilon,
                                                 int max) {
        int n = values.size();
        for (int size = 2; size <= max; size++) {
            List<Integer> found = search(values, target, epsilon, size, 0, new ArrayList<Integer>(), n);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Integer> search(List<Double> values, double remaining, double epsilon, int left,
                                        int from, List<Integer> chosen, int n) {
        if (left == 0) {
            return Math.abs(remaining) <= epsilon ? new ArrayList<>(chosen) : null;
        }
        for (int i = from; i <= n - left; i++) {
            chosen.add(i);
            List<Integer> found = search(values, remaining - values.get(i), epsilon, left - 1, i + 1,
                    chosen, n);
            if (found != null) {
                return found;
            }
            chosen.remove(chosen.size() - 1);
        }
        return null;
    }

    /** Das gebuchte Datum: die Zeile suchen, in der genau dieses Datum steht. */
    private static AnchorRule forDate(PdfText text, long dateMillis, String chosen, List<String> used) {
        if (dateMillis <= 0) {
            return null;
        }
        String anchor = null;
        AnchorRule.Direction direction = AnchorRule.Direction.SAME_LINE;
        AnchorRule.Position position = AnchorRule.Position.LAST;
        int nthFound = 1;
        List<PdfText.Line> lines = text.lines();
        for (int i = 0; i < lines.size(); i++) {
            // Irgendwo in der Zeile, nicht nur als erste Angabe: eine Zeile trägt oft mehrere Daten
            // („Schlusstag/-Zeit 25.11.2015 11:02:54 Zinstermin Monat(e) 27. Juni"), und gebucht gehört
            // nicht zwangsläufig das erste.
            int[] stelle = datumsStelle(lines.get(i).text(), dateMillis);
            if (stelle == null) {
                continue;
            }
            AnchorRule.Position where = stelle[0] == 1
                    ? AnchorRule.Position.FIRST : AnchorRule.Position.LAST;
            String label = firstUsable(used, where,
                    labelBeforeDate(lines.get(i).text(), dateMillis), labelOf(lines.get(i).text()));
            if (label != null) {
                if (chosen != null && chosen.equalsIgnoreCase(label)) {
                    // Seine Wahl gewinnt – und mit ihr die Stelle, an der das Datum dort steht.
                    return AnchorRule.single(label, AnchorRule.Direction.SAME_LINE, "",
                            where, stelle[1]);
                }
                anchor = label;
                direction = AnchorRule.Direction.SAME_LINE;
                position = where;
                nthFound = stelle[1];
                continue;
            }
            // Keine eigene Beschriftung – dann trägt sie die Spaltenüberschrift darüber. So steht das
            // Datum bei Scalable Capital: die Zeile beginnt mit ihm und hat gar keine.
            String above = labelAbove(lines, i, used, where);
            if (above != null) {
                anchor = above;
                direction = AnchorRule.Direction.LINE_BELOW;
                position = where;
                nthFound = stelle[1];
            }
        }
        return anchor == null ? null
                : AnchorRule.single(anchor, direction, "", position, nthFound);
    }

    /**
     * Das wievielte Datum der Zeile das gesuchte ist: {@code {0 = von rechts | 1 = von links, Stelle}}.
     *
     * <p>Gezählt wird <b>immer von links</b>. Anders als bei den Zahlen ist das keine Geschmacksfrage:
     * die Vorgabe „von rechts, erste" bedeutet beim Datum aus Rücksicht auf den Bestand etwas anderes
     * ({@link AnchorRule#dateAt}), und eine gelernte Regel soll nicht in diese Zweideutigkeit geraten.
     * „30.06.2026 01.07.2026 Gutschrift …" ergibt für die Wertstellung also die zweite von links.</p>
     */
    private static int[] datumsStelle(String line, long dateMillis) {
        List<Long> alle = AnchorRule.allDates(line);
        int vonLinks = alle.indexOf(dateMillis);
        return vonLinks < 0 ? null : new int[]{1, vonLinks + 1};
    }

    /** Ob dieses Datum irgendwo in der Zeile steht. */
    private static boolean hasDate(String line, long dateMillis) {
        for (String token : line.trim().split("\\s+")) {
            if (TextValues.toUnambiguousDateMillis(token) == dateMillis) {
                return true;
            }
        }
        return false;
    }

    /** Die erste brauchbare unter mehreren Beschriftungen; {@code null}, wenn keine taugt. */
    private static String firstUsable(List<String> used, AnchorRule.Position where,
                                      String... candidates) {
        for (String candidate : candidates) {
            if (isUsable(candidate, used, where)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Die Beschriftung <b>unmittelbar vor</b> dem Wert: die Wörter davor, zurück bis zur nächsten Zahl.
     *
     * <p>Der Unterschied zu {@link #labelOf} zeigt sich erst, wenn vor dem Wert schon eine Zahl steht:
     * „Beispielstraße 1 DATUM 13.05.2019" — die Beschriftung ist „DATUM", nicht „Beispielstraße". Steht
     * keine Zahl davor, kommt dasselbe heraus wie bisher, und die gelernten Vorlagen sehen aus wie
     * gewohnt.</p>
     */
    static String labelBefore(String line, AnchorRule.Position where) {
        if (line == null) {
            return "";
        }
        String[] tokens = line.trim().split("\\s+");
        int value = numberIndex(tokens, where);
        return value <= 0 ? labelOf(line) : wordsBefore(tokens, value);
    }

    /** Dasselbe für ein Datum: die Wörter vor der Datumsangabe. */
    static String labelBeforeDate(String line, long dateMillis) {
        if (line == null) {
            return "";
        }
        String[] tokens = line.trim().split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            if (TextValues.toUnambiguousDateMillis(tokens[i]) == dateMillis) {
                return i == 0 ? "" : wordsBefore(tokens, i);
            }
        }
        return labelOf(line);
    }

    /** Index der ersten bzw. letzten Zahl unter den Wörtern; -1, wenn keine dabei ist. */
    private static int numberIndex(String[] tokens, AnchorRule.Position where) {
        int found = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (TextValues.toDecimal(tokens[i]) == null) {
                continue;
            }
            if (where == AnchorRule.Position.FIRST) {
                return i;
            }
            found = i;
        }
        return found;
    }

    /** Die Wörter vor {@code index}, zurück bis zum nächsten mit einer Ziffer; ohne Währungskürzel. */
    private static String wordsBefore(String[] tokens, int index) {
        List<String> words = new ArrayList<>();
        for (int i = index - 1; i >= 0; i--) {
            if (hasDigit(tokens[i])) {
                break;
            }
            words.add(0, tokens[i]);
        }
        while (!words.isEmpty() && isCurrencyCode(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        StringBuilder label = new StringBuilder();
        for (String w : words) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(w);
        }
        return label.toString();
    }

    /**
     * Die Beschriftung einer Zeile: alle Wörter bis zum ersten, das eine Ziffer enthält.
     *
     * <p>Nicht „bis zur ersten Zahl": „Kapitalertragsteuer 25,00% EUR 158,73" — der Steuersatz ist keine
     * lesbare Zahl (Prozentzeichen), stünde aber im Anker und änderte sich mit dem Satz. Ein
     * abschließendes Währungskürzel fällt ebenfalls weg, damit derselbe Anker auch greift, wenn eine
     * Abrechnung einmal in einer anderen Währung kommt.</p>
     */
    static String labelOf(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder label = new StringBuilder();
        List<String> words = new ArrayList<>();
        for (String token : line.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (hasDigit(token)) {
                break;
            }
            words.add(token);
        }
        while (!words.isEmpty() && isCurrencyCode(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        for (String w : words) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(w);
        }
        return label.toString();
    }

    private static boolean hasDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCurrencyCode(String s) {
        return s.length() == 3 && s.equals(s.toUpperCase(Locale.ROOT))
                && s.matches("[A-Z]{3}");
    }

    /**
     * Ob eine Beschriftung als Anker taugt: nicht leer, nicht zu kurz (ein einzelner Buchstabe träfe zu
     * viel) und noch nicht für ein anderes Feld vergeben — sonst läsen zwei Felder dieselbe Zahl.
     */
    private static boolean isUsable(String label, List<String> used, AnchorRule.Position position) {
        if (label == null || label.trim().length() < 3 || used.contains(taken(label, position))) {
            return false;
        }
        // Ein bloßes Währungskürzel taugt nicht: „EUR 2.000,00 8,75 % METALCORP …" führt die Zeile mit
        // „EUR" an, und darauf schlüge die Regel bei jeder anderen Zeile derselben Abrechnung auch an.
        return !isCurrencyCode(label.trim());
    }
}
