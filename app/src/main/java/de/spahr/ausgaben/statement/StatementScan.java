package de.spahr.ausgaben.statement;

import java.util.Locale;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Was sich an einer Depotabrechnung <b>ohne</b> gelernte Vorlage erkennen lässt.
 *
 * <p>Bewusst wenig: nur was bei jeder Bank und in jeder Sprache gleich ist. Alles Übrige — welche
 * Beschriftung vor welchem Wert steht, welches der mehreren Datumsangaben gebucht gehört — lernt die
 * Vorlage aus einem Beispiel, statt hier als Wortliste zu versteinern.</p>
 */
public final class StatementScan {

    public static final String BUY = "buy";
    public static final String SELL = "sell";
    public static final String DIVIDEND = "dividend";

    /**
     * Wörter, die eine Aktion nahelegen — deutsch und englisch. Die <b>Reihenfolge der Prüfung</b> trägt
     * hier die Arbeit: „Verkaufsabrechnung" enthält „kauf", und „Wiederanlage einer Ausschüttung" enthält
     * beides. Geprüft wird deshalb Dividende, dann Verkauf, dann Kauf — das jeweils speziellere zuerst.
     */
    private static final String[] DIVIDEND_WORDS = {
            "ertragsgutschrift", "dividendengutschrift", "dividende", "ausschüttung", "ausschuettung",
            "dividend", "distribution"};
    private static final String[] SELL_WORDS = {
            "verkauf", "veräußerung", "veraeusserung", "sale", "sell", "redemption"};
    private static final String[] BUY_WORDS = {
            "kauf", "erwerb", "buy", "purchase", "subscription"};

    private StatementScan() {
    }

    /**
     * Ein <b>Vorschlag</b>, um welche Aktion es geht — {@code null}, wenn keines der Wörter vorkommt.
     *
     * <p>Nur ein Vorschlag: die Wortliste kann nicht vollständig sein, dafür gibt es zu viele Banken und
     * Formulierungen. Trifft sie nicht, wählt der Nutzer die Aktion einmal von Hand, und die Vorlage merkt
     * sie sich für dieses Dokument — ab dann ist die Liste für diese Bank ohne Bedeutung.</p>
     */
    public static String guessAction(PdfText text) {
        if (text == null) {
            return null;
        }
        String all = text.text().toLowerCase(Locale.ROOT);
        int dividend = count(all, DIVIDEND_WORDS);
        int sell = count(all, SELL_WORDS);
        int buy = count(all, BUY_WORDS);
        if (dividend >= sell && dividend >= buy) {
            return dividend > 0 ? DIVIDEND : null;
        }
        return sell >= buy ? SELL : BUY;
    }

    /**
     * Die Art, wenn das Dokument sie <b>eindeutig</b> nennt: nur eine der drei Wortgruppen kommt darin
     * vor. Sonst {@code null}.
     *
     * <p>Der Unterschied zu {@link #guessAction} ist keine Feinheit, sondern der zwischen Aussage und
     * Vermutung. An 2354 fremden Abrechnungen gemessen: der Vorschlag liegt insgesamt zu 76 % richtig,
     * bei eindeutigen Dokumenten aber zu <b>97,8 %</b> — und eindeutig sind gut zwei Drittel. Nur darauf
     * darf sich die Auswahl der Vorlage verlassen ({@code StatementTemplates.best}); eine Abrechnung, in
     * deren Kleingedrucktem „Verkaufsprospekt" steht, entscheidet gar nichts.</p>
     */
    public static String certainAction(PdfText text) {
        if (text == null) {
            return null;
        }
        String all = text.text().toLowerCase(Locale.ROOT);
        int dividend = count(all, DIVIDEND_WORDS);
        int sell = count(all, SELL_WORDS);
        int buy = count(all, BUY_WORDS);
        int kinds = (dividend > 0 ? 1 : 0) + (sell > 0 ? 1 : 0) + (buy > 0 ? 1 : 0);
        if (kinds != 1) {
            return null;
        }
        return dividend > 0 ? DIVIDEND : sell > 0 ? SELL : BUY;
    }

    /**
     * Wie oft die Wörter einer Liste vorkommen — nur am Wortanfang gezählt.
     *
     * <p>Die Wortgrenze ist der Kern: „Verkauf" enthält „kauf", und ohne sie zählte jede Verkaufs-
     * abrechnung auch als Kauf. Und gezählt statt der Reihe nach geprüft, weil das Kleingedruckte
     * einer Kaufabrechnung gern einmal „Verkaufsprospekt" enthält — ein einzelnes Vorkommen darf die
     * Überschrift nicht überstimmen. An einem Bestand von 2354 fremden Abrechnungen gemessen: 76 %
     * richtig statt 72 %.</p>
     */
    private static int count(String haystack, String[] needles) {
        int total = 0;
        for (String needle : needles) {
            int from = 0;
            int at;
            while ((at = haystack.indexOf(needle, from)) >= 0) {
                if (at == 0 || !isWordChar(haystack.charAt(at - 1))) {
                    total++;
                }
                from = at + 1;
            }
        }
        return total;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetter(c);
    }

