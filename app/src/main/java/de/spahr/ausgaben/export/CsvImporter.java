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
 * Importiert das kMyMoney-Ledger-CSV-Format:
 * <pre>
 * Kontentyp:Bargeld
 * &lt;leer&gt;
 * Datum;Zahlungsempfänger;Betrag;Konto/Kategorie;Notiz;Status;Nummer;Split-…
 * 2010-12-06;"Meisters";"-40,00";"Unterhaltung:Ausgehen";;C;
 * </pre>
 * Verwertet werden Spalte 1 (Datum, ISO yyyy-MM-dd), Spalte 2 (Empfänger), Spalte 3 (Betrag,
 * Vorzeichen bestimmt Ausgabe/Einnahme) und Spalte 5 (Notiz). Das Konto stammt aus Zeile 1.
 * Alle importierten Buchungen werden als bereits exportiert markiert.
 */
public class CsvImporter {

    private final SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
    private String parsedAccount = "";
    private final Context ctx;

    public CsvImporter(Context context) {
        this.ctx = de.spahr.ausgaben.i18n.LocaleManager.localizedContext(context);
    }

    /** Kontoname aus der zuletzt geparsten Datei (Zeile „Kontentyp:…"). */
    public String getParsedAccount() {
        return parsedAccount;
    }

    /** Parst den Dateiinhalt. Wirft {@link IllegalArgumentException} bei unbrauchbarem Aufbau. */
    public List<Booking> parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException(ctx.getString(R.string.err_csv_empty));
        }
        String[] lines = content.split("\r\n|\n|\r");

        String account = null;
        int headerIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (account == null && line.contains(":")
                    && line.toLowerCase(Locale.GERMANY).startsWith("kont")) {
                // Label tolerant: "Kontentyp", "Kontotyp", "Konto", …
                account = line.substring(line.indexOf(':') + 1).trim();
            }
            if (line.toLowerCase(Locale.GERMANY).startsWith("datum;")) {
                headerIndex = i;
                break;
            }
        }
        if (account == null || account.isEmpty()) {
            throw new IllegalArgumentException(ctx.getString(R.string.err_csv_account_missing));
        }
        if (headerIndex < 0) {
            throw new IllegalArgumentException(ctx.getString(R.string.err_csv_header_missing));
        }
        parsedAccount = account;

        List<Booking> result = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String raw = lines[i];
            if (raw.trim().isEmpty()) {
                continue;
            }
            List<String> fields = splitCsv(raw);
            if (fields.size() < 3) {
                continue;
            }
            String dateStr = fields.get(0).trim();
            String payee = fields.get(1).trim();
            String amountStr = fields.get(2).trim();
            String category = fields.size() > 3 ? fields.get(3).trim() : "";
            String note = fields.size() > 4 ? fields.get(4).trim() : "";

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
        return result;
    }

    /** ISO-Datum → epoch millis zur lokalen Mitternacht; -1 bei Fehler. */
    private long parseDate(String s) {
        try {
            Date d = isoDate.parse(s);
            if (d == null) {
                return -1;
            }
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (ParseException e) {
            return -1;
        }
    }

    /** Deutscher Betrag (ggf. mit Tausenderpunkt) → vorzeichenbehaftete Cent; null bei Fehler. */
    private Long parseAmountToCents(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String s = raw.replace(" ", "");
        if (s.contains(",")) {
            // Deutsch: Punkt = Tausender, Komma = Dezimal
            s = s.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(s).movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    /** Zerlegt eine CSV-Zeile an ';' unter Beachtung von "…"-Quoting (mit ""-Escaping). */
    private List<String> splitCsv(String line) {
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
                } else if (c == ';') {
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
