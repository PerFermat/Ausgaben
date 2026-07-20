package de.spahr.ausgaben.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.BookingSplit;
import de.spahr.ausgaben.db.KmyPendingDelete;

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

    /** Ergebnis des Entfernens bereits vorhandener, aber lokal gelöschter Transaktionen. */
    public static class DeleteResult {
        public String xml;
        /** In dieser Runde tatsächlich gefundene und entfernte Vormerkungen (per {@link KmyPendingDelete#id}). */
        public final List<Long> resolvedIds = new ArrayList<>();
    }

    private final KmyDocument doc;
    private final android.content.Context ctx;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public KmyExporter(KmyDocument doc, android.content.Context context) {
        this.doc = doc;
        this.ctx = de.spahr.ausgaben.i18n.LocaleManager.localizedContext(context);
    }

    public Result build(List<Booking> bookings) {
        return build(bookings, new HashMap<>());
    }

    public Result build(List<Booking> bookings, Map<Long, List<BookingSplit>> splitsMap) {
        Result result = new Result();
        long[] nextTx = {doc.maxTransactionNumber() + 1};
        int[] nextPayee = {doc.maxPayeeNumber() + 1};

        // In diesem Lauf neu angelegte Empfänger: kleingeschriebener Name → id (zur Wiederverwendung).
        Map<String, String> newPayeeIds = new HashMap<>();
        StringBuilder txFragments = new StringBuilder();
        StringBuilder payeeFragments = new StringBuilder();
        // Bereits geschriebene Umbuchungs-Gruppen (die zweite Seite nur als „exportiert" markieren).
        Set<String> doneTransferGroups = new HashSet<>();
        String today = dateFormat.format(new Date());

        for (Booking b : bookings) {
            if (b.isTransfer) {
                writeTransfer(b, result, txFragments, nextTx, today, doneTransferGroups,
                        newPayeeIds, payeeFragments, nextPayee);
                continue;
            }
            String assetId = doc.accountId(b.account);
            if (assetId == null) {
                result.skipped.add(label(b) + ": "
                        + ctx.getString(de.spahr.ausgaben.R.string.skip_account_not_found, b.account));
                continue;
            }

            String payeeId = resolvePayee(b.payee, result, newPayeeIds, payeeFragments, nextPayee);
            long signedCents = b.isIncome ? b.amountCents : -b.amountCents;
            String memo = b.note == null ? "" : b.note;

            // Splitbuchung (≥2 Kategorien): Konto-Split + je Teil ein Kategorie-Split (Gegen-Vorzeichen).
            List<BookingSplit> parts = splitsMap.get(b.id);
            if (parts != null && parts.size() >= 2) {
                List<String> splitXmls = buildSplitParts(b, assetId, payeeId, signedCents, memo, parts, result);
                if (splitXmls == null) {
                    continue; // eine Kategorie unbekannt → Buchung übersprungen (in result.skipped vermerkt)
                }
                String txId = String.format(Locale.US, "T%018d", nextTx[0]++);
                txFragments.append(transactionElement(txId, dateFor(b.createdAt), today, memo, splitXmls));
                result.writtenIds.add(b.id);
                continue;
            }

            // Einzel-/Ohne-Kategorie: leere Kategorie erlaubt (nicht zugeordnet); unbekannte Kategorie → Skip.
            String cat = b.category == null ? "" : b.category.trim();
            String categoryId = null;
            if (!cat.isEmpty()) {
                categoryId = doc.categoryId(cat);
                if (categoryId == null) {
                    result.skipped.add(label(b) + ": "
                        + ctx.getString(de.spahr.ausgaben.R.string.skip_category_not_found, cat));
                    continue;
                }
            }
            List<String> splitXmls = new ArrayList<>();
            splitXmls.add(split("S0001", assetId, payeeId, fraction(signedCents), esc(memo)));
            if (categoryId != null && !categoryId.isEmpty()) {
                splitXmls.add(split("S0002", categoryId, payeeId, fraction(-signedCents), esc(memo)));
            }
            String txId = String.format(Locale.US, "T%018d", nextTx[0]++);
            txFragments.append(transactionElement(txId, dateFor(b.createdAt), today, memo, splitXmls));
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

    /**
     * Entfernt Transaktionen, die einer lokal gelöschten Buchung entsprechen, aus der XML – für die
     * Buchungs-Lösch-Synchronisierung im kmy-Modus. KMyMoney-Transaktionen tragen aus App-Sicht keine
     * bekannte id (weder von der App selbst importierte noch am Rechner angelegte Buchungen werden bisher
     * mit ihrer Transaktions-id verknüpft), daher wird stattdessen über den Inhalt gesucht: Konto + Datum
     * (auf den Tag genau) + der vorzeichenbehaftete Betrag des Kontosplits müssen zu {@link KmyPendingDelete}
     * passen. Trifft eine Vormerkung auf mehrere gleichartige Transaktionen (z. B. zwei identische Beträge
     * am selben Tag), wird nur die erste noch nicht verbrauchte entfernt – ein bekanntes, seltenes Risiko.
     */
    public DeleteResult removeTransactions(String xml, List<KmyPendingDelete> deletes) {
        DeleteResult result = new DeleteResult();
        if (deletes == null || deletes.isEmpty()) {
            result.xml = xml;
            return result;
        }
        // Je Vormerkung: Konto-id + Datum + Betrag, sofern das Konto (noch) existiert.
        List<String> sigAccountId = new ArrayList<>();
        List<String> sigDate = new ArrayList<>();
        List<Long> sigCents = new ArrayList<>();
        List<Long> sigDeleteId = new ArrayList<>();
        for (KmyPendingDelete d : deletes) {
            String assetId = doc.accountId(d.account);
            if (assetId == null) {
                continue; // Konto nicht mehr vorhanden → nichts zuzuordnen
            }
            sigAccountId.add(assetId);
            sigDate.add(dateFor(d.createdAt));
            sigCents.add(d.signedCents);
            sigDeleteId.add(d.id);
        }
        if (sigAccountId.isEmpty()) {
            result.xml = xml;
            return result;
        }
        boolean[] consumed = new boolean[sigAccountId.size()];

        Pattern block = Pattern.compile("<TRANSACTION\\b.*?</TRANSACTION>", Pattern.DOTALL);
        Pattern postdateAttr = Pattern.compile("\\bpostdate=\"([^\"]*)\"");
        Pattern splitTag = Pattern.compile("<SPLIT\\b[^>]*/>");
        Pattern accountAttr = Pattern.compile("\\baccount=\"([^\"]*)\"");
        Pattern valueAttr = Pattern.compile("\\bvalue=\"([^\"]*)\"");

        Matcher m = block.matcher(xml);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        int removed = 0;
        while (m.find()) {
            String tx = m.group();
            Matcher dm = postdateAttr.matcher(tx);
            // postdate steht im öffnenden <TRANSACTION …> vor den Splits – erster Treffer genügt.
            String date = dm.find() ? dm.group(1) : null;
            int matchIdx = -1;
            if (date != null) {
                Matcher sm = splitTag.matcher(tx);
                while (matchIdx < 0 && sm.find()) {
                    String splitTagXml = sm.group();
                    Matcher am = accountAttr.matcher(splitTagXml);
                    Matcher vm = valueAttr.matcher(splitTagXml);
                    if (!am.find() || !vm.find()) {
                        continue;
                    }
                    String acc = am.group(1);
                    long cents = valueToCents(vm.group(1));
                    for (int i = 0; i < sigAccountId.size(); i++) {
                        if (!consumed[i] && sigDate.get(i).equals(date) && sigAccountId.get(i).equals(acc)
                                && sigCents.get(i) == cents) {
                            matchIdx = i;
                            break;
                        }
                    }
                }
            }
            if (matchIdx >= 0) {
                consumed[matchIdx] = true;
                result.resolvedIds.add(sigDeleteId.get(matchIdx));
                sb.append(xml, last, m.start());
                last = m.end();
                removed++;
            }
        }
        sb.append(xml.substring(last));
        String out = sb.toString();
        if (removed > 0) {
            out = bumpCount(out, "TRANSACTIONS", -removed);
        }
        result.xml = out;
        return result;
    }

    /** KMyMoney-Betrag „num/den" → Cent (gerundet, mit Vorzeichen); wie {@code KmyImporter.valueToCents}. */
    private static long valueToCents(String value) {
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

    // ---- XML-Bausteine ----

    /** Umbuchung: eine Transaktion mit zwei Konto-Splits (Quelle −, Ziel +), keine Kategorie; mit Empfänger. */
    private void writeTransfer(Booking b, Result result, StringBuilder txFragments, long[] nextTx,
                               String today, Set<String> doneTransferGroups,
                               Map<String, String> newPayeeIds, StringBuilder payeeFragments,
                               int[] nextPayee) {
        String group = b.transferGroup == null ? "" : b.transferGroup;
        if (!group.isEmpty() && doneTransferGroups.contains(group)) {
            result.writtenIds.add(b.id); // zweite Seite: nur als exportiert markieren, nicht erneut schreiben
            return;
        }
        // Aus der Sicht dieser Zeile Quelle/Ziel bestimmen (Einnahme = Geld kam auf dieses Konto).
        String fromAccount = b.isIncome ? b.transferAccount : b.account;
        String toAccount = b.isIncome ? b.account : b.transferAccount;
        String fromId = doc.accountId(fromAccount);
        String toId = doc.accountId(toAccount);
        if (fromId == null || toId == null) {
            String missing = fromId == null ? fromAccount : toAccount;
            result.skipped.add(ctx.getString(
                    de.spahr.ausgaben.R.string.skip_transfer_account_not_found,
                    fromAccount, toAccount, missing));
            return;
        }
        String payeeId = resolvePayee(b.payee, result, newPayeeIds, payeeFragments, nextPayee);
        String memo = b.note == null ? "" : b.note;
        List<String> splitXmls = new ArrayList<>();
        splitXmls.add(split("S0001", fromId, payeeId, fraction(-b.amountCents), esc(memo)));
        splitXmls.add(split("S0002", toId, payeeId, fraction(b.amountCents), esc(memo)));
        String txId = String.format(Locale.US, "T%018d", nextTx[0]++);
        txFragments.append(transactionElement(txId, dateFor(b.createdAt), today, memo, splitXmls));
        result.writtenIds.add(b.id);
        if (!group.isEmpty()) {
            doneTransferGroups.add(group);
        }
    }

    /**
     * Baut die Kategorie-Splits einer Splitbuchung: Konto-Split (signierter Gesamtbetrag) + je Teil ein
     * Kategorie-Split mit Gegen-Vorzeichen. Gibt {@code null} zurück, wenn eine Kategorie unbekannt ist.
     */
    private List<String> buildSplitParts(Booking b, String assetId, String payeeId, long signedCents,
                                         String memo, List<BookingSplit> parts, Result result) {
        List<String> splitXmls = new ArrayList<>();
        splitXmls.add(split("S0001", assetId, payeeId, fraction(signedCents), esc(memo)));
        int idx = 2;
        for (BookingSplit p : parts) {
            String cat = p.category == null ? "" : p.category.trim();
            String categoryId = doc.categoryId(cat);
            if (categoryId == null) {
                result.skipped.add(label(b) + ": "
                        + ctx.getString(de.spahr.ausgaben.R.string.skip_category_not_found, cat));
                return null;
            }
            // App-Teilbetrag (in Gesamt-Einheiten) → Kategorie-Split mit Gegen-Vorzeichen zum Konto-Split.
            long catValue = b.isIncome ? -p.amountCents : p.amountCents;
            splitXmls.add(split(String.format(Locale.US, "S%04d", idx++), categoryId, payeeId,
                    fraction(catValue), esc(memo)));
        }
        return splitXmls;
    }

    /** Löst einen Empfänger auf (bekannt/schon angelegt) oder legt ihn neu an; liefert die Payee-id ("" = keiner). */
    private String resolvePayee(String rawPayee, Result result, Map<String, String> newPayeeIds,
                                StringBuilder payeeFragments, int[] nextPayee) {
        String payee = rawPayee == null ? "" : rawPayee.trim();
        if (payee.isEmpty()) {
            return "";
        }
        String existing = doc.payeeId(payee);
        if (existing == null) {
            existing = newPayeeIds.get(payee.toLowerCase(Locale.GERMANY));
        }
        if (existing == null) {
            existing = String.format(Locale.US, "P%06d", nextPayee[0]++);
            newPayeeIds.put(payee.toLowerCase(Locale.GERMANY), existing);
            payeeFragments.append(payeeElement(existing, payee));
            result.newPayees++;
        }
        return existing;
    }

    private String transactionElement(String txId, String postdate, String entrydate, String memo,
                                      List<String> splitXmls) {
        String m = esc(memo);
        StringBuilder splits = new StringBuilder();
        for (String s : splitXmls) {
            splits.append(s);
        }
        return "<TRANSACTION postdate=\"" + postdate + "\" entrydate=\"" + entrydate + "\" memo=\"" + m
                + "\" id=\"" + txId + "\" commodity=\"EUR\">"
                + "<SPLITS>"
                + splits
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
        String p = b.payee == null || b.payee.trim().isEmpty()
                ? ctx.getString(de.spahr.ausgaben.R.string.no_payee) : b.payee.trim();
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
