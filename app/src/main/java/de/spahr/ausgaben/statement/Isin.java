package de.spahr.ausgaben.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die ISIN in einer Depotabrechnung — die einzige Angabe, die weltweit gleich aussieht und sich selbst
 * prüfen lässt. Sie ist deshalb der Anker, der ohne gelernte Vorlage auskommt: einmal einem Wertpapier
 * zugeordnet, ist die Zuordnung dauerhaft eindeutig, ganz ohne Namensvergleich.
 *
 * <p>Zwölf Zeichen: zwei Buchstaben Länderkennung, neun alphanumerische Stellen, eine Prüfziffer. Geprüft
 * wird nach dem Verfahren „modulus 10 double add double": Buchstaben werden zu Zahlen (A=10 … Z=35), dann
 * wird im Nutzteil von rechts jede zweite Stelle verdoppelt.</p>
 *
 * <p>Die Prüfziffer ist der Grund, warum diese Erkennung ohne Vorlage tragfähig ist — eine zufällige
 * Zeichenfolge im Text besteht sie mit einer Wahrscheinlichkeit von einem Zehntel nicht.</p>
 */
public final class Isin {

    /** Zwölf Zeichen am Wortrand; die Prüfung entscheidet danach, ob es wirklich eine ISIN ist. */
    private static final Pattern CANDIDATE = Pattern.compile("\\b([A-Z]{2}[A-Z0-9]{9}\\d)\\b");

    private Isin() {
    }

    /** Ob {@code code} eine formal gültige ISIN mit passender Prüfziffer ist. */
    public static boolean isValid(String code) {
        if (code == null) {
            return false;
        }
        String s = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (s.length() != 12 || !Character.isDigit(s.charAt(11))) {
            return false;
        }
        for (int i = 0; i < 2; i++) {
            if (!Character.isLetter(s.charAt(i))) {
                return false;
            }
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                digits.append(c - 'A' + 10);
            } else if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                return false;
            }
        }
        int total = 0;
        boolean doubling = true;   // von rechts betrachtet wird die letzte Stelle des Nutzteils verdoppelt
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubling) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            total += d;
            doubling = !doubling;
        }
        return (10 - total % 10) % 10 == s.charAt(11) - '0';
    }

    /** Alle gültigen ISINs im Text, in Lesereihenfolge und ohne Wiederholungen. */
    public static List<String> findAll(PdfText text) {
        List<String> found = new ArrayList<>();
        if (text == null) {
            return found;
        }
        for (PdfText.Line line : text.lines()) {
            Matcher m = CANDIDATE.matcher(line.text().toUpperCase(java.util.Locale.ROOT));
            while (m.find()) {
                String candidate = m.group(1);
                if (isValid(candidate) && !found.contains(candidate)) {
                    found.add(candidate);
                }
            }
        }
        return found;
    }

    /**
     * Die ISIN der Abrechnung, oder {@code null}.
     *
     * <p>Stehen mehrere im Dokument, wird <b>keine</b> genommen: dann handelt es sich um eine
     * Sammelabrechnung mit mehreren Positionen, und welche gemeint ist, kann die App nicht wissen.
     * Lieber nichts vorbelegen als das falsche Wertpapier.</p>
     */
    public static String single(PdfText text) {
        List<String> all = findAll(text);
        return all.size() == 1 ? all.get(0) : null;
    }
}
