package de.spahr.ausgaben.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.util.TextValues;

/**
 * Wo in einer Abrechnung ein bestimmter Wert steht — beschrieben über die <b>Beschriftung</b> daneben,
 * nicht über eine Position auf der Seite.
 *
 * <p>Der Grund: Abrechnungen verschieben sich senkrecht. Der Wertpapiername bricht mal auf zwei Zeilen
 * um, „Stückzinsen" oder „Devisenkurs" erscheinen nur manchmal, bei Sparplänen rutscht der Block eine
 * Seite weiter. Eine gemerkte Koordinate zeigt dann ins Leere — und zwar still, sie liefert eine falsche
 * Zahl statt keiner. Die Beschriftung bleibt.</p>
 *
 * <p>Regeln entstehen nicht von Hand, sondern werden von {@link TemplateLearner} aus einer einmal selbst
 * erfassten Abrechnung abgeleitet. Im Code steht deshalb nichts über eine bestimmte Bank.</p>
 */
public final class AnchorRule {

    /** Wo der Wert relativ zur Beschriftung steht. Welche Richtung gilt, ermittelt der Lerner. */
    public enum Direction {
        /** In derselben Zeile wie die Beschriftung — der Regelfall. */
        SAME_LINE,
        /** In der Zeile darunter; manche Banken setzen den Wert unter die Überschrift. */
        LINE_BELOW
    }

    /** Beschriftungen, an denen die Regel anschlägt; eine genügt (mehrere nur beim Summieren). */
    public final List<String> anchors;
    public final Direction direction;
    /**
     * Ob über <b>alle</b> passenden Zeilen summiert wird. Nötig, weil Banken die Steuer aufteilen:
     * Kapitalertragsteuer und Solidaritätszuschlag stehen auf zwei Zeilen, die Kirchensteuer käme als
     * dritte dazu.
     */
    public final boolean sum;

    public AnchorRule(List<String> anchors, Direction direction, boolean sum) {
        List<String> copy = new ArrayList<>();
        for (String a : anchors) {
            if (a != null && !a.trim().isEmpty()) {
                copy.add(a.trim());
            }
        }
        this.anchors = copy;
        this.direction = direction;
        this.sum = sum;
    }

    public static AnchorRule single(String anchor, Direction direction) {
        return new AnchorRule(java.util.Collections.singletonList(anchor), direction, false);
    }

    public static AnchorRule summed(List<String> anchors, Direction direction) {
        return new AnchorRule(anchors, direction, true);
    }

    /**
     * Der Wert dieser Regel, roh als Dezimalzahl; {@code null}, wenn keine Beschriftung anschlägt.
     *
     * <p>Passt eine Beschriftung auf mehrere Zeilen und wird nicht summiert, gilt die <b>unterste</b>.
     * In einer Abrechnung steht die Endsumme unten, und sie ist die Zahl, die auch mit Gebühren noch
     * stimmt — dieselbe Wahl trifft der Lerner.</p>
     */
    public Double read(PdfText text) {
        if (text == null || anchors.isEmpty()) {
            return null;
        }
        List<PdfText.Line> lines = text.lines();
        double total = 0;
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (!matches(lines.get(i).text())) {
                continue;
            }
            int valueLine = direction == Direction.LINE_BELOW ? i + 1 : i;
            if (valueLine >= lines.size()) {
                continue;
            }
            Double value = lastNumber(lines.get(valueLine).text());
            if (value == null) {
                continue;
            }
            if (sum) {
                total += value;
            } else {
                total = value;   // weiter suchen: die unterste Fundstelle gewinnt
            }
            found = true;
        }
        return found ? total : null;
    }

    /** Der Wert in Cent — für die Geldfelder. */
    public Long readCents(PdfText text) {
        Double value = read(text);
        return value == null ? null : Math.round(value * 100.0);
    }

    /**
     * Das Datum der angeankerten Zeile; -1, wenn keines darin steht oder es mehrdeutig ist.
     *
     * <p>Eine Abrechnung trägt mehrere Datumsangaben — Briefdatum, Ausführungstag, Valuta, Ex-Tag,
     * Zahltag. Welches gebucht gehört, ist deshalb eine Ankerfrage wie jede andere und kein „erstes
     * Datum im Dokument".</p>
     */
    public long readDate(PdfText text) {
        if (text == null || anchors.isEmpty()) {
            return -1;
        }
        List<PdfText.Line> lines = text.lines();
        long result = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!matches(lines.get(i).text())) {
                continue;
            }
            int valueLine = direction == Direction.LINE_BELOW ? i + 1 : i;
            if (valueLine >= lines.size()) {
                continue;
            }
            long millis = firstDate(lines.get(valueLine).text());
            if (millis > 0) {
                result = millis;   // unterste Fundstelle gewinnt, wie bei den Zahlen
            }
        }
        return result;
    }

    /** Das erste eindeutige Datum einer Zeile; -1, wenn keines darin steht. */
    static long firstDate(String line) {
        if (line == null) {
            return -1;
        }
        for (String token : line.split("\\s+")) {
            long millis = TextValues.toUnambiguousDateMillis(token);
            if (millis > 0) {
                return millis;
            }
        }
        return -1;
    }

    /** Ob die Zeile mit einer der Beschriftungen beginnt (am Wortende, nicht mitten im Wort). */
    private boolean matches(String line) {
        String l = line.toLowerCase(Locale.ROOT);
        for (String a : anchors) {
            String needle = a.toLowerCase(Locale.ROOT);
            if (!l.startsWith(needle)) {
                continue;
            }
            // „Kurs" darf nicht auf „Kurswert" anschlagen – sonst läse ein Kauf den Kurswert als Kurs.
            if (l.length() == needle.length() || l.charAt(needle.length()) == ' ') {
                return true;
            }
        }
        return false;
    }

    /** Die letzte Zahl einer Zeile; {@code null}, wenn keine darin steht. */
    static Double lastNumber(String line) {
        List<String> tokens = TextValues.numberTokens(line);
        return tokens.isEmpty() ? null : TextValues.toDecimal(tokens.get(tokens.size() - 1));
    }

    /**
     * Zwei Regeln sind gleich, wenn sie dieselben Beschriftungen in derselben Richtung lesen. Gebraucht,
     * um zu erkennen, ob ein Lernvorgang überhaupt etwas Neues ergeben hat.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnchorRule)) {
            return false;
        }
        AnchorRule other = (AnchorRule) o;
        return sum == other.sum && direction == other.direction && anchors.equals(other.anchors);
    }

    @Override
    public int hashCode() {
        return anchors.hashCode() * 31 + direction.hashCode() + (sum ? 1 : 0);
    }

    @Override
    public String toString() {
        return (sum ? "Summe von " : "") + anchors + " (" + direction + ")";
    }
}
