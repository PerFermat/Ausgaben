package de.spahr.ausgaben.export;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.spahr.ausgaben.db.Booking;

/**
 * Fügt App-Buchungen als KMyMoney-Transaktionen in die XML-Struktur einer {@link KmyDocument} ein.
 * Konto und Kategorie werden per Namensabgleich aufgelöst (nicht gefunden → übersprungen); ein noch
 * unbekannter, nicht-leerer Empfänger wird als neuer {@code <PAYEE>} angelegt.
 */
public class KmyExporter {

    /** Ergebnis eines Export-Laufs. */
    public static class Result {
        public String xml;
        public final List<Long> writtenIds = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
        public int newPayees;
    }

    private final KmyDocument doc;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public KmyExporter(KmyDocument doc) {
        this.doc = doc;
    }

    public Result build(List<Booking> bookings) {
        Result result = new Result();
        long nextTx = doc.maxTransactionNumber() + 1;
        int nextPayee = doc.maxPayeeNumber() + 1;

        // In diesem Lauf neu angelegte Empfänger: kleingeschriebener Name → id (zur Wiederverwendung).
        Map<String, String> newPayeeIds = new HashMap<>();
        StringBuilder txFragments = new StringBuilder();
        StringBuilder payeeFragments = new StringBuilder();
        String today = dateFormat.format(new Date());

        for (Booking b : bookings) {
            String assetId = doc.accountId(b.account);
            if (assetId == null) {
                result.skipped.add(label(b) + ": Konto nicht gefunden (" + b.account + ")");
                continue;
            }
            String categoryId = b.category == null ? null : doc.categoryId(b.category);
            if (categoryId == null) {
                String cat = b.category == null || b.category.trim().isEmpty() ? "ohne" : b.category;
                result.skipped.add(label(b) + ": Kategorie nicht gefunden (" + cat + ")");
                continue;
            }

            String payeeId = "";
            String payee = b.payee == null ? "" : b.payee.trim();
            if (!payee.isEmpty()) {
                String existing = doc.payeeId(payee);
                if (existing == null) {
                    existing = newPayeeIds.get(payee.toLowerCase(Locale.GERMANY));
                }
                if (existing == null) {
                    existing = String.format(Locale.US, "P%06d", nextPayee++);
                    newPayeeIds.put(payee.toLowerCase(Locale.GERMANY), existing);
                    payeeFragments.append(payeeElement(existing, payee));
                    result.newPayees++;
                }
                payeeId = existing;
            }

            String txId = String.format(Locale.US, "T%018d", nextTx++);
            long signedCents = b.isIncome ? b.amountCents : -b.amountCents;
            txFragments.append(transactionElement(txId, dateFor(b.createdAt), today,
                    assetId, categoryId, payeeId, signedCents, b.note == null ? "" : b.note));
            result.writtenIds.add(b.id);
        }

        String xml = doc.xml();
        if (result.writtenIds.size() > 0) {
            xml = insertBefore(xml, "</TRANSACTIONS>", txFragments.toString());
            xml = bumpCount(xml, "TRANSACTIONS", result.writtenIds.size());
        }
        if (result.newPayees > 0) {
            xml = insertBefore(xml, "</PAYEES>", payeeFragments.toString());
            xml = bumpCount(xml, "PAYEES", result.newPayees);
        }
        xml = updateLastModified(xml, today);
        result.xml = xml;
        return result;
    }

    // ---- XML-Bausteine ----

    private String transactionElement(String txId, String postdate, String entrydate,
                                      String assetId, String categoryId, String payeeId,
                                      long signedCents, String memo) {
        String m = esc(memo);
        String cash = fraction(signedCents);
        String cat = fraction(-signedCents);
        return "<TRANSACTION postdate=\"" + postdate + "\" entrydate=\"" + entrydate + "\" memo=\"" + m
                + "\" id=\"" + txId + "\" commodity=\"EUR\">"
                + "<SPLITS>"
                + split("S0001", assetId, payeeId, cash, m)
                + split("S0002", categoryId, payeeId, cat, m)
                + "</SPLITS>"
                + "</TRANSACTION>";
    }

    private String split(String id, String accountId, String payeeId, String value, String memo) {
        return "<SPLIT reconcileflag=\"0\" payee=\"" + esc(payeeId) + "\" number=\"\" bankid=\"\" memo=\""
                + memo + "\" value=\"" + value + "\" reconciledate=\"\" account=\"" + esc(accountId)
                + "\" id=\"" + id + "\" price=\"1/1\" shares=\"" + value + "\" action=\"\"/>";
    }

    private String payeeElement(String id, String name) {
        return "<PAYEE reference=\"\" matchignorecase=\"1\" email=\"\" matchingenabled=\"1\" name=\""
                + esc(name) + "\" id=\"" + id + "\" matchkey=\"\" usingmatchkey=\"0\">"
                + "<ADDRESS city=\"\" street=\"\" telephone=\"\" postcode=\"\" state=\"\"/>"
                + "</PAYEE>";
    }

    private String fraction(long signedCents) {
        return signedCents + "/100";
    }

    private String dateFor(long millis) {
        return dateFormat.format(new Date(millis));
    }

    private String label(Booking b) {
        String p = b.payee == null || b.payee.trim().isEmpty() ? "(ohne Empfänger)" : b.payee.trim();
        return p;
    }

    // ---- String-Manipulation ----

    private static String insertBefore(String xml, String closeTag, String fragment) {
        int idx = xml.lastIndexOf(closeTag);
        if (idx < 0) {
            return xml;
        }
        return xml.substring(0, idx) + fragment + xml.substring(idx);
    }

    /** Erhöht das count-Attribut von {@code <TAG count="N" …>} um {@code delta}. */
    private static String bumpCount(String xml, String tag, int delta) {
        Pattern p = Pattern.compile("(<" + tag + " count=\")(\\d+)(\")");
        Matcher m = p.matcher(xml);
        if (m.find()) {
            long n = Long.parseLong(m.group(2)) + delta;
            return xml.substring(0, m.start()) + m.group(1) + n + m.group(3) + xml.substring(m.end());
        }
        return xml;
    }

    private static String updateLastModified(String xml, String today) {
        Pattern p = Pattern.compile("(<LAST_MODIFIED_DATE date=\")[^\"]*(\")");
        Matcher m = p.matcher(xml);
        if (m.find()) {
            return xml.substring(0, m.start()) + m.group(1) + today + m.group(2) + xml.substring(m.end());
        }
        return xml;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
