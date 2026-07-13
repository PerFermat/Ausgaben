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

import java.util.HashMap;
import java.util.Map;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.BookingSplit;
import de.spahr.ausgaben.db.ScheduledTransaction;
import de.spahr.ausgaben.db.Security;
import de.spahr.ausgaben.db.SecurityTx;

/**
 * Liest aus einer {@link KmyDocument} die Buchungen eines gewählten (Bargeld-/Vermögens-)Kontos und
 * bildet sie auf App-{@link Booking}s ab. Betrag/Vorzeichen stammen aus dem Konto-Split, Kategorie aus
 * dem Gegen-Split, Empfänger aus der Payee-Referenz.
 */
public class KmyImporter {

    private final KmyDocument doc;
    private final android.content.Context ctx;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public KmyImporter(KmyDocument doc, android.content.Context context) {
        this.doc = doc;
        this.ctx = de.spahr.ausgaben.i18n.LocaleManager.localizedContext(context);
    }

    public List<String> accountNames() {
        return doc.accountNames();
    }

    /** Anzeigenamen der Depots (Investment-Konten). */
    public List<String> depotNames() {
        return doc.depotNames();
    }

    /** Budgetjahre aus der Datei. */
    public List<Integer> budgetYears() {
        return doc.budgetYears();
    }

    /** Kategorie-Soll-Werte (Jahressumme) des Budgetjahres. */
    public List<KmyDocument.BudgetEntry> budgetEntries(int year) {
        return doc.budgetEntries(year);
    }

    /** Ergebnis eines Depot-Imports: Wertpapiere (mit letztem Kurs) + ihre Bewegungen. */
    public static final class DepotData {
        public final List<Security> securities = new ArrayList<>();
        public final List<SecurityTx> transactions = new ArrayList<>();
    }

