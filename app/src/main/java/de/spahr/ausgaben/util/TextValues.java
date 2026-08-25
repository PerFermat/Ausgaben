package de.spahr.ausgaben.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zahlen und Datumsangaben aus fremdem Text lesen — aus einer CSV-Datei ebenso wie aus der Textebene
 * einer PDF-Abrechnung.
 *
 * <p>Stammt aus dem {@code CsvImporter}, wo beides jahrelang privat lag. Herausgelöst, damit die
 * PDF-Auslese dieselbe Erkennung benutzt und nicht eine zweite, leicht abweichende bekommt.</p>
 *
 * <p>Reine Rechenklasse ohne Android-Bezug.</p>
 */
public final class TextValues {

    /**
     * Datumsformate in Prüfreihenfolge: ISO (KMyMoney), dt. Punkt (App-Export), Schrägstrich- und
     * Bindestrich-Varianten. Letztere kommen bei französischen und belgischen Banken vor
     * ({@code 05-12-2019}); dass sie zweideutig sind, fängt {@link #toUnambiguousDateMillis} ab.
     */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "dd.MM.yyyy", "yyyy/MM/dd", "MM/dd/yyyy", "dd/MM/yyyy", "dd-MM-yyyy",
            "MM-dd-yyyy", "MM/dd/yy", "dd/MM/yy", "dd MMM yyyy", "MMM dd yyyy"};

    /**
     * Ein Datum mit Schrägstrichen oder Bindestrichen, dessen erste beide Zahlen einzeln gelesen werden
     * müssen. Der ISO-Form {@code yyyy-MM-dd} kommt das nicht in die Quere: dort steht vorn das Jahr.
     */
    private static final Pattern SLASH_DATE = Pattern.compile("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})$");

    private TextValues() {
    }

    /**
     * Betrag → vorzeichenbehaftete Cent; {@code null} bei Fehler. Toleriert deutsches und englisches
     * Format: das rechteste von ',' und '.' gilt als Dezimaltrenner, das andere (Tausender) fällt weg.
     */
    public static Long toCents(String raw) {
        BigDecimal d = toBigDecimal(raw);
        if (d == null) {
            return null;
        }
        try {
            return d.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /**
     * Dezimalzahl → {@code double}; {@code null} bei Fehler. Dieselbe Trennzeichen-Erkennung wie
     * {@link #toCents}, aber <b>ohne</b> Rundung auf Cent: eine Stückzahl wie {@code 1.839,80185} hat
     * mehr Nachkommastellen als Geld, und die dürfen nicht verlorengehen.
     */
    public static Double toDecimal(String raw) {
        BigDecimal d = toBigDecimal(raw);
        return d == null ? null : d.doubleValue();
    }

    /**
     * Gemeinsame Trennzeichen-Erkennung: das rechteste Zeichen entscheidet.
     *
     * <p>Davor wird abgeschält, was Banken um die Zahl herum schreiben. Das ist keine Bequemlichkeit:
     * {@code AnchorRule} liest die <b>letzte</b> Zahl einer Zeile — ist die unlesbar, gewinnt
     * stillschweigend eine frühere, und aus „Kapitalertragsteuer 24,45 % auf 131,25 EUR 32,09- EUR"
     * wird die Bemessungsgrundlage statt der Steuer.</p>
     *
     * <p>Der Parse am Ende bleibt <b>streng</b>. Nur dadurch bleiben Auftrags-, Depot- und
     * Referenznummern draußen, die einer Zahl zum Verwechseln ähnlich sehen: {@code 495752/48.00},
     * {@code 0993.01010100.0000346ER02}, {@code 1.234.567.890}, {@code 11.6.2022-01:30:01}.</p>
     */
    private static BigDecimal toBigDecimal(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        // Auch das geschützte Leerzeichen (U+00A0): Banken setzen es gern zwischen Zahl und
        // Währung. Als Unicode-Fluchtfolge geschrieben, weil es sonst unsichtbar im Quelltext steht
        // und beim nächsten Verschieben still zu einem normalen Leerzeichen wird.
        String s = raw.replace(" ", "").replace("\u00A0", "");
        boolean negative = false;

        // (1.234,56) — angelsächsische Schreibweise für negativ.
        if (s.length() > 2 && s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')') {
            negative = true;
            s = s.substring(1, s.length() - 1);
        }
        // €2.083,94 · £638.52 · $1,029.92
        while (!s.isEmpty() && isCurrencySymbol(s.charAt(0))) {
            s = s.substring(1);
        }
        if (!s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
            negative ^= s.charAt(0) == '-';
            s = s.substring(1);
        }
        // 926,40-EUR — Kürzel am Wort, danach erst das nachgestellte Vorzeichen.
        if (s.length() > 3 && isCurrencyCode(s.substring(s.length() - 3))) {
            s = s.substring(0, s.length() - 3);
        }
        while (!s.isEmpty() && isCurrencySymbol(s.charAt(s.length() - 1))) {
            s = s.substring(0, s.length() - 1);
        }
        // 1.242,-- — österreichisch für „und keine Cent". Vor dem Vorzeichen behandelt, sonst nähme der
        // nächste Schritt den letzten Strich für ein Minus und ließe „1.242,-" übrig.
        if (s.endsWith(",--") || s.endsWith(".--")) {
            s = s.substring(0, s.length() - 2) + "00";
        }
        // 32,09- · 144,52+ — in Deutschland und Österreich steht das Vorzeichen hinter der Zahl.
        if (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            if (last == '-' || last == '+') {
                negative ^= last == '-';
                s = s.substring(0, s.length() - 1);
            }
        }
        // 4'420.00 — Schweizer Tausendertrennung, gerader wie krummer Apostroph.
        s = s.replace("'", "").replace("\u2019", "");

        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            // Beide vorhanden: das rechteste ist der Dezimaltrenner, das andere Tausendertrennung.
            if (lastComma > lastDot) {
                s = s.replace(".", "").replace(',', '.');
            } else {
                s = s.replace(",", "");
            }
        } else if (lastComma >= 0) {
            // Nur Komma → Dezimaltrenner (deutsch).
            s = s.replace(',', '.');
        }
        // Nur Punkt (oder keins) → bereits im BigDecimal-Format.
        try {
            BigDecimal value = new BigDecimal(s);
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Währungszeichen, die unmittelbar an der Zahl kleben können. */
    private static boolean isCurrencySymbol(char c) {
        return c == '€' || c == '$' || c == '£' || c == '¥' || c == '₣';
    }

    /** Drei Großbuchstaben am Wortende — ein Währungskürzel wie EUR, CHF, USD. */
    private static boolean isCurrencyCode(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 'A' || s.charAt(i) > 'Z') {
                return false;
            }
        }
        return true;
    }

    /**
     * Die Wörter einer Zeile, die sich als Zahl lesen lassen — in Reihenfolge und unverändert.
     *
     * <p>Für die Auslese von Abrechnungen: dort steht der gesuchte Wert als <b>letzte</b> Zahl der Zeile.
     * „Kapitalertragsteuer 25,00% EUR 158,73" liefert nur {@code 158,73}: der Steuersatz trägt ein
     * Prozentzeichen, das Währungskürzel ist keine Zahl, und ein Datum wie {@code 19.08.2026} lässt sich
     * ebenfalls nicht als Zahl lesen — genau das trennt die Beschriftung vom Wert.</p>
     */
    public static java.util.List<String> numberTokens(String line) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (line == null) {
            return out;
        }
        for (String token : line.split("\\s+")) {
            if (!token.isEmpty() && toBigDecimal(token) != null) {
                out.add(token);
            }
        }
        return out;
    }

    /** Ob ein gelesenes Datum in einem Bereich liegt, in dem eine Abrechnung stehen kann. */
    private static boolean plausibel(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        int year = c.get(Calendar.YEAR);
        return year >= 1900 && year <= 2100;
    }

    /** Datum → epoch millis zur lokalen Mitternacht; -1 bei Fehler. Toleriert mehrere Formate. */
    public static long toDateMillis(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        for (String pattern : DATE_PATTERNS) {
            // Locale.US auch für die Monatsnamen: „Aug", „Dec" – deutsche Belege schreiben Zahlen.
            SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.US);
            fmt.setLenient(false);
            try {
                Date d = fmt.parse(s);
                if (d == null || !plausibel(d)) {
                    // „06/29/22" ginge sonst als Jahr 22 durch das Muster MM/dd/yyyy, weil
                    // SimpleDateFormat die Stellenzahl des Jahres nicht erzwingt – und das folgende
                    // Muster MM/dd/yy käme nie zum Zug.
                    continue;
                }
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                return cal.getTimeInMillis();
            } catch (ParseException e) {
                // nächstes Muster versuchen
            }
        }
        return -1;
    }

    /**
     * Wie {@link #toDateMillis}, weigert sich aber bei einem <b>mehrdeutigen</b> Datum: {@code 03/08/2026}
     * ist der 3. August oder der 8. März, je nachdem, aus welchem Land das Dokument stammt. Steht in einer
     * der beiden Stellen eine Zahl über 12, ist die Sache entschieden; sonst kommt -1 zurück.
     *
     * <p>Für die Auslese fremder Dokumente gedacht: ein leeres Feld, das der Nutzer ausfüllt, ist besser
     * als ein falsches Datum, das er übersieht. Der CSV-Import benutzt weiter {@link #toDateMillis} —
     * dort geht es um Dateien aus der App selbst und aus KMyMoney, deren Format bekannt ist.</p>
     */
    public static long toUnambiguousDateMillis(String s) {
        if (s != null) {
            Matcher m = SLASH_DATE.matcher(s.trim());
            if (m.matches()) {
                int first = Integer.parseInt(m.group(1));
                int second = Integer.parseInt(m.group(2));
                if (first <= 12 && second <= 12 && first != second) {
                    return -1;
                }
            }
        }
        return toDateMillis(s);
    }
}