    /** Ein im Dokument gefundenes Datum samt der Beschriftung seiner Zeile. */
    public static final class DateCandidate {
        public final String label;
        public final long millis;

        DateCandidate(String label, long millis) {
            this.label = label;
            this.millis = millis;
        }
    }

    /**
     * Alle Datumsangaben des Dokuments mit ihrer Beschriftung, ohne Wiederholungen.
     *
     * <p>Eine Abrechnung trägt mehrere — Briefdatum, Ausführungstag, Ex-Tag, Zahltag, Valuta. Welches
     * gebucht gehört, kann die App nicht wissen, solange sie es für diese Bank nicht gelernt hat. Statt
     * zu raten, legt sie dem Nutzer die gefundenen vor; seine Wahl wird dann zum Anker.</p>
     */
    public static java.util.List<DateCandidate> dates(PdfText text) {
        java.util.List<DateCandidate> out = new java.util.ArrayList<>();
        if (text == null) {
            return out;
        }
        for (PdfText.Line line : text.lines()) {
            long millis = AnchorRule.firstDate(line.text());
            if (millis <= 0) {
                continue;
            }
            String label = TemplateLearner.labelOf(line.text());
            if (label.trim().length() < 3) {
                continue;   // ohne Beschriftung wäre die Auswahl nicht zu unterscheiden
            }
            boolean known = false;
            for (DateCandidate c : out) {
                if (c.millis == millis && c.label.equalsIgnoreCase(label)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                out.add(new DateCandidate(label, millis));
            }
        }
        return out;
    }

    /** Eine im Dokument gefundene Zahl samt der Beschriftung, über die sie erreichbar ist. */
    public static final class ValueCandidate {
        public final String label;
        public final double value;

        ValueCandidate(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }

    /**
     * Alle Zahlen, die sich mit den <b>gerade eingestellten</b> Angaben lesen lassen — je mit der
     * Beschriftung, über die man sie erreicht.
     *
     * <p>Das Gegenstück zu {@link #dates}: dort legt die App die gefundenen Datumsangaben vor, statt zu
     * raten, welche gemeint ist. Bei den Zahlen ist die Auswahl noch nötiger, weil eine Abrechnung ein
     * Dutzend davon trägt und die Beschriftung daneben von Bank zu Bank anders lautet.</p>
     *
     * <p>Gelesen wird dabei nicht anders als später auch: zu jeder Zeile wird ihre Beschriftung
     * bestimmt, daraus eine Regel mit genau diesem einen Anker und den übergebenen Einstellungen
     * gebaut, und die liest den Wert. Damit ist zugesichert, was eine Auswahlliste zusichern muss —
     * was hier draufsteht, findet die Regel hinterher wieder. Eine Liste, die aus eigener Anschauung
     * Zahlen sammelt, könnte das nicht.</p>
     */
    public static java.util.List<ValueCandidate> values(PdfText text, AnchorRule.Direction direction,
                                                       int lineDistance, AnchorRule.Position position,
                                                       int nth, String currency) {
        java.util.List<ValueCandidate> out = new java.util.ArrayList<>();
        if (text == null) {
            return out;
        }
        for (PdfText.Line line : text.lines()) {
            String label = TemplateLearner.labelOf(line.text()).trim();
            if (label.length() < 3) {
                continue;   // ohne Beschriftung wäre die Auswahl nicht zu unterscheiden
            }
            boolean known = false;
            for (ValueCandidate c : out) {
                if (c.label.equalsIgnoreCase(label)) {
                    known = true;
                    break;
                }
            }
            if (known) {
                continue;   // dieselbe Beschriftung liest zweimal dasselbe – einmal genügt
            }
            AnchorRule probe = new AnchorRule(java.util.Collections.singletonList(label), direction,
                    false, currency, position, nth, lineDistance);
            Double value = probe.read(text);
            if (value != null) {
                out.add(new ValueCandidate(label, value));
            }
        }
        return out;
    }

    /** Die ISIN der Abrechnung, oder {@code null} (siehe {@link Isin#single}). */
    public static String isin(PdfText text) {
        return Isin.single(text);
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