    /**
     * Importiert ein Depot: seine Wertpapiere (Typ-15-Unterkonten) samt letztem Kurs und die
     * Käufe/Verkäufe/Dividenden/Einbuchungen aus dem Hauptbuch.
     */
    public DepotData importDepot(String depotName) throws IOException {
        DepotData data = new DepotData();
        String depotId = doc.depotId(depotName);
        if (depotId == null) {
            return data;
        }
        // Stock-Konten des Depots (Typ 15, parent = Depot): id → {securityKmyId, securityName}.
        Map<String, String[]> stockAccounts = new HashMap<>();
        for (String id : doc.allAccountIds()) {
            if (doc.accountTypeOf(id) == 15 && depotId.equals(doc.accountParentOf(id))) {
                String secId = doc.accountCurrencyOf(id);
                String secName = orEmpty(doc.accountNameById(id)).trim();
                stockAccounts.put(id, new String[]{secId, secName});
                String[] info = doc.securityInfo(secId);
                double[] price = doc.securityPrice(secId);
                String name = info != null && !info[0].isEmpty() ? info[0] : secName;
                String symbol = info != null ? info[1] : "";
                String currency = info != null && !info[2].isEmpty() ? info[2] : "EUR";
                data.securities.add(new Security(depotName, secId, name, symbol, currency,
                        price != null ? price[0] : 0, price != null ? (long) price[1] : 0));
            }
        }
        if (stockAccounts.isEmpty()) {
            return data;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(doc.xml()));
            int event = parser.getEventType();
            boolean inLedger = false;
            String postdate = null;
            String entrydate = null;
            List<String[]> splits = null; // je Split: {account, value, action, shares}
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("TRANSACTIONS".equals(tag)) {
                        inLedger = true;
                    } else if (inLedger && "TRANSACTION".equals(tag)) {
                        postdate = parser.getAttributeValue(null, "postdate");
                        entrydate = parser.getAttributeValue(null, "entrydate");
                        splits = new ArrayList<>();
                    } else if (inLedger && "SPLIT".equals(tag) && splits != null) {
                        splits.add(new String[]{
                                orEmpty(parser.getAttributeValue(null, "account")),
                                orEmpty(parser.getAttributeValue(null, "value")),
                                orEmpty(parser.getAttributeValue(null, "action")),
                                orEmpty(parser.getAttributeValue(null, "shares"))});
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("TRANSACTIONS".equals(tag)) {
                        break;
                    } else if (inLedger && "TRANSACTION".equals(tag)) {
                        SecurityTx tx = toSecurityTx(depotName, stockAccounts, postdate, entrydate, splits);
                        if (tx != null) {
                            data.transactions.add(tx);
                        }
                        splits = null;
                    }
                }
                event = parser.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException(ctx.getString(de.spahr.ausgaben.R.string.err_kmy_read), e);
        }
        return data;
    }

    private SecurityTx toSecurityTx(String depotName, Map<String, String[]> stockAccounts,
                                    String postdate, String entrydate, List<String[]> splits) {
        if (splits == null) {
            return null;
        }
        String[] stock = null;
        for (String[] s : splits) {
            if (stockAccounts.containsKey(s[0])) {
                stock = s;
                break;
            }
        }
        if (stock == null) {
            return null; // Transaktion betrifft kein Wertpapier dieses Depots
        }
        String[] sec = stockAccounts.get(stock[0]);
        String action = normalizeAction(stock[2]);
        double shares = de.spahr.ausgaben.export.KmyDocument.fractionToDouble(stock[3]);
        if ("dividend".equals(action)) {
            long gross = dividendGross(splits);
            long net = dividendNet(splits);
            if (net == 0) {
                net = gross;
            }
            return new SecurityTx(depotName, sec[0], sec[1],
                    parseDate(postdate, entrydate), action, shares, gross, net);
        }
        long amountCents;
        if ("add".equals(action) || "remove".equals(action)) {
            amountCents = 0;
        } else {
            amountCents = Math.abs(valueToCents(stock[1]));
        }
        return new SecurityTx(depotName, sec[0], sec[1],
                parseDate(postdate, entrydate), action, shares, amountCents);
    }

    /** Brutto-Dividende: aus dem Einnahme-Kategorie-Split (Typ 12), sonst dem positiven Geld-Split. */
    private long dividendGross(List<String[]> splits) {
        for (String[] s : splits) {
            if (doc.accountTypeOf(s[0]) == 12) {
                return Math.abs(valueToCents(s[1]));
            }
        }
        for (String[] s : splits) {
            int t = doc.accountTypeOf(s[0]);
            long v = valueToCents(s[1]);
            if (t != 15 && v > 0) {
                return v;
            }
        }
        return 0;
    }

    /**
     * Netto-Dividende = tatsächlich gutgeschriebenes Geld: der positive Split auf einem Geld-/Bankkonto
     * (kein Kategorie-Typ 12/13, keine Aktie 15, kein Eigenkapital 16). Fehlt einer, wird Brutto genutzt.
     */
    private long dividendNet(List<String[]> splits) {
        for (String[] s : splits) {
            int t = doc.accountTypeOf(s[0]);
            long v = valueToCents(s[1]);
            if (t != 12 && t != 13 && t != 15 && t != 16 && v > 0) {
                return v;
            }
        }
        return 0;
    }

    private static String normalizeAction(String a) {
        String x = a == null ? "" : a.trim().toLowerCase(Locale.US);
        switch (x) {
            case "buy":
            case "sell":
            case "dividend":
            case "add":
            case "remove":
            case "reinvest":
                return x;
            default:
                return x.isEmpty() ? "buy" : x;
        }
    }

    /** Währungskennzeichen des Kontos aus der KMyMoney-Datei (z. B. „EUR"), sonst leer. */
    public String currencyOf(String accountName) {
        return doc.currencyOfAccount(accountName);
    }

    /** KMyMoney-Kontotyp (für die Trennung Anlage/Verbindlichkeit); 0 wenn unbekannt. */
    public int accountType(String accountName) {
        String id = doc.accountId(accountName);
        return id == null ? 0 : doc.accountTypeOf(id);
    }

    /** Kontoname → KMyMoney-Typ für alle wählbaren Konten (zum Klassifizieren aller App-Konten beim Import). */
    public java.util.Map<String, Integer> accountTypes() {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (String name : doc.accountNames()) {
            out.put(name, accountType(name));
        }
        return out;
    }

    /**
     * Kategorie-Pfad → Typ ({@code true} = Einnahme, {@code false} = Ausgabe) für alle Kategorien der
     * Datei (zum Klassifizieren aller Budget-Kategorien beim Import). Siehe {@code KmyDocument}.
     */
    public java.util.Map<String, Boolean> categoryTypes() {
        return doc.categoryTypesByPath();
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
            throw new IOException(ctx.getString(de.spahr.ausgaben.R.string.err_kmy_read), e);
        }
        return out;
    }

    private Booking toBooking(String accountId, String accountName, String postdate, String entrydate,
                              String txMemo, List<String[]> splits) {
        if (splits == null) {
            return null;
        }
        String[] own = null;
        for (String[] s : splits) {
            if (accountId.equals(s[0])) {
                own = s;
                break;
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
        b.note = !own[3].isEmpty() ? own[3] : txMemo;
        b.createdAt = parseDate(postdate, entrydate);
        b.exported = true;

        // Gegen-Splits klassifizieren: Kategorie (Typ 12/13) vs. Konto; Aktien/ETF (Typ 15) gesondert.
        List<String[]> categorySplits = new ArrayList<>();
        List<String[]> nonCatCounters = new ArrayList<>();
        boolean hasStock = false;
        for (String[] s : splits) {
            if (s == own) {
                continue;
            }
            int type = doc.accountTypeOf(s[0]);
            if (type == 15) {
                hasStock = true;
            }
            if (type == 12 || type == 13) {
                categorySplits.add(s);
            } else {
                nonCatCounters.add(s);
            }
        }

        // Umbuchung: genau ein Nicht-Kategorie-Gegenkonto, keine Kategorien, kein Aktien-/ETF-Split.
        if (!hasStock && categorySplits.isEmpty() && nonCatCounters.size() == 1) {
            String[] counterSplit = nonCatCounters.get(0);
            b.isTransfer = true;
            b.transferAccount = orEmpty(doc.accountNameById(counterSplit[0])).trim();
            b.category = "";
            // Empfänger der Umbuchung aus dem Payee-Attribut der Splits (kein Kontoname-Fallback).
            String payeeId = !own[2].isEmpty() ? own[2] : counterSplit[2];
            b.payee = payeeId.isEmpty() ? "" : orEmpty(doc.payeeName(payeeId));
            return b;
        }

        // Splitbuchung: mehrere Kategorie-Splits → Kategorie-Teile aufbauen (Umkehr des Exports).
        if (categorySplits.size() >= 2) {
            b.parts = new ArrayList<>();
            for (String[] cs : categorySplits) {
                long catValue = valueToCents(cs[1]);
                long partial = b.isIncome ? -catValue : catValue;
                b.parts.add(new BookingSplit(0, orEmpty(doc.categoryPath(cs[0])), partial));
            }
            b.category = b.parts.isEmpty() ? "" : b.parts.get(0).category;
            b.payee = resolveImportPayee(own, categorySplits.get(0), splits);
            return b;
        }

        // Einzelbuchung: Kategorie/Empfänger aus dem ersten Gegen-Split (bisheriges Verhalten).
        String[] counter = null;
        for (String[] s : splits) {
            if (s != own) {
                counter = s;
                break;
            }
        }
        b.category = counter == null ? "" : orEmpty(doc.categoryPath(counter[0]));
        b.payee = resolveImportPayee(own, counter, splits);
        return b;
    }

    /**
     * Empfänger einer importierten Buchung: aus dem eigenen bzw. Gegen-Split; fällt auf den Namen des
     * Aktien-/ETF-Kontos (Typ 15) bzw. eines Nicht-Kategorie-Gegenkontos zurück (statt „—").
     */
    private String resolveImportPayee(String[] own, String[] counter, List<String[]> splits) {
        String payeeId = !own[2].isEmpty() ? own[2] : (counter == null ? "" : counter[2]);
        String payee = payeeId.isEmpty() ? "" : orEmpty(doc.payeeName(payeeId));
        if (!payee.isEmpty()) {
            return payee;
        }
        // 1) Aktien-/ETF-Konto (Typ 15) – deckt Kauf, Kauf-mit-Gebühr und Dividende ab.
        for (String[] s : splits) {
            if (doc.accountTypeOf(s[0]) == 15) {
                String stockName = doc.accountNameById(s[0]);
                if (stockName != null && !stockName.trim().isEmpty()) {
                    return stockName.trim();
                }
            }
        }
        // 2) Nicht-Kategorie-Gegenkonto → dessen Name.
        if (counter != null) {
            int t = doc.accountTypeOf(counter[0]);
            if (t != 12 && t != 13) {
                return orEmpty(doc.accountNameById(counter[0])).trim();
            }
        }
        return "";
    }

    /**
     * Liest den (nach {@code <TRANSACTIONS>} stehenden) {@code <SCHEDULES>}-Block: je {@code <SCHEDULED_TX>}
     * die eingebettete Transaktion. Klassifikation (Einzahlung/Auszahlung/Umbuchung, Betrag, Empfänger,
     * Kategorie/Zielkonto) über die vorhandene {@link #toBooking}-Logik; nächste Fälligkeit = postdate.
     */
    public List<ScheduledTransaction> scheduledTransactions() throws IOException {
        List<ScheduledTransaction> out = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(doc.xml()));
            int event = parser.getEventType();
            boolean inSchedules = false;
            String schedId = null;
            String schedName = null;
            String schedEnd = "";
            int schedOcc = 0;
            int schedMult = 1;
            String postdate = null;
            String entrydate = null;
            String txMemo = "";
            List<String[]> splits = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("SCHEDULES".equals(tag)) {
                        inSchedules = true;
                    } else if (inSchedules && "SCHEDULED_TX".equals(tag)) {
                        schedId = orEmpty(parser.getAttributeValue(null, "id"));
                        schedName = orEmpty(parser.getAttributeValue(null, "name"));
                        schedEnd = orEmpty(parser.getAttributeValue(null, "endDate"));
                        schedOcc = parseIntOr(parser.getAttributeValue(null, "occurence"), 0);
                        schedMult = parseIntOr(parser.getAttributeValue(null, "occurenceMultiplier"), 1);
                        postdate = null;
                        entrydate = null;
                        txMemo = "";
                        splits = null;
                    } else if (inSchedules && "TRANSACTION".equals(tag)) {
                        postdate = parser.getAttributeValue(null, "postdate");
                        entrydate = parser.getAttributeValue(null, "entrydate");
                        txMemo = orEmpty(parser.getAttributeValue(null, "memo"));
                        splits = new ArrayList<>();
                    } else if (inSchedules && "SPLIT".equals(tag) && splits != null) {
                        splits.add(new String[]{
                                orEmpty(parser.getAttributeValue(null, "account")),
                                orEmpty(parser.getAttributeValue(null, "value")),
                                orEmpty(parser.getAttributeValue(null, "payee")),
                                orEmpty(parser.getAttributeValue(null, "memo"))});
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("SCHEDULES".equals(tag)) {
                        break; // Block vollständig
                    } else if (inSchedules && "SCHEDULED_TX".equals(tag)) {
                        ScheduledTransaction st = buildScheduled(schedId, schedName, schedEnd, schedOcc,
                                schedMult, postdate, entrydate, txMemo, splits);
                        if (st != null) {
                            out.add(st);
                        }
                        schedId = null;
                        schedName = null;
                        splits = null;
                    }
                }
                event = parser.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException(ctx.getString(de.spahr.ausgaben.R.string.err_kmy_read), e);
        }
        return out;
    }

    /**
     * Baut aus der Transaktion einer geplanten Buchung den DB-Eintrag (Wiederverwendung von toBooking).
     * Nur <b>aktive</b> Planungen: mit gesetztem {@code endDate} in der Vergangenheit werden übersprungen.
     */
    private ScheduledTransaction buildScheduled(String schedId, String name, String endDate,
                                                int occurrence, int occurrenceMultiplier, String postdate,
                                                String entrydate, String txMemo, List<String[]> splits) {
        if (splits == null || splits.isEmpty()) {
            return null;
        }
        // Nur aktive Planungen: endDate gesetzt und vor heute → abgelaufen, überspringen.
        long endMs = de.spahr.ausgaben.export.KmyDocument.parseKmyDate(endDate);
        if (endMs >= 0 && endMs < System.currentTimeMillis()) {
            return null;
        }
        // Primäres Konto: erster Split auf einem echten Konto (nicht Kategorie 12/13, nicht Aktie 15).
        String primaryId = null;
        for (String[] s : splits) {
            int t = doc.accountTypeOf(s[0]);
            if (t != 12 && t != 13 && t != 15) {
                primaryId = s[0];
                break;
            }
        }
        if (primaryId == null) {
            return null; // kein Kontobezug
        }
        String primaryName = orEmpty(doc.accountNameById(primaryId)).trim();
        Booking b = toBooking(primaryId, primaryName, postdate, entrydate, txMemo, splits);
        if (b == null) {
            return null;
        }
        int kind = b.isTransfer ? ScheduledTransaction.KIND_TRANSFER
                : (b.isIncome ? ScheduledTransaction.KIND_INCOME : ScheduledTransaction.KIND_EXPENSE);
        String counterparty = b.isTransfer ? orEmpty(b.transferAccount) : orEmpty(b.category);
        return new ScheduledTransaction(orEmpty(schedId), orEmpty(name).trim(), kind, b.createdAt,
                b.amountCents, orEmpty(b.payee), primaryName, counterparty,
                occurrence, occurrenceMultiplier <= 0 ? 1 : occurrenceMultiplier,
                endMs < 0 ? 0 : endMs);
    }

    private static int parseIntOr(String s, int fallback) {
        if (s == null || s.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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
