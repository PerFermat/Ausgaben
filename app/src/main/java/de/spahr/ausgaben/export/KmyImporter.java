package de.spahr.ausgaben.export;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.db.Booking;

/**
 * Liest aus einer {@link KmyDocument} die Buchungen eines gewählten (Bargeld-/Vermögens-)Kontos und
 * bildet sie auf App-{@link Booking}s ab. Betrag/Vorzeichen stammen aus dem Konto-Split, Kategorie aus
 * dem Gegen-Split, Empfänger aus der Payee-Referenz.
 */
public class KmyImporter {

    private final KmyDocument doc;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    /** Beginn von morgen (lokale Mitternacht): Buchungen ab hier gelten als vorgemerkt/zukünftig. */
    private final long futureCutoff = startOfTomorrow();

    public KmyImporter(KmyDocument doc) {
        this.doc = doc;
    }

    public List<String> accountNames() {
        return doc.accountNames();
    }

    /** Alle Buchungen, die einen Split auf dem gewählten Konto haben. */
    public List<Booking> bookingsForAccount(String accountName) throws IOException {
        List<Booking> out = new ArrayList<>();
        String accountId = doc.accountId(accountName);
        if (accountId == null) {
            return out;
        }
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(doc.xml()));

            int event = parser.getEventType();
            boolean inLedger = false; // nur echte Buchungen aus <TRANSACTIONS>, nicht aus <SCHEDULES>
            String postdate = null;
            String entrydate = null;
            String txMemo = "";
            List<String[]> splits = null; // je Split: {account, value, payeeId, memo}
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("TRANSACTIONS".equals(tag)) {
                        inLedger = true;
                    } else if (inLedger && "TRANSACTION".equals(tag)) {
                        postdate = parser.getAttributeValue(null, "postdate");
                        entrydate = parser.getAttributeValue(null, "entrydate");
                        txMemo = orEmpty(parser.getAttributeValue(null, "memo"));
                        splits = new ArrayList<>();
                    } else if (inLedger && "SPLIT".equals(tag) && splits != null) {
                        splits.add(new String[]{
                                orEmpty(parser.getAttributeValue(null, "account")),
                                orEmpty(parser.getAttributeValue(null, "value")),
                                orEmpty(parser.getAttributeValue(null, "payee")),
                                orEmpty(parser.getAttributeValue(null, "memo"))});
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("TRANSACTIONS".equals(tag)) {
                        break; // Hauptbuch vollständig gelesen; <SCHEDULES> (geplante Buchungen) ignorieren
                    } else if (inLedger && "TRANSACTION".equals(tag)) {
                        Booking b = toBooking(accountId, accountName, postdate, entrydate, txMemo, splits);
                        if (b != null) {
                            out.add(b);
                        }
                        splits = null;
                    }
                }
                event = parser.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException("KMyMoney-Datei konnte nicht gelesen werden", e);
        }
        return out;
    }

    private Booking toBooking(String accountId, String accountName, String postdate, String entrydate,
                              String txMemo, List<String[]> splits) {
        if (splits == null) {
            return null;
        }
        String[] own = null;
        String[] counter = null;
        for (String[] s : splits) {
            if (own == null && accountId.equals(s[0])) {
                own = s;
            } else if (counter == null) {
                counter = s;
            }
        }
        if (own == null) {
            return null; // Buchung betrifft dieses Konto nicht
        }

        long signedCents = valueToCents(own[1]);
        Booking b = new Booking();
        b.amountCents = Math.abs(signedCents);
        b.isIncome = signedCents > 0;
        b.account = accountName;
        b.category = counter == null ? "" : orEmpty(doc.categoryPath(counter[0]));
        String payeeId = !own[2].isEmpty() ? own[2] : (counter == null ? "" : counter[2]);
        b.payee = payeeId.isEmpty() ? "" : orEmpty(doc.payeeName(payeeId));
        b.note = !own[3].isEmpty() ? own[3] : txMemo;
        b.createdAt = parseDate(postdate, entrydate);
        if (b.createdAt >= futureCutoff) {
            return null; // vorgemerkte/zukünftige Buchung – nicht importieren
        }
        b.exported = true;
        return b;
    }

    private static long startOfTomorrow() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        c.add(java.util.Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    /** KMyMoney-Betrag „num/den" → Cent (gerundet, mit Vorzeichen). */
    private long valueToCents(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            int slash = value.indexOf('/');
            if (slash < 0) {
                return new BigDecimal(value.trim()).multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
            }
            BigDecimal num = new BigDecimal(value.substring(0, slash).trim());
            BigDecimal den = new BigDecimal(value.substring(slash + 1).trim());
            return num.multiply(BigDecimal.valueOf(100))
                    .divide(den, 0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return 0;
        }
    }

    /**
     * postdate → Cent-Zeit; bei leerem/ungültigem postdate auf entrydate zurückfallen, sonst {@code 0}
     * (Epoche), damit Buchungen ohne Datum ans Ende (statt mit „heute" an den Anfang) sortiert werden.
     */
    private long parseDate(String postdate, String entrydate) {
        long t = parseOne(postdate);
        if (t >= 0) {
            return t;
        }
        t = parseOne(entrydate);
        return t >= 0 ? t : 0L;
    }

    /** Parst „yyyy-MM-dd" oder liefert -1 bei leer/ungültig. */
    private long parseOne(String s) {
        if (s == null || s.trim().isEmpty()) {
            return -1;
        }
        try {
            return dateFormat.parse(s.trim()).getTime();
        } catch (ParseException e) {
            return -1;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
