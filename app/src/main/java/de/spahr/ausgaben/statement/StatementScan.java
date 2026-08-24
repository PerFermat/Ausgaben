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
        if (containsAny(all, DIVIDEND_WORDS)) {
            return DIVIDEND;
        }
        if (containsAny(all, SELL_WORDS)) {
            return SELL;
        }
        return containsAny(all, BUY_WORDS) ? BUY : null;
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
