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

    /** Zahlen gelten als gleich, wenn sie sich um weniger als das unterscheiden (Rundung im PDF). */
    private static final double EPSILON = 0.0000005;

    /** Höchstens so viele Zeilen werden zu einer Summe zusammengesucht (Steuer + Soli + Kirchensteuer). */
    private static final int MAX_SUMMANDS = 3;

    private TemplateLearner() {
    }

    /** Die Werte, die der Nutzer für dieses Dokument eingetragen hat. Nicht Gesetztes ist {@code null}. */
    public static final class Known {
        public String action;
        public Double shares;
        public Double price;
        public Long feeCents;
        public Long netCents;
        public long dateMillis = -1;
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
        put(rules, StatementTemplate.Field.NET, forValue(text, cents(known.netCents), used), used);
        put(rules, StatementTemplate.Field.FEE, forValue(text, cents(known.feeCents), used), used);
        put(rules, StatementTemplate.Field.PRICE, forValue(text, known.price, used), used);
        put(rules, StatementTemplate.Field.SHARES, forValue(text, known.shares, used), used);
        put(rules, StatementTemplate.Field.DATE, forDate(text, known.dateMillis, used), used);
        return new StatementTemplate(known.action, rules);
    }

    private static Double cents(Long value) {
        return value == null ? null : value / 100.0;
    }

    private static void put(Map<StatementTemplate.Field, AnchorRule> rules,
                            StatementTemplate.Field field, AnchorRule rule, List<String> used) {
        if (rule != null) {
            rules.put(field, rule);
            used.addAll(rule.anchors);
        }
    }

    /**
     * Sucht eine Regel für {@code value}. Zuerst in einer einzelnen Zeile; findet sich der Wert dort
     * nicht, wird er als <b>Summe</b> mehrerer Zeilen gesucht — so steht die Steuer bei der ING, auf
     * Kapitalertragsteuer und Solidaritätszuschlag verteilt.
     */
    private static AnchorRule forValue(PdfText text, Double value, List<String> used) {
        if (value == null) {
            return null;
        }
        AnchorRule single = singleLine(text, value, used);
        if (single != null) {
            return single;
        }
        return summedLines(text, value, used);
    }

    /** Der Wert steht als letzte Zahl einer Zeile — der Regelfall. */
    private static AnchorRule singleLine(PdfText text, double value, List<String> used) {
        String anchor = null;
        AnchorRule.Direction direction = AnchorRule.Direction.SAME_LINE;
        List<PdfText.Line> lines = text.lines();
        for (int i = 0; i < lines.size(); i++) {
            Double last = AnchorRule.lastNumber(lines.get(i).text());
            if (last == null || Math.abs(last - value) > EPSILON) {
                continue;
            }
            // Beschriftung in derselben Zeile; hat die Zeile keine, steht sie eine Zeile darüber.
            String own = labelOf(lines.get(i).text());
            if (isUsable(own, used)) {
                anchor = own;
                direction = AnchorRule.Direction.SAME_LINE;
            } else if (i > 0) {
                String above = labelOf(lines.get(i - 1).text());
                if (isUsable(above, used)) {
                    anchor = above;
                    direction = AnchorRule.Direction.LINE_BELOW;
                }
            }
        }
        // Mehrere Fundstellen: die unterste gewinnt (die Schleife überschreibt) – in einer Abrechnung
        // steht die Endsumme unten, und sie ist die Zahl, die auch mit Gebühren noch stimmt.
        return anchor == null ? null : AnchorRule.single(anchor, direction);
    }

    /** Der Wert ist die Summe der letzten Zahlen mehrerer Zeilen (aufgeteilte Steuer). */
    private static AnchorRule summedLines(PdfText text, double value, List<String> used) {
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (PdfText.Line line : text.lines()) {
            Double last = AnchorRule.lastNumber(line.text());
            String label = labelOf(line.text());
            if (last != null && last != 0 && isUsable(label, used) && !labels.contains(label)) {
                labels.add(label);
                values.add(last);
            }
        }
        List<Integer> pick = subsetSummingTo(values, value, MAX_SUMMANDS);
        if (pick == null) {
            return null;
        }
        List<String> anchors = new ArrayList<>();
        for (int idx : pick) {
            anchors.add(labels.get(idx));
        }
        return AnchorRule.summed(anchors, AnchorRule.Direction.SAME_LINE);
    }

    /** Sucht bis zu {@code max} Zeilen, deren Werte zusammen {@code target} ergeben; null wenn keine. */
    private static List<Integer> subsetSummingTo(List<Double> values, double target, int max) {
        int n = values.size();
        for (int size = 2; size <= max; size++) {
            List<Integer> found = search(values, target, size, 0, new ArrayList<Integer>(), n);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Integer> search(List<Double> values, double remaining, int left, int from,
                                        List<Integer> chosen, int n) {
        if (left == 0) {
            return Math.abs(remaining) <= EPSILON ? new ArrayList<>(chosen) : null;
        }
        for (int i = from; i <= n - left; i++) {
            chosen.add(i);
            List<Integer> found = search(values, remaining - values.get(i), left - 1, i + 1, chosen, n);
            if (found != null) {
                return found;
            }
            chosen.remove(chosen.size() - 1);
        }
        return null;
    }

    /** Das gebuchte Datum: die Zeile suchen, in der genau dieses Datum steht. */
    private static AnchorRule forDate(PdfText text, long dateMillis, List<String> used) {
        if (dateMillis <= 0) {
            return null;
        }
        String anchor = null;
        for (PdfText.Line line : text.lines()) {
            if (AnchorRule.firstDate(line.text()) != dateMillis) {
                continue;
            }
            String label = labelOf(line.text());
            if (isUsable(label, used)) {
                anchor = label;
            }
        }
        return anchor == null ? null : AnchorRule.single(anchor, AnchorRule.Direction.SAME_LINE);
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
    private static boolean isUsable(String label, List<String> used) {
        return label != null && label.trim().length() >= 3 && !used.contains(label);
    }
}
