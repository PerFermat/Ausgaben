package de.spahr.ausgaben.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die SEDOL in einer Depotabrechnung — das Gegenstück zur {@link Isin} für britische und irische
 * Broker, die keine ISIN drucken. Sieben Zeichen, das letzte eine Prüfziffer, gewichtet mit
 * {@code 1, 3, 1, 7, 3, 9}.
 */
public final class Sedol {

    /**
     * Sieben Zeichen am Wortrand, das letzte eine Ziffer (die Prüfziffer ist stets numerisch).
     *
     * <p>Die Vokale A, E, I, O, U sind ausgenommen — die SEDOL-Spezifikation vergibt sie nicht. Vorher
     * stand hier {@code [0-9A-Z]}, und damit bestand jede siebenstellige Auftrags- oder Referenznummer
     * die Prüfung mit rund einem Zehntel Wahrscheinlichkeit. Ein solcher Treffer bleibt nicht folgenlos:
     * über {@code StatementScan.isin} wird er in {@code StatementTemplates.rememberSecurity} dauerhaft
     * einem Wertpapier zugeordnet.</p>
     */
    private static final Pattern CANDIDATE = Pattern.compile("\\b([0-9B-DF-HJ-NP-TV-Z]{6}[0-9])\\b");
    private static final int[] WEIGHTS = {1, 3, 1, 7, 3, 9};

    private Sedol() {
    }

    /** Ob {@code code} eine formal gültige SEDOL mit passender Prüfziffer ist. */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        String s = code.trim().toUpperCase(Locale.ROOT);
        if (s.length() != 7) {
            return false;
        }
        int total = 0;
        for (int i = 0; i < 6; i++) {
            int v = value(s.charAt(i));
            if (v < 0) {
                return false;
            }
            total += v * WEIGHTS[i];
        }
        char last = s.charAt(6);
        if (!Character.isDigit(last)) {
            return false;
        }
        return (10 - total % 10) % 10 == last - '0';
    }

    /**
     * Der Zahlenwert einer Stelle: Ziffern 0–9, Buchstaben 10–35 nach ihrer Lage im Alphabet.
     *
     * <p>Vokale gibt es in einer SEDOL nicht; sie werden hier abgelehnt statt mitgerechnet. Der Wert der
     * übrigen Buchstaben bleibt davon unberührt — gewichtet wird nach der Lage im ganzen Alphabet, nicht
     * nach der Lage in der zugelassenen Teilmenge.</p>
     */
    private static int value(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        if (c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U') {
            return -1;
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /** Alle gültigen SEDOLs im Text, in Lesereihenfolge und ohne Wiederholungen. */
    public static List<String> findAll(PdfText text) {
        List<String> found = new ArrayList<>();
        if (text == null) {
            return found;
        }
        for (PdfText.Line line : text.lines()) {
            Matcher m = CANDIDATE.matcher(line.text().toUpperCase(Locale.ROOT));
            while (m.find()) {
                String candidate = m.group(1);
                if (isValid(candidate) && !found.contains(candidate)) {
                    found.add(candidate);
                }
            }
        }
        return found;
    }

    /** Die SEDOL der Abrechnung, oder {@code null} — bei mehreren wird keine genommen (siehe {@link Isin#single}). */
    public static String single(PdfText text) {
        List<String> all = findAll(text);
        return all.size() == 1 ? all.get(0) : null;
    }
}
