package de.spahr.ausgaben.voice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zerlegt einen gesprochenen Buchungssatz wie „Frisör 20€" in Empfänger-Suchbegriff und Betrag.
 *
 * <p>Der Betrag wird als Zahl (mit optionaler Nachkommastelle, Komma oder Punkt) erkannt; bevorzugt die
 * Zahl direkt vor einem Währungswort (€ / Euro / EUR), sonst die letzte Zahl im Satz. Beträge in Worten
 * („zwanzig") werden nicht umgesetzt. Der verbleibende Text (ohne Zahl und Währungswörter) ist der
 * Empfänger-Suchbegriff.</p>
 */
public final class VoiceInput {

    /** Ergebnis der Zerlegung. {@link #amountCents} ist {@code null}, wenn kein Betrag erkannt wurde. */
    public static final class Result {
        public final String payee;
        public final Long amountCents;

        public Result(String payee, Long amountCents) {
            this.payee = payee;
            this.amountCents = amountCents;
        }
    }

    // Zahl, optional gefolgt (mit Leerzeichen) von einem Währungswort.
    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d+(?:[.,]\\d{1,2})?)\\s*(€|euro|eur)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENCY_WORD = Pattern.compile(
            "\\b(euro|eur)\\b|€", Pattern.CASE_INSENSITIVE);

    private VoiceInput() {
    }

    public static Result parse(String spoken) {
        if (spoken == null) {
            return new Result("", null);
        }
        String text = spoken.trim();

        // Betrag suchen: den Treffer mit Währungswort bevorzugen, sonst den letzten Zahl-Treffer.
        Matcher m = AMOUNT.matcher(text);
        int lastStart = -1, lastEnd = -1;
        String lastNumber = null;
        int curStart = -1, curEnd = -1;
        String curNumber = null;
        while (m.find()) {
            lastStart = m.start();
            lastEnd = m.end();
            lastNumber = m.group(1);
            if (m.group(2) != null && curNumber == null) { // erste Zahl mit Währungswort
                curStart = m.start();
                curEnd = m.end();
                curNumber = m.group(1);
            }
        }
        boolean useCurrency = curNumber != null;
        String amtNumber = useCurrency ? curNumber : lastNumber;
        int amtStart = useCurrency ? curStart : lastStart;
        int amtEnd = useCurrency ? curEnd : lastEnd;

        Long cents = null;
        String rest = text;
        if (amtNumber != null) {
            cents = toCents(amtNumber);
            rest = text.substring(0, amtStart) + " " + text.substring(amtEnd);
        }
        // Übrige Währungswörter aus dem Empfänger entfernen und Leerraum normalisieren.
        rest = CURRENCY_WORD.matcher(rest).replaceAll(" ");
        String payee = rest.replaceAll("\\s+", " ").trim();

        return new Result(payee, cents);
    }

    /**
     * Wählt aus {@code candidates} den Empfänger, der dem gesprochenen {@code term} am ähnlichsten ist –
     * unscharf (Umlaut-Normalisierung + Ähnlichkeit auf Wortebene), damit z. B. „Friseur" auch „Frisör
     * Frank" findet. Gibt {@code null} zurück, wenn kein Kandidat ähnlich genug ist.
     */
    public static String bestFuzzyPayee(String term, List<String> candidates) {
        if (term == null || candidates == null) {
            return null;
        }
        String[] spoken = normalizeTokens(term);
        if (spoken.length == 0) {
            return null;
        }
        String best = null;
        double bestScore = 0;
        for (String cand : candidates) {
            if (cand == null || cand.trim().isEmpty()) {
                continue;
            }
            double score = tokenSimilarity(spoken, normalizeTokens(cand));
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return bestScore >= 0.70 ? best : null;
    }

    /** Beste Wort-zu-Wort-Ähnlichkeit (0..1) zwischen zwei Token-Listen; zu kurze Tokens ignoriert. */
    private static double tokenSimilarity(String[] a, String[] b) {
        double best = 0;
        for (String ta : a) {
            if (ta.length() < 4) {
                continue;
            }
            for (String tb : b) {
                if (tb.length() < 4) {
                    continue;
                }
                int d = levenshtein(ta, tb);
                int max = Math.max(ta.length(), tb.length());
                double s = max == 0 ? 1.0 : 1.0 - (double) d / max;
                if (s > best) {
                    best = s;
                }
            }
        }
        return best;
    }

    /** Kleinschreibung, Umlaute → ae/oe/ue/ss, nur Buchstaben/Ziffern; in Wörter zerlegt. */
    private static String[] normalizeTokens(String s) {
        String x = s.toLowerCase(Locale.GERMANY)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < x.length(); i++) {
            char c = x.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        String norm = sb.toString().trim();
        return norm.isEmpty() ? new String[0] : norm.split("\\s+");
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }

    private static Long toCents(String number) {
        try {
            String normalized = number.replace(",", ".");
            return new BigDecimal(normalized).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }
}
