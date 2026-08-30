package de.spahr.ausgaben.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die CUSIP in einer Depotabrechnung — das Gegenstück zur {@link Isin} für US-amerikanische und
 * kanadische Broker, die keine ISIN drucken. Neun Zeichen, das letzte eine Prüfziffer, geprüft nach
 * demselben Verfahren „modulus 10 double add double" wie bei der ISIN.
 *
 * <p>„YOU BOUGHT XBI 78464A870 06/29/22" trägt keine ISIN, aber die CUSIP — ohne sie bliebe ein
 * US-Beleg ohne jede selbstprüfende Kennung.</p>
 */
public final class Cusip {

    /** Neun Zeichen am Wortrand, das letzte eine Ziffer (die Prüfziffer ist stets numerisch). */
    private static final Pattern CANDIDATE = Pattern.compile("\\b([0-9A-Z]{8}[0-9])\\b");

    private Cusip() {
    }

    /** Ob {@code code} eine formal gültige CUSIP mit passender Prüfziffer ist. */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        String s = code.trim().toUpperCase(Locale.ROOT);
        if (s.length() != 9) {
            return false;
        }
        int total = 0;
        for (int i = 0; i < 8; i++) {
            int v = value(s.charAt(i));
            if (v < 0) {
                return false;
            }
            if ((i + 1) % 2 == 0) {   // gerade Stelle, 1-indexiert
                v *= 2;
            }
            total += v / 10 + v % 10;
        }
        char last = s.charAt(8);
        if (!Character.isDigit(last)) {
            return false;
        }
        return (10 - total % 10) % 10 == last - '0';
    }

    /** Der Zahlenwert einer Stelle: Ziffern 0–9, Buchstaben 10–35, {@code *}=36, {@code @}=37, {@code #}=38. */
    private static int value(char c) {
        if (Character.isDigit(c)) {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        switch (c) {
            case '*':
                return 36;
            case '@':
                return 37;
            case '#':
                return 38;
            default:
                return -1;
        }
    }

    /** Alle gültigen CUSIPs im Text, in Lesereihenfolge und ohne Wiederholungen. */
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

    /** Die CUSIP der Abrechnung, oder {@code null} — bei mehreren wird keine genommen (siehe {@link Isin#single}). */
    public static String single(PdfText text) {
        List<String> all = findAll(text);
        return all.size() == 1 ? all.get(0) : null;
    }
}
