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
        return of(
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
                "Valuta                            17.08.2026");
    }
}
