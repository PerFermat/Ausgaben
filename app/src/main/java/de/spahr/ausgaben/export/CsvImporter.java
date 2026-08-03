package de.spahr.ausgaben.export;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.Context;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;

/**
 * Importiert ein Ledger-CSV sprachunabhängig. Erkannt werden zwei Layouts:
 * <ul>
 *   <li><b>KMyMoney-Ledger</b> (jede Sprache): {@code Datum;Empfänger;Betrag;Konto/Kategorie;Notiz;…}</li>
 *   <li><b>Ausgaben-Export</b>: {@code Datum;Empfänger;Konto;Typ;Betrag;Notiz;Kategorie}</li>
 * </ul>
 * <pre>
 * Account Type:Credit Card,Account Name:My Visa   (bzw. Kontentyp:Bargeld / AccountType:Bargeld)
 * &lt;leer&gt;
 * Date,Payee,Amount,Account/Cat,Memo,Status,…     (bzw. Datum;Zahlungsempfänger;Betrag;…)
 * 2010-12-06,"Meisters","-40.00","Unterhaltung",…
 * </pre>
 * Der Aufbau wird strukturell erkannt: erste nicht-leere Zeile = Kontozeile (Name = Wert hinter dem
 * letzten „:"), nächste nicht-leere Zeile = Kopfzeile (bestimmt das Trennzeichen), danach Datenzeilen.
 * Das Spalten-Layout wird aus dem Inhalt der ersten Datenzeile abgeleitet. Alle importierten Buchungen
 * werden als bereits exportiert markiert.
 */
public class CsvImporter {

    /** Datumsformate in Prüfreihenfolge: ISO (KMyMoney), dt. Punkt (App-Export), Schrägstrich-Varianten. */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "dd.MM.yyyy", "yyyy/MM/dd", "MM/dd/yyyy", "dd/MM/yyyy"};

    private String parsedAccount = "";
    private final Context ctx;

    public CsvImporter(Context context) {
        this.ctx = de.spahr.ausgaben.i18n.LocaleManager.localizedContext(context);
    }

    /** Kontoname aus der zuletzt geparsten Datei (Wert hinter dem letzten „:" der Kontozeile). */
    public String getParsedAccount() {
        return parsedAccount;
    }

    /** Parst den Dateiinhalt. Wirft {@link IllegalArgumentException} bei unbrauchbarem Aufbau. */
    public List<Booking> parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException(ctx.getString(R.string.err_csv_empty));
        }
        String[] lines = content.split("\r\n|\n|\r");

        // Erste nicht-leere Zeile = Kontozeile, nächste nicht-leere = Kopfzeile.
        int accountIndex = nextNonEmpty(lines, 0);
        int headerIndex = accountIndex < 0 ? -1 : nextNonEmpty(lines, accountIndex + 1);

        String account = accountIndex < 0 ? null : accountName(lines[accountIndex]);
        if (account == null || account.isEmpty()) {
            throw new IllegalArgumentException(ctx.getString(R.string.err_csv_account_missing));
        }
        if (headerIndex < 0) {
            throw new IllegalArgumentException(ctx.getString(R.string.err_csv_header_missing));
        }
        parsedAccount = account;

        char sep = detectSeparator(lines[headerIndex]);

        // Spalten-Layout aus der ersten verwertbaren Datenzeile bestimmen (Betrag in Spalte 2 → KMyMoney,
        // sonst Spalte 4 → Ausgaben-Export). Standard: KMyMoney.
        int amountCol = 2;
        int categoryCol = 3;
        int noteCol = 4;
        for (int i = headerIndex + 1; i < lines.length; i++) {
            if (lines[i].trim().isEmpty()) {
                continue;
            }
            List<String> f = splitCsv(lines[i], sep);
            if (f.size() < 3 || parseDate(f.get(0).trim()) < 0) {
                continue;
            }
            boolean amountAt2 = parseAmountToCents(f.get(2).trim()) != null;
            if (!amountAt2 && f.size() > 4 && parseAmountToCents(f.get(4).trim()) != null) {
                amountCol = 4;
                noteCol = 5;
                categoryCol = 6;   // Ausgaben-Export-Layout
            }
            break;
        }

        List<Booking> result = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.trim().isEmpty()) {
                continue;
            }
            List<String> fields = splitCsv(raw, sep);
            if (fields.size() <= amountCol) {
                continue;
            }
            String dateStr = fields.get(0).trim();
            String payee = fields.get(1).trim();
            String amountStr = fields.get(amountCol).trim();
            String category = fields.size() > categoryCol ? fields.get(categoryCol).trim() : "";
            String note = fields.size() > noteCol ? fields.get(noteCol).trim() : "";

            Long cents = parseAmountToCents(amountStr);
            long when = parseDate(dateStr);
            if (cents == null || when < 0) {
                continue; // unbrauchbare Zeile überspringen
            }

            Booking b = new Booking();
            b.amountCents = Math.abs(cents);
            b.isIncome = cents >= 0;
            b.payee = payee;
            b.account = account;
            b.category = category;
            b.note = note;
            b.createdAt = when;
            b.exported = true;
            result.add(b);
        }
        if (result.isEmpty()) {
            // Aufbau passte oberflächlich (irgendein „:" in Zeile 1), aber keine einzige Zeile war eine
            // Buchung – typisch für fremde CSV (Kontoauszug einer Bank, Berichts-Export). Ohne diese
            // Meldung käme ein beruhigendes „0 Buchungen importiert" zurück.
            throw new IllegalArgumentException(ctx.getString(R.string.err_csv_no_bookings));
        }
        return result;
    }

    /** Index der nächsten nicht-leeren Zeile ab {@code from} (inklusive), sonst -1. */
    private static int nextNonEmpty(String[] lines, int from) {
        for (int i = Math.max(0, from); i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Kontoname = Wert hinter dem letzten „:" der Kontozeile (ohne Anführungszeichen/Whitespace). */
    private static String accountName(String line) {
        int colon = line.lastIndexOf(':');
        if (colon < 0) {
            return null;
        }
        String value = line.substring(colon + 1).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value.trim();
    }

    /** Trennzeichen aus der Kopfzeile: häufigeres von ';' und ',' (';' bei Gleichstand). */
    private static char detectSeparator(String header) {
        int semis = 0;
        int commas = 0;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == ';') {
                semis++;
            } else if (c == ',') {
                commas++;
            }
        }
        return commas > semis ? ',' : ';';
    }

    /** Datum → epoch millis zur lokalen Mitternacht; -1 bei Fehler. Toleriert mehrere Formate. */
    private long parseDate(String s) {
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
     * Betrag → vorzeichenbehaftete Cent; null bei Fehler. Toleriert deutsches und englisches Format:
     * das rechteste von ',' und '.' gilt als Dezimaltrenner, das andere (Tausender) wird entfernt.
     */
    private Long parseAmountToCents(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String s = raw.replace(" ", "").replace(" ", "");
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
            return new BigDecimal(s).movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    /** Zerlegt eine CSV-Zeile am {@code sep} unter Beachtung von "…"-Quoting (mit ""-Escaping). */
    private List<String> splitCsv(String line, char sep) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == sep) {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
