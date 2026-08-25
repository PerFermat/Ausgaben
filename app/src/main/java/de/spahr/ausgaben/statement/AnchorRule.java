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

    /**
     * Welche Zahl der Zeile gemeint ist.
     *
     * <p>Der Regelfall ist die letzte: in einer Abrechnung steht rechts der Betrag. Bei Tabellen mit
     * Spaltenüberschrift ist es die erste — „Nominale Wertpapierbezeichnung ISIN (WKN)" und darunter
     * „EUR 2.000,00 8,75 % METALCORP GROUP B.V. DE000A1HLTD2"; dort führt die Stückzahl die Zeile an und
     * rechts stehen Kurs und Kennnummer. An 2354 fremden Abrechnungen gemessen kommt die Stückzahl in
     * 876 Fällen <b>nur</b> so vor.</p>
     */
    public enum Position {
        /** Die letzte Zahl der Zeile — der Regelfall. */
        LAST,
        /** Die erste Zahl der Zeile — Tabellenspalte unter einer Überschrift. */
        FIRST
    }

    /** Wo der Wert relativ zur Beschriftung steht. Welche Richtung gilt, ermittelt der Lerner. */
    public enum Direction {
        /** In derselben Zeile wie die Beschriftung — der Regelfall. */
        SAME_LINE,
        /** In der Zeile darunter; manche Banken setzen den Wert unter die Überschrift. */
        LINE_BELOW
    }

    /**
     * Die Beschriftungen, an denen die Regel anschlägt — <b>der Reihe nach</b>: es gilt die erste, die im
     * Dokument eine Zeile mit Wert anführt.
     *
     * <p>Damit lassen sich Rückfälle angeben, und die braucht es: eine Bank druckt die Valuta-Zeile nicht,
     * wenn sie mit dem Zahltag zusammenfällt — {@code [Valuta, Zahltag, Ex-Tag]} liest dann beide Formen
     * derselben Abrechnung. Ebenso beim Brutto, das bei einem dollarnotierten Papier in der
     * Umrechnungszeile steht und bei einem euronotierten in der Bruttozeile.</p>
     *
     * <p>Beim Summieren ({@link #sum}) gilt das nicht: dort tragen <b>alle</b> Beschriftungen zusammen
     * das Ergebnis, weil Banken die Steuer auf mehrere Zeilen verteilen.</p>
     */
    public final List<String> anchors;
    public final Direction direction;
    /**
     * Ob über <b>alle</b> passenden Zeilen summiert wird. Nötig, weil Banken die Steuer aufteilen:
     * Kapitalertragsteuer und Solidaritätszuschlag stehen auf zwei Zeilen, die Kirchensteuer käme als
     * dritte dazu.
     */
    public final boolean sum;
    /**
     * Das Währungskennzeichen der Zeile, in der der Wert steht — leer, wenn die Zeile keines trägt
     * (Stückzahlen etwa haben keines).
     *
     * <p>Nötig, weil dieselbe Abrechnung Beträge in mehreren Währungen führt: die Ertragsgutschrift nennt
     * „Brutto USD 1.053,47" und rechnet zwei Zeilen tiefer auf „EUR 906,99" um. Ohne diese Prüfung läse
     * eine Regel den Dollarbetrag als Euro — still, und um ein Sechstel daneben.</p>
     */
    public final String currency;
    /** Von welchem Ende der Zeile gezählt wird; der Lerner ermittelt es (siehe {@link Position}). */
    public final Position position;
    /**
     * Die wievielte Zahl von diesem Ende gemeint ist, ab 1.
     *
     * <p>Gebraucht für Tabellenzeilen: „YOU BOUGHT XBI 78464A870 06/29/22 07/01/22 MARGIN 5 $74.33000"
     * nennt den Kurs als letzte und die Menge als <b>zweitletzte</b> Zahl. Ohne das wäre die Menge nicht
     * auszudrücken.</p>
     */
    public final int nth;

    public AnchorRule(List<String> anchors, Direction direction, boolean sum) {
        this(anchors, direction, sum, "");
    }

    public AnchorRule(List<String> anchors, Direction direction, boolean sum, String currency) {
        this(anchors, direction, sum, currency, Position.LAST);
    }

    public AnchorRule(List<String> anchors, Direction direction, boolean sum, String currency,
                      Position position) {
        this(anchors, direction, sum, currency, position, 1);
    }

    public AnchorRule(List<String> anchors, Direction direction, boolean sum, String currency,
                      Position position, int nth) {
        List<String> copy = new ArrayList<>();
        for (String a : anchors) {
            if (a != null && !a.trim().isEmpty()) {
                copy.add(a.trim());
            }
        }
        this.anchors = copy;
        this.direction = direction;
        this.sum = sum;
        this.currency = currency == null ? "" : currency.trim();
        this.position = position == null ? Position.LAST : position;
        this.nth = Math.max(1, nth);
    }

    public static AnchorRule single(String anchor, Direction direction) {
        return single(anchor, direction, "");
    }

    public static AnchorRule single(String anchor, Direction direction, String currency) {
        return single(anchor, direction, currency, Position.LAST);
    }

    public static AnchorRule single(String anchor, Direction direction, String currency,
                                    Position position) {
        return single(anchor, direction, currency, position, 1);
    }

    public static AnchorRule single(String anchor, Direction direction, String currency,
                                    Position position, int nth) {
        return new AnchorRule(java.util.Collections.singletonList(anchor), direction, false, currency,
                position, nth);
    }

    public static AnchorRule summed(List<String> anchors, Direction direction, String currency) {
        return new AnchorRule(anchors, direction, true, currency);
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
        if (sum) {
            // Alle Beschriftungen zusammen: Kapitalertragsteuer und Solidaritätszuschlag ergeben die
            // Steuer erst gemeinsam. Fehlt eine der Zeilen, zählt eben nur die vorhandene.
            double total = 0;
            boolean found = false;
            for (String anchor : anchors) {
                Double part = valueOf(text, anchor);
                if (part != null) {
                    total += part;
                    found = true;
                }
            }
            return found ? total : null;
        }
        for (String anchor : anchors) {
            Double value = valueOf(text, anchor);
            if (value != null) {
                return value;   // die erste Beschriftung, die trägt
            }
        }
        return null;
    }

    /** Der Wert zu <b>einer</b> Beschriftung; passt sie auf mehrere Zeilen, gilt die unterste. */
    private Double valueOf(PdfText text, String anchor) {
        List<PdfText.Line> lines = text.lines();
        Double result = null;
        for (int i = 0; i < lines.size(); i++) {
            String valueText = valueText(lines, i, anchor);
            if (valueText == null || !currencyFits(valueText)) {
                continue;   // andere Währung als gelernt – dann ist es nicht der gesuchte Betrag
            }
            Double value = numberAt(valueText, position, nth);
            if (value != null) {
                result = value;   // weiter suchen: die unterste Fundstelle gewinnt
            }
        }
        return result;
    }

    /**
     * Der Teil der Zeile, in dem der Wert zu suchen ist — {@code null}, wenn die Beschriftung hier nicht
     * anschlägt.
     *
     * <p>Steht die Beschriftung <b>mitten</b> in der Zeile, gilt nur, was dahinter kommt. Nötig, weil
     * nicht jede Bank die Beschriftung an den Anfang setzt: Trade Republic schreibt
     * „Beispielstraße 1 DATUM 13.05.2019", und ohne diese Einschränkung läse eine Regel für „DATUM" die
     * Hausnummer mit. Führt die Beschriftung die Zeile an — der Regelfall —, ist es wie bisher der
     * ganze Rest.</p>
     */
    private String valueText(List<PdfText.Line> lines, int i, String anchor) {
        int after = afterAnchor(lines.get(i).text(), anchor);
        if (after < 0) {
            return null;
        }
        if (direction == Direction.LINE_BELOW) {
            return i + 1 < lines.size() ? lines.get(i + 1).text() : null;
        }
        return lines.get(i).text().substring(after);
    }

    /**
     * Welche Beschriftung den Wert getragen hat; {@code null}, wenn keine trägt. Für die Probe auf der
     * Regelseite — beim Eintippen einer Kette will man sehen, welches Glied gegriffen hat. Beim Summieren
     * sind es alle, die etwas beigetragen haben.
     */
    public String matchedAnchor(PdfText text) {
        if (text == null) {
            return null;
        }
        StringBuilder summed = new StringBuilder();
        for (String anchor : anchors) {
            if (valueOf(text, anchor) == null && dateOf(text, anchor) <= 0) {
                continue;
            }
            if (!sum) {
                return anchor;
            }
            if (summed.length() > 0) {
                summed.append(" + ");
            }
            summed.append(anchor);
        }
        return summed.length() == 0 ? null : summed.toString();
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
        for (String anchor : anchors) {
            long millis = dateOf(text, anchor);
            if (millis > 0) {
                return millis;   // die erste Beschriftung, die trägt
            }
        }
        return -1;
    }

    /** Das Datum zu <b>einer</b> Beschriftung; passt sie auf mehrere Zeilen, gilt die unterste. */
    private long dateOf(PdfText text, String anchor) {
        List<PdfText.Line> lines = text.lines();
        long result = -1;
        for (int i = 0; i < lines.size(); i++) {
            String valueText = valueText(lines, i, anchor);
            if (valueText == null) {
                continue;
            }
            long millis = firstDate(valueText);
            if (millis > 0) {
                result = millis;   // unterste Fundstelle gewinnt, wie bei den Zahlen
            }
        }
        return result;
    }

    /**
     * Das erste eindeutige Datum einer Zeile; -1, wenn keines darin steht.
     *
     * <p>Auch über mehrere Wörter: englischsprachige Belege schreiben „30 Aug 2023" oder „Dec 5, 2019",
     * und das sind drei Wörter, kein eines. Geprüft werden deshalb ein, zwei und drei aufeinander
     * folgende — das längste zuerst, sonst gewönne bei „5 Dec 2019" die bloße 5.</p>
     */
    static long firstDate(String line) {
        if (line == null) {
            return -1;
        }
        String[] tokens = line.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            for (int len = Math.min(3, tokens.length - i); len >= 1; len--) {
                StringBuilder joined = new StringBuilder();
                for (int k = 0; k < len; k++) {
                    if (k > 0) {
                        joined.append(' ');
                    }
                    joined.append(tokens[i + k]);
                }
                // Das Komma in „Dec 5, 2019" gehört zur Schreibweise, nicht zum Datum.
                long millis = TextValues.toUnambiguousDateMillis(
                        joined.toString().replace(",", ""));
                if (millis > 0) {
                    return millis;
                }
            }
        }
        return -1;
    }

    /** Ob die Zeile mit genau dieser Beschriftung beginnt. */
    private static boolean startsWithAnchor(String line, String anchor) {
        return afterAnchor(line, anchor) >= 0;
    }

    /**
     * Der Teil der Zeile hinter der Beschriftung, oder {@code null}. Für den Lerner: die Währung muss aus
     * demselben Text stammen, der beim Lesen geprüft wird, sonst lernt er eine, die nie wieder passt.
     */
    static String afterAnchorText(String line, String anchor) {
        int at = afterAnchor(line, anchor);
        return at < 0 ? null : line.substring(at);
    }

    /**
     * Die Stelle unmittelbar hinter der Beschriftung, oder -1. Gesucht wird an <b>Wortgrenzen</b>:
     * „Kurs" darf nicht auf „Kurswert" anschlagen, sonst läse ein Kauf den Kurswert als Kurs.
     */
    private static int afterAnchor(String line, String anchor) {
        String l = line.toLowerCase(Locale.ROOT);
        String needle = anchor.toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return -1;
        }
        int at = l.indexOf(needle);
        while (at >= 0) {
            boolean linksFrei = at == 0 || l.charAt(at - 1) == ' ';
            int end = at + needle.length();
            boolean rechtsFrei = end == l.length() || l.charAt(end) == ' ';
            if (linksFrei && rechtsFrei) {
                return end;
            }
            at = l.indexOf(needle, at + 1);
        }
        return -1;
    }

    /**
     * Wie viele der Beschriftungen dieser Regel im Dokument tatsächlich eine Zeile anführen.
     *
     * <p>Damit lässt sich messen, wie gut eine Vorlage zu einem Dokument passt, <b>ohne</b> Vollständigkeit
     * zu verlangen: eine Abrechnung, in der eine Zeile fehlt, ist immer noch dieselbe Abrechnung. Gezählt
     * wird nach demselben Kriterium, nach dem später gelesen wird — sonst könnte eine Vorlage „passen",
     * ohne dass eine einzige Regel etwas fände.</p>
     */
    public int hits(PdfText text) {
        if (text == null) {
            return 0;
        }
        int found = 0;
        for (String anchor : anchors) {
            for (PdfText.Line line : text.lines()) {
                if (startsWithAnchor(line.text(), anchor)) {
                    found++;
                    break;
                }
            }
        }
        return found;
    }

    /** Ob die Zeile das gelernte Währungskennzeichen trägt (ohne gelerntes: immer). */
    private boolean currencyFits(String line) {
        return currency.isEmpty() || currency.equals(currencyOf(line));
    }

    /**
     * Das Währungskennzeichen einer Zeile: ein alleinstehendes Wort aus drei Großbuchstaben oder ein
     * Währungssymbol. {@code ""}, wenn keines darin steht.
     */
    static String currencyOf(String line) {
        if (line == null) {
            return "";
        }
        for (String token : line.split("\\s+")) {
            if (token.equals("€") || token.equals("$")) {
                return token;
            }
            if (token.length() == 3 && token.matches("[A-Z]{3}")) {
                return token;
            }
        }
        return "";
    }

    /** Die {@code nth}-te Zahl vom vorderen oder hinteren Ende der Zeile. */
    static Double numberAt(String line, Position position, int nth) {
        List<String> tokens = TextValues.numberTokens(line);
        int index = position == Position.FIRST ? nth - 1 : tokens.size() - nth;
        if (index < 0 || index >= tokens.size()) {
            return null;
        }
        Double value = TextValues.toDecimal(tokens.get(index));
        return value == null ? null : Math.abs(value);
    }

    /** Die erste Zahl einer Zeile, ohne Vorzeichen; {@code null}, wenn keine darin steht. */
    static Double firstNumber(String line) {
        return numberAt(line, Position.FIRST, 1);
    }

    /**
     * Die letzte Zahl einer Zeile, <b>ohne Vorzeichen</b>; {@code null}, wenn keine darin steht.
     *
     * <p>Das Vorzeichen fällt weg, weil es zur Buchhaltung der Bank gehört und nicht zum Feld: „Kurswert:
     * -1.100,-- EUR" nennt denselben Kurswert wie „Kurswert 1.100,00 EUR", das Minus sagt nur, dass es
     * eine Belastung ist. Was ein Wert bedeutet, sagt hier seine Beschriftung.</p>
     *
     * <p>Ohne das ginge auch die Rechnung schief: bei einer Dividende ist {@code Netto = Brutto − Steuer},
     * und mit einer negativ gelesenen Steuer käme aus 144,52 und 36,73 nicht 181,25 heraus, sondern
     * 107,79. Ebenso beim Lernen — der Nutzer tippt 1.100 ein und muss die Zeile wiederfinden.</p>
     */
    static Double lastNumber(String line) {
        return numberAt(line, Position.LAST, 1);
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
        return sum == other.sum && direction == other.direction && anchors.equals(other.anchors)
                && currency.equals(other.currency) && position == other.position && nth == other.nth;
    }

    @Override
    public int hashCode() {
        return (((anchors.hashCode() * 31 + direction.hashCode()) * 31 + currency.hashCode()) * 31
                + position.hashCode()) * 31 + nth + (sum ? 1 : 0);
    }

    @Override
    public String toString() {
        return (sum ? "Summe von " : "") + anchors + " (" + direction
                + (position == Position.FIRST || nth > 1 ? ", " + nth + ". Zahl von "
                        + (position == Position.FIRST ? "links" : "rechts") : "")
                + (currency.isEmpty() ? "" : ", " + currency) + ")";
    }
}
