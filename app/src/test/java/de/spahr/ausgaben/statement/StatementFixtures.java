package de.spahr.ausgaben.statement;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Abrechnungen als Text für die Tests. Nachgebaut aus zwei echten ING-Dokumenten, Kontonummern und
 * Anschrift entfernt — genau die Zeilen, auf die es ankommt, in der Reihenfolge und Spaltenlage, in der
 * {@code PdfTextExtractor} sie liefert.
 *
 * <p>Damit lässt sich die ganze Auslese ohne eine einzige PDF-Datei prüfen. Der Weg vom PDF zu diesen
 * Zeilen ist die Aufgabe von {@code PdfTextExtractor} und wird dort für sich geprüft.</p>
 */
final class StatementFixtures {

    /** Zeichenbreite und Zeilenhöhe; die Höhe liegt klar über der Bündelungstoleranz von drei Punkten. */
    private static final float CHAR_WIDTH = 6f;
    private static final float LINE_HEIGHT = 14f;

    private StatementFixtures() {
    }

    /**
     * Baut aus Textzeilen einen {@link PdfText}. Die Spalte im String wird zur x-Position, damit
     * „steht rechts daneben" auch im Test etwas bedeutet.
     */
    static PdfText of(String... lines) {
        PdfText.Builder b = new PdfText.Builder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) == ' ') {
                    col++;
                    continue;
                }
                int end = col;
                while (end < line.length() && line.charAt(end) != ' ') {
                    end++;
                }
                b.add(0, line.substring(col, end), col * CHAR_WIDTH, end * CHAR_WIDTH,
                        LINE_HEIGHT * (i + 1));
                col = end;
            }
        }
        return b.build();
    }

    /** Kauf aus einem Sparplan: krumme Stückzahl, keine Gebührenzeile, Betrag dreimal im Dokument. */
    static PdfText ingKauf() {
        return of(
                "ING-DiBa AG · 60628 Frankfurt am Main",
                "Depotinhaber: Max Muster",
                "Datum: 18.08.2026",
                "Seite: 1 von 1",
                "Wertpapierabrechnung        Kauf aus Sparplan",
                "Ordernummer                 123456789.001",
                "ISIN (WKN)                  IE00B3RBWM25 (A1JX52)",
                "Wertpapierbezeichnung       Vanguard FTSE All-World U.ETF",
                "                            Registered Shares USD Dis.oN",
                "Nominale                    Stück            6,09607",
                "Kurs                        EUR               164,04",
                "Handelsplatz                Xetra",
                "Ausführungstag / -zeit      17.08.2026 um 09:04:58 Uhr",
                "Kurswert                    EUR             1.000,00",
                "Zwischensumme               EUR             1.000,00",
                "Endbetrag zu Ihren Lasten   EUR             1.000,00",
                "Valuta                      19.08.2026");
    }

    /** Dividende in Fremdwährung: Brutto in USD, Steuer auf zwei Zeilen, Netto in Euro. */
    static PdfText ingDividende() {
        return of(dividendeZeilen());
    }

    /**
     * Dieselbe Abrechnung, wie die Bank sie <b>ohne</b> Valuta-Zeile druckt: fällt die Valuta mit dem
     * Zahltag zusammen, lässt sie die Zeile weg. Für eine Vorlage, die das Datum an „Valuta" festgemacht
     * hat, ist damit gar kein Datum mehr zu finden.
     */
    static PdfText ingDividendeOhneValuta() {
        java.util.List<String> ohne = new java.util.ArrayList<>();
        for (String line : dividendeZeilen()) {
            if (!line.startsWith("Valuta")) {
                ohne.add(line);
            }
        }
        return of(ohne.toArray(new String[0]));
    }

    /**
     * Eine Ertragsgutschrift, von der <b>nichts</b> abgezogen wird: der Ertrag liegt innerhalb des
     * Freibetrags. Die zweite Seite rechnet den Freibetrag durch (Teilfreistellung, Verrechnungstopf,
     * Sparer-Pauschbetrag) — dort fließt kein Geld, und keine dieser Zeilen trägt eine Steuer.
     *
     * <p>Nachgebaut aus einer echten Abrechnung; sie ist der Prüfstein dafür, dass eine Steuerregel ohne
     * Treffer 0 bedeutet und nicht „unbekannt".</p>
     */
    static PdfText ingDividendeOhneSteuer() {
        return of(
                "ING-DiBa AG · 60628 Frankfurt am Main",
                "Depotinhaber: Max Muster",
                "Datum: 06.03.2026",
                "Seite: 1 von 2",
                "Ertragsgutschrift",
                "ISIN (WKN)                             LU1242369327 (DBX0P1)",
                "Wertpapierbezeichnung                  Xtrackers MSCI Europe",
                "                                       Inhaber-Anteile 1D o.N.",
                "Nominale                               215,44908 Stück",
                "Ertragsausschüttung per Stück          0,3403 USD",
                "Ausschüttung mit Teilfreist. per Stück 0,23821 USD",
                "Ex-Tag                                 18.02.2026",
                "Zahltag                                05.03.2026",
                "Brutto                                 USD               73,32",
                "Zwischensumme                          USD               73,32",
                "Umg. z. Dev.-Kurs (1,160795)           EUR               63,16",
                "Gesamtbetrag zu Ihren Gunsten          EUR               63,16",
                "Valuta                                 05.03.2026",
                "Seite: 2 von 2",
                "Ausschüttung gem §2 Abs. 11 InvStG                       63,16 EUR",
                "abzgl. Teilfreistellungsbetrag 30,00 %                   18,95 EUR",
                "Ertragsausschüttung nach Teilfreistellung                44,21 EUR",
                "KapSt-pflichtiger Kapitalertrag                          44,21 EUR",
                "Mit Verrechnungstopf Allgemein verrechnet               -44,21 EUR",
                "Sparer-Pauschbetrag vor Ertrag                        1.000,00 EUR",
                "Sparer-Pauschbetrag nach Ertrag                       1.000,00 EUR");
    }

    /** Ein Kauf mit Gebührenzeile — Gegenstück zu {@link #ingKauf()}, der keine hat. */
    static PdfText ingKaufMitProvision() {
        return of(
                "Wertpapierabrechnung        Kauf",
                "ISIN (WKN)                  IE00B3RBWM25 (A1JX52)",
                "Nominale                    Stück            3,12345",
                "Kurs                        EUR               170,50",
                "Ausführungstag / -zeit      19.09.2026 um 10:11:12 Uhr",
                "Kurswert                    EUR               532,63",
                "Provision                   EUR                 4,90",
                "Endbetrag zu Ihren Lasten   EUR               537,53");
    }

    private static String[] dividendeZeilen() {
        return new String[]{
                "ING-DiBa AG · 60628 Frankfurt am Main",
                "Depotinhaber: Max Muster",
                "Datum: 19.08.2026",
                "Ertragsgutschrift",
                "ISIN (WKN)                        IE00B9CQXS71 (A1T8GD)",
                "Wertpapierbezeichnung             SSSPDR S&P Glob.Div.Arist.ETF",
                "                                  Registered Shares o.N.",
                "Nominale                          1.839,80185 Stück",
                "Ertragsausschüttung per Stück     0,5726 USD",
                "Ex-Tag                            03.08.2026",
                "Zahltag                           17.08.2026",
                "Brutto                            USD           1.053,47",
                "Zwischensumme                     USD           1.053,47",
                "Umg. z. Dev.-Kurs (1,161497)      EUR             906,99",
                "Kapitalertragsteuer 25,00%        EUR             158,73",
                "Solidaritätszuschlag 5,50%        EUR               8,73",
                "Gesamtbetrag zu Ihren Gunsten     EUR             739,53",
                "Valuta                            17.08.2026"};
    }

    /**
     * Eine Ertragsgutschrift im <b>Tabellenaufbau</b> — nachgebaut aus einem echten Beleg von Scalable
     * Capital, ohne dessen persönliche Angaben.
     *
     * <p>Der Prüfstein für Werte, die unter einer Spaltenüberschrift stehen: die Datenzeile beginnt mit
     * dem Datum und hat deshalb <b>keine eigene Beschriftung</b>, und zwischen Überschrift und Daten
     * steht noch eine zweite Kopfzeile („Wechselkurs"). Weder „in derselben Zeile" noch „genau eine
     * Zeile darunter" erreicht sie.</p>
     *
     * <p>Die Zeile trägt zudem <b>zwei</b> Daten: vorn den Buchungstag, daneben die Wertstellung. Gebucht
     * gehört die zweite.</p>
     */
    static PdfText tabellenDividende() {
        return of(
                "Dividende",
                "Für 01.07.2025 - 30.06.2026",
                "Berechtigtes Wertpapier   Vanguard FTSE All-World U.ETF",
                "ISIN                      IE00B3RBWM25",
                "Berechtigte Anzahl        816,652",
                "Ex Tag                    18.06.2026",
                "Kontobewegung",
                "Buchung    Wertstellung   Typ          Betrag / Stk.   Berechtigte Anzahl   Gesamt",
                "                                       Wechselkurs",
                "30.06.2026 01.07.2026     Gutschrift   0,905474 USD    816,652    648,36 EUR",
                "USD / EUR 1,1405",
                "Steuern      -126,26 EUR",
                "Gesamtbetrag 522,10 EUR",
                "Anfallende Steuern",
                "Typ                       Betrag",
                "Kapitalertragsteuer       111,24 EUR",
                "Solidaritätszuschlag        6,12 EUR",
                "Kirchensteuer               8,90 EUR",
                "Anfallende Steuern        126,26 EUR");
    }

    /**
     * Ein <b>Verkauf</b> von Scalable Capital, nachgebaut aus einem echten Beleg ohne die persönlichen
     * Angaben.
     *
     * <p>Der Prüfstein für die feste Ordergebühr neben ausgewiesenen Steuern: die drei Steuerzeilen
     * stehen einzeln da (zusammen 772,59), eine Summenzeile für sie gibt es nicht, und die Gebühr von
     * 0,99, die Scalable nimmt, druckt der Beleg nirgends aus. Wer die Gutschrift 20.805,03 einträgt,
     * findet im Dokument nur die 20.806,02 „zu Gunsten Konto" — die Differenz ist die feste Gebühr,
     * und <b>nur</b> sie.</p>
     */
    static PdfText scalableVerkauf() {
        return of(
                "Wertpapierabrechnung: Verkauf",
                "Auftragsdatum: 29.07.2024        Ausführungsplatz: GETTEX - MM Munich",
                "Nominale                         ISIN: DE0009779611     WKN: 977961      Kurs",
                "STK 170                          First Priv. Euro Div.STAUFER            EUR    126,933",
                "Kurswert                                                                 EUR  21.578,61",
                "Kapitalertragsteuer                                                      EUR     680,71 -",
                "Kirchensteuer                                                            EUR      54,45 -",
                "Solidaritätszuschlag                                                     EUR      37,43 -",
                "Zu Gunsten Konto 1234567890      Valuta: 02.08.2024                      EUR  20.806,02");
    }
}
