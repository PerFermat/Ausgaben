package de.spahr.ausgaben.export;

import de.spahr.ausgaben.db.Booking;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Erzeugt eine kMyMoney-taugliche CSV im deutschen Format:
 * Spaltentrenner ';', Dezimaltrennzeichen ',', Datum TT.MM.JJJJ.
 * Ausgaben werden mit negativem, Einnahmen mit positivem Betrag exportiert.
 */
public class CsvExporter {

    private static final String SEPARATOR = ";";
    private static final String NEWLINE = "\r\n";
    private static final String[] HEADER = {"Datum", "Empfänger", "Konto", "Typ", "Betrag", "Notiz"};

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY);

    /** Baut den CSV-Inhalt für die übergebenen Buchungen. */
    public String build(List<Booking> bookings) {
        StringBuilder sb = new StringBuilder();
        sb.append(joinRow(HEADER)).append(NEWLINE);
        for (Booking b : bookings) {
            String datum = dateFormat.format(new Date(b.createdAt));
            String typ = b.isIncome ? "Einnahme" : "Ausgabe";
            sb.append(joinRow(new String[]{
                    datum,
                    b.payee,
                    b.account,
                    typ,
                    formatAmount(b.amountCents, b.isIncome),
                    b.note
            })).append(NEWLINE);
        }
        return sb.toString();
    }

    /** Erzeugt den Dateinamen mit Zeitstempel, z. B. Ausgaben-20260629-153012.csv */
    public String buildFileName() {
        return "Ausgaben-" + timestampFormat.format(new Date()) + ".csv";
    }

    /** Dateiname pro Konto, z. B. Bargeld-20260629-153012.csv (Kontoname bereinigt). */
    public String buildFileName(String account) {
        return sanitize(account) + "-" + timestampFormat.format(new Date()) + ".csv";
    }

    /** Macht einen Kontonamen für einen Dateinamen sicher. */
    private String sanitize(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Konto";
        }
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return s.isEmpty() ? "Konto" : s;
    }

    /** Betrag in deutschem Format mit Komma; Ausgaben negativ. */
    private String formatAmount(long amountCents, boolean isIncome) {
        long signed = isIncome ? amountCents : -amountCents;
        long euros = signed / 100;
        long cents = Math.abs(signed % 100);
        String sign = (signed < 0 && euros == 0) ? "-" : "";
        return sign + euros + "," + String.format(Locale.GERMANY, "%02d", cents);
    }

    private String joinRow(String[] fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                row.append(SEPARATOR);
            }
            row.append(quote(fields[i]));
        }
        return row.toString();
    }

    /** RFC-4180-Quoting: nur quoten, wenn Trenner, Anführungszeichen oder Zeilenumbruch enthalten. */
    private String quote(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.contains(SEPARATOR)
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");
        if (!needsQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
