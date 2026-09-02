package de.spahr.ausgaben.statement.bank;

import java.util.List;

import de.spahr.ausgaben.pdf.PdfText;
import de.spahr.ausgaben.statement.StatementTemplate;
import de.spahr.ausgaben.util.TextValues;

/**
 * Abrechnungen der ING-DiBa: Wertpapierabrechnung (Kauf/Verkauf) und Ertragsgutschrift.
 *
 * <p>Nachgebaut aus echten Belegen; die Testfassungen stehen in {@code StatementFixtures}. Die Bank
 * schreibt jede Angabe als Zeile mit fester Beschriftung links und dem Wert rechts, und genau das macht
 * einen Leser hier so kurz.</p>
 *
 * <p>Zwei Eigenheiten, an denen die gelernte Ankerlogik regelmäßig scheitert und die deshalb den
 * Ausschlag für einen eigenen Leser geben:</p>
 * <ul>
 *   <li>Das <b>Datum</b> steht mal als «Valuta», mal als «Zahltag», mal nur als «Ausführungstag» — fällt
 *       die Valuta mit dem Zahltag zusammen, druckt die Bank die Zeile gar nicht.</li>
 *   <li>Bei einem Papier in Fremdwährung ist der Bruttobetrag in <b>Dollar</b> ausgewiesen; für die
 *       Buchung zählt der umgerechnete Euro-Betrag eine Zeile darunter.</li>
 * </ul>
 */
public final class IngReader implements BankReader {

    @Override
    public String id() {
        return "ing";
    }

    @Override
    public boolean matches(PdfText text) {
        boolean art = false;
        boolean ing = false;
        for (PdfText.Line line : text.lines()) {
            String s = line.text();
            art |= s.startsWith("Wertpapierabrechnung") || s.startsWith("Ertragsgutschrift");
            // Der Briefkopf ist das sichere Zeichen. «ISIN (WKN)» mit Klammer ist die Schreibweise
            // dieser Bank und trägt einen Beleg, dem der Kopf abgeschnitten wurde.
            ing |= s.contains("ING-DiBa") || s.startsWith("ISIN (WKN)");
        }
        return art && ing;
    }

    @Override
    public void read(PdfText text, StatementTemplate.Extraction into) {
        List<PdfText.Line> lines = text.lines();
        boolean dividende = false;
        for (PdfText.Line line : lines) {
            String s = line.text();
            if (s.startsWith("Ertragsgutschrift")) {
                dividende = true;
                into.action = "dividend";
            } else if (s.startsWith("Wertpapierabrechnung")) {
                // Die Bank kürzt: «Verk. Teil-/Bezugsr.» ist auch ein Verkauf. Steht auf der Zeile
                // weder das eine noch das andere, bleibt die Art offen — die Maske fragt dann.
                if (s.contains("Verk")) {
                    into.action = "sell";
                } else if (s.contains("Kauf") || s.contains("Bezug")) {
                    // «Bezug» ist die Ausübung eines Bezugsrechts – für das Depot ein Kauf.
                    into.action = "buy";
                }
            }
        }
        into.dateMillis = datum(lines, dividende);
        into.shares = stueckzahl(lines);
        if (dividende) {
            liesDividende(lines, into);
        } else {
            liesHandel(lines, into);
        }
    }

