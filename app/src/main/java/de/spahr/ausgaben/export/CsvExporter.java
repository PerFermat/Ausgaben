package de.spahr.ausgaben.export;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.BookingSplit;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Erzeugt eine kMyMoney-taugliche CSV im deutschen Format:
 * Spaltentrenner ';', Dezimaltrennzeichen ',', Datum TT.MM.JJJJ.
 * Ausgaben werden mit negativem, Einnahmen mit positivem Betrag exportiert.
 */
public class CsvExporter {

    private static final String SEPARATOR = ";";
    private static final String NEWLINE = "\r\n";
    private static final String[] HEADER =
            {"Datum", "Empfänger", "Konto", "Typ", "Betrag", "Notiz", "Kategorie"};

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY);
    private final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY);

    /** Baut den CSV-Inhalt für die übergebenen Buchungen, beginnend mit "Kontentyp:<Konto>" + Leerzeile. */
    public String build(String account, List<Booking> bookings) {
        return build(account, bookings, new HashMap<>());
    }

    /**
     * Wie {@link #build(String, List)}, aber Splitbuchungen (≥2 Kategorien in {@code splitsMap}) werden je
     * Teil als eigene Zeile geschrieben; Umbuchungen bleiben (als zwei getrennte Buchungen) je eine Zeile.
     */
    public String build(String account, List<Booking> bookings,
                        Map<Long, List<BookingSplit>> splitsMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("Kontentyp:").append(account == null ? "" : account).append(NEWLINE).append(NEWLINE);
        sb.append(joinRow(HEADER)).append(NEWLINE);
        for (Booking b : bookings) {
            String datum = dateFormat.format(new Date(b.createdAt));
            String typ = b.isIncome ? "Einnahme" : "Ausgabe";
            List<BookingSplit> parts = splitsMap.get(b.id);
            if (parts != null && parts.size() >= 2) {
                for (BookingSplit p : parts) {
                    long signed = b.isIncome ? p.amountCents : -p.amountCents;
                    sb.append(joinRow(new String[]{
                            datum, b.payee, b.account, typ,
                            formatSigned(signed), b.note, p.category
                    })).append(NEWLINE);
                }
            } else {
                sb.append(joinRow(new String[]{
                        datum,
                        b.payee,
                        b.account,
                        typ,
                        formatAmount(b.amountCents, b.isIncome),
                        b.note,
                        b.category
                })).append(NEWLINE);
            }
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
        return formatSigned(isIncome ? amountCents : -amountCents);
    }

    /** Bereits vorzeichenbehafteter Cent-Betrag im deutschen Format mit Komma. */
    private String formatSigned(long signed) {
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
