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

    /** Datumsformate in Prüfreihenfolge: ISO (KMyMoney), dt. Punkt (App-Export), Schrägstrich-Varianten. */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "dd.MM.yyyy", "yyyy/MM/dd", "MM/dd/yyyy", "dd/MM/yyyy"};

    /** Ein Datum mit Schrägstrichen, dessen erste beide Zahlen einzeln gelesen werden müssen. */
    private static final Pattern SLASH_DATE = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{2,4})$");

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

    /** Gemeinsame Trennzeichen-Erkennung: das rechteste Zeichen entscheidet. */
    private static BigDecimal toBigDecimal(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        // Auch das geschützte Leerzeichen (U+00A0): Banken setzen es gern zwischen Zahl und
        // Währung. Als Unicode-Fluchtfolge geschrieben, weil es sonst unsichtbar im Quelltext steht
        // und beim nächsten Verschieben still zu einem normalen Leerzeichen wird.
        String s = raw.replace(" ", "").replace("\u00A0", "");
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
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Datum → epoch millis zur lokalen Mitternacht; -1 bei Fehler. Toleriert mehrere Formate. */
    public static long toDateMillis(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat fmt = new SimpleDateFormat(pattern, Locale.US);
            fmt.setLenient(false);
            try {
                Date d = fmt.parse(s);
                if (d == null) {
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