    /**
     * Das Datum — und das ist nicht bei jeder Art dasselbe.
     *
     * <p>Ein <b>Kauf oder Verkauf</b> gilt am Tag der Ausführung; die Valuta zwei Tage später ist nur
     * die Wertstellung des Geldes. Eine <b>Ertragsgutschrift</b> hat keinen Ausführungstag: dort ist die
     * Valuta beziehungsweise der Zahltag der Tag, an dem das Geld ankommt. Der Ex-Tag zuletzt — er sagt
     * nur, wer anspruchsberechtigt war, nicht wann gezahlt wurde.</p>
     */
    private static long datum(List<PdfText.Line> lines, boolean dividende) {
        String[] reihenfolge = dividende
                ? new String[]{"Valuta", "Zahltag", "Ex-Tag"}
                // «Ausf» statt «Ausführungstag»: manche PDFs geben den Umlaut je nach eingebettetem
                // Zeichensatz verstümmelt heraus, und kein anderes Feld dieser Belege beginnt so.
                : new String[]{"Ausf", "Handelstag", "Schlusstag", "Valuta"};
        for (String label : reihenfolge) {
            for (PdfText.Line line : lines) {
                String s = line.text();
                if (!s.startsWith(label)) {
                    continue;
                }
                // Wortweise, weil hinter der Beschriftung noch anderes stehen kann: «Ausführungstag /
                // -zeit  17.08.2026 um 09:04:58 Uhr». TextValues liest von vorn und bräuchte den
                // Zeitpunkt allein.
                for (String token : nachBeschriftung(s, label).trim().split("\\s+")) {
                    long ms = TextValues.toDateMillis(token);
                    if (ms > 0) {
                        return ms;
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Kauf und Verkauf: Stückpreis, Endbetrag — und die Kosten als <b>Differenz</b>.
     *
     * <p>Die Bank rechnet zwischen Kurswert und Endbetrag alles ab, was anfällt: Provision,
     * Handelsplatzgebühr, Courtage, beim Verkauf zusätzlich Kapitalertragsteuer und Solidaritätszuschlag.
     * Diese Zeilen einzeln aufzuzählen hieße, sie beim nächsten neuen Posten wieder zu übersehen — die
     * Differenz erfasst sie alle und stimmt auch dann noch, wenn sich die Bank etwas Neues einfallen
     * lässt. In dieser App führen Gebühr und Steuer bei Kauf und Verkauf ohnehin dasselbe Feld.</p>
     */
    private static void liesHandel(List<PdfText.Line> lines, StatementTemplate.Extraction into) {
        Long kurswert = null;
        long stueckzinsen = 0;
        for (PdfText.Line line : lines) {
            String s = line.text();
            // «Kurswert» ist die Summe, nicht der Stückpreis; «Dev.-Kurs» der Wechselkurs.
            if (s.startsWith("Kurs ") || s.equals("Kurs")) {
                into.price = letzteZahl(s);
            } else if (s.startsWith("Kurswert")) {
                kurswert = letzterBetrag(s);
            } else if (s.startsWith("Stückzinsen")) {
                // Aufgelaufene Zinsen, die der Käufer dem Verkäufer erstattet. Sie stehen zwischen
                // Kurswert und Endbetrag, sind aber keine Kosten, sondern Teil des Preises.
                Long zinsen = ersterBetrag(s);
                if (zinsen != null) {
                    stueckzinsen = Math.abs(zinsen);
                }
            } else if (s.startsWith("Endbetrag")) {
                into.netCents = letzterBetrag(s);
            }
        }
        if (kurswert != null && into.netCents != null) {
            into.feeCents = kosten(into.action, kurswert, stueckzinsen, into.netCents);
        }
    }

    /**
     * Die Kosten als Differenz — mit dem Vorzeichen, das zur Art gehört.
     *
     * <p>Beim <b>Kauf</b> zahlt man Kurswert + Stückzinsen + Kosten, beim <b>Verkauf</b> bekommt man
     * Kurswert + Stückzinsen − Kosten. Ohne diese Unterscheidung kam beim Verkauf einer Anleihe mit
     * Stückzinsen eine negative Kostensumme heraus: {@code |Endbetrag − Kurswert|} ist dort
     * Stückzinsen − Kosten, und davon noch die Stückzinsen abzuziehen ergibt −Kosten.</p>
     *
     * <p>Ohne Stückzinsen — der Regelfall, jede Aktie — ist der Betrag der Differenz in beide Richtungen
     * richtig. Deshalb kommt auch ein Beleg durch, dessen Art die Bank nicht deutlich genug
     * hingeschrieben hat. Mit Stückzinsen und ohne bekannte Art bleibt das Feld dagegen leer: die Maske
     * fragt dann nach, statt eine ausgedachte Zahl vorzulegen.</p>
     */
    private static Long kosten(String action, long kurswert, long stueckzinsen, long netCents) {
        if (stueckzinsen == 0) {
            return Math.abs(netCents - kurswert);
        }
        if ("buy".equals(action)) {
            return nichtNegativ(netCents - kurswert - stueckzinsen);
        }
        if ("sell".equals(action)) {
            return nichtNegativ(kurswert + stueckzinsen - netCents);
        }
        return null;
    }

    /** Eine negative Kostensumme wäre gerechnet, nicht gelesen – dann lieber gar keine. */
    private static Long nichtNegativ(long cents) {
        return cents < 0 ? null : cents;
    }

    /**
     * Ertragsgutschrift: Netto ist der Gesamtbetrag, Brutto der <b>umgerechnete</b> Euro-Betrag, und die
     * Steuer die Summe ihrer Einzelzeilen.
     *
     * <p>Die Steuer wird auch dann gesetzt, wenn keine Zeile sie nennt: dann ist sie 0 und nicht
     * unbekannt. Der Unterschied ist der Fall, an dem sich diese App schon einmal vertan hat — eine
     * Ausschüttung innerhalb des Freibetrags zeigte eine gerechnete Steuer, die nirgends stand.</p>
     *
     * <p>Erfasst werden Kapitalertragsteuer, Solidaritätszuschlag und Kirchensteuer. Zieht die Bank
     * darüber hinaus etwas ab, gehen Brutto − Steuer und Netto nicht auf, und die Maske meldet den
     * Widerspruch, statt still eine falsche Zahl vorzulegen.</p>
     */
    private static void liesDividende(List<PdfText.Line> lines, StatementTemplate.Extraction into) {
        long steuer = 0;
        Long brutto = null;
        for (PdfText.Line line : lines) {
            String s = line.text();
            if (s.startsWith("Kapitalertragsteuer") || s.startsWith("Solidaritätszuschlag")
                    || s.startsWith("Kirchensteuer")) {
                Long betrag = letzterBetrag(s);
                if (betrag != null) {
                    steuer += Math.abs(betrag);
                    // Ohne Beschriftung: welche Kategorie zu welcher Steuerart gehört, weiß nur der
                    // Nutzer, und hier gibt es keine Vorlage, in der es stehen könnte. Zugeordnet
                    // wird deshalb der Reihe nach — und die ist die des Dokuments.
                    into.feeParts.add(new StatementTemplate.Part("", Math.abs(betrag)));
                }
            } else if (s.startsWith("Brutto")) {
                // Bei einem Euro-Papier ist das schon der gesuchte Betrag; bei einem Dollar-Papier
                // überschreibt ihn die Umrechnungszeile weiter unten.
                brutto = euroBetrag(s);
            } else if (s.startsWith("Umg. z. Dev.-Kurs") || s.startsWith("Umger. zum Dev.-Kurs")) {
                Long umgerechnet = letzterBetrag(s);
                if (umgerechnet != null) {
                    brutto = umgerechnet;
                }
            } else if (s.startsWith("Gesamtbetrag")) {
                into.netCents = letzterBetrag(s);
            }
        }
        into.feeCents = steuer;
        into.grossCents = brutto;
    }

    /** Der Betrag am Zeilenende – aber nur, wenn die Zeile ihn in Euro ausweist. */
    private static Long euroBetrag(String line) {
        return line.contains("EUR") ? letzterBetrag(line) : null;
    }

    /**
     * Der erste Betrag der Zeile. Bei den Stückzinsen folgt in Klammern noch die Zinsvaluta mit einer
     * Tagesangabe — der Betrag steht vorn.
     */
    private static Long ersterBetrag(String line) {
        List<String> tokens = TextValues.numberTokens(line);
        // Direkt aus dem Token: toCents rundet auf der Dezimalzahl. Der Umweg über double verschöbe
        // krumme Beträge um einen Cent (2,675 wird als double zu 2,67499…).
        return tokens.isEmpty() ? null : TextValues.toCents(tokens.get(0));
    }

    private static Long letzterBetrag(String line) {
        List<String> tokens = TextValues.numberTokens(line);
        return tokens.isEmpty() ? null : TextValues.toCents(tokens.get(tokens.size() - 1));
    }

    private static Double letzteZahl(String line) {
        List<String> tokens = TextValues.numberTokens(line);
        return tokens.isEmpty() ? null : TextValues.toDecimal(tokens.get(tokens.size() - 1));
    }

    /**
     * Die Stückzahl aus der Nominale-Zeile — <b>nur</b>, wenn dort „Stück" steht.
     *
     * <p>Bei einer Anleihe schreibt die Bank stattdessen einen Nennwert hin: «Nominale EUR 2.000,00».
     * Das sind keine 2000 Stücke, sondern ein Nennbetrag, aus dem sich die Stückzahl erst über die
     * Prozentnotierung ergibt. Diesen Fall lässt der Leser offen, statt sich um den Faktor 100 zu
     * vertun — ein leeres Feld sieht der Nutzer, eine falsche Zahl übersieht er.</p>
     */
    private static Double stueckzahl(List<PdfText.Line> lines) {
        for (PdfText.Line line : lines) {
            String s = line.text();
            if (!s.startsWith("Nominale")) {
                continue;
            }
            List<String> tokens = TextValues.numberTokens(nachBeschriftung(s, "Nominale"));
            if (tokens.isEmpty()) {
                continue;
            }
            Double wert = TextValues.toDecimal(tokens.get(0));
            // Entscheidend ist, ob die Zeile eine Währung nennt: «Nominale EUR 10.000,00» ist ein
            // Nennwert, «Nominale Stück 1,19591» eine Stückzahl. Am Währungskürzel festgemacht und
            // nicht am Wort «Stück», weil das je nach Zeichensatz auch verstümmelt ankommen kann.
            if (!waehrung(s)) {
                return wert;
            }
            // Anleihe: der Nennwert steht in Euro, und der Kurs ist ein Prozentsatz. Dann sind hundert
            // Euro Nennwert ein Stück — das Dokument sagt das selbst mit dem Prozentzeichen am Kurs.
            if (wert != null && prozentnotiert(lines)) {
                return wert / 100.0;
            }
            // Nennwert ohne Prozentnotierung: dann lässt sich die Stückzahl nicht herleiten, und ein
            // leeres Feld ist besser als eine Zahl, die um den Faktor hundert danebenliegt.
            return null;
        }
        return null;
    }

    private static boolean waehrung(String line) {
        return line.contains("EUR") || line.contains("USD") || line.contains("CHF")
                || line.contains("GBP");
    }

    /** Kurs als Prozentsatz statt als Stückpreis — die Schreibweise einer Anleihe. */
    private static boolean prozentnotiert(List<PdfText.Line> lines) {
        for (PdfText.Line line : lines) {
            String s = line.text();
            if (s.startsWith("Kurs ") && s.contains("%")) {
                return true;
            }
        }
        return false;
    }

    private static String nachBeschriftung(String line, String label) {
        return line.substring(label.length());
    }
}
