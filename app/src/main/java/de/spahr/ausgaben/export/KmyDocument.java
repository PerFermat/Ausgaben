package de.spahr.ausgaben.export;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Liest eine KMyMoney-Datei (gzip-XML oder reines XML) ein und baut daraus die Zuordnungen zwischen
 * den Freitext-Namen der App und den internen KMyMoney-IDs. Wird von {@link KmyExporter} und
 * {@link KmyImporter} gemeinsam genutzt.
 *
 * <p>Konten der Typen 12 (Einnahme) und 13 (Ausgabe) sind Kategorien; Typ 16 ist Eigenkapital.
 * Alle übrigen Konten (Bargeld/Bank/Vermögen …) gelten als wählbare „Konten".</p>
 */
public class KmyDocument {

    private static final int TYPE_INCOME = 12;
    private static final int TYPE_EXPENSE = 13;
    private static final int TYPE_EQUITY = 16;
    private static final int TYPE_INVESTMENT = 7; // Depot
    private static final int TYPE_STOCK = 15;     // Wertpapier im Depot

    private final String xml;

    /** id → einfacher (Blatt-)Kontoname, für alle Konten. */
    private final Map<String, String> accountName = new LinkedHashMap<>();
    /** id → parentaccount-id. */
    private final Map<String, String> accountParent = new LinkedHashMap<>();
    /** id → KMyMoney-Kontotyp. */
    private final Map<String, Integer> accountType = new LinkedHashMap<>();
    /** id → Währungskennzeichen (currency-Attribut, z. B. „EUR"). */
    private final Map<String, String> accountCurrency = new LinkedHashMap<>();

    /** Anzeigename → id der wählbaren Konten (Reihenfolge = Eingabereihenfolge). */
    private final Map<String, String> selectableAccounts = new LinkedHashMap<>();
    /** kleingeschriebener Kontoname → id (für den Export-Lookup). */
    private final Map<String, String> assetNameToId = new LinkedHashMap<>();
    /** Anzeigename → id der Depots (Investment-Konten, Typ 7). */
    private final Map<String, String> depotAccounts = new LinkedHashMap<>();

    /** Wertpapier-ID (E00000x) → Anzeigedaten aus dem SECURITY-Block. */
    private final Map<String, String[]> securityInfo = new LinkedHashMap<>(); // {name, symbol, currency}
    /** Wertpapier-ID → letzter Kurs {price, dateMillis}. */
    private final Map<String, double[]> securityPrice = new LinkedHashMap<>();

    /** kleingeschriebener Kategorie-Pfad (bzw. Blattname) → id. */
    private final Map<String, String> categoryToId = new LinkedHashMap<>();
    /** id → Kategorie-Pfad (für den Import). */
    private final Map<String, String> categoryIdToPath = new LinkedHashMap<>();

    private final Map<String, String> payeeNameToId = new LinkedHashMap<>();
    private final Map<String, String> payeeIdToName = new LinkedHashMap<>();

    private long maxTransactionNumber = 0;
    private int maxPayeeNumber = 0;

    private final android.content.Context ctx;

    public KmyDocument(byte[] raw, android.content.Context context) throws IOException {
        this.ctx = de.spahr.ausgaben.i18n.LocaleManager.localizedContext(context);
        this.xml = gunzip(raw);
        parseHeader();
        buildDerivedMaps();
        parseSecuritiesAndPrices();
        scanMaxNumbers();
    }

    // ---- Öffentliche Zugriffe ----

    public String xml() {
        return xml;
    }

    /** Wählbare Konten (Anzeigenamen) in Dateireihenfolge. */
    public List<String> accountNames() {
        return new ArrayList<>(selectableAccounts.keySet());
    }

    public String accountId(String name) {
        return name == null ? null : assetNameToId.get(name.trim().toLowerCase(Locale.GERMANY));
    }

    /** Währungskennzeichen des Kontos (z. B. „EUR") oder leer, wenn keins hinterlegt ist. */
    public String currencyOfAccount(String name) {
        String id = accountId(name);
        String c = id == null ? null : accountCurrency.get(id);
        return c == null ? "" : c;
    }

    /** Findet die Kategorie-id per vollem Pfad (bevorzugt) oder Blattnamen. */
    public String categoryId(String pathOrName) {
        return pathOrName == null ? null : categoryToId.get(pathOrName.trim().toLowerCase(Locale.GERMANY));
    }

    public String categoryPath(String id) {
        return categoryIdToPath.get(id);
    }

    public String payeeId(String name) {
        return name == null ? null : payeeNameToId.get(name.trim().toLowerCase(Locale.GERMANY));
    }

    public String payeeName(String id) {
        return payeeIdToName.get(id);
    }

    /** Anzeigename eines beliebigen Kontos (auch Depot/Aktie/ETF), für Investment-Umbuchungen. */
    public String accountNameById(String id) {
        return accountName.get(id);
    }

    /** KMyMoney-Kontotyp eines Kontos (15 = Aktie/ETF), sonst 0. */
    public int accountTypeOf(String id) {
        Integer t = accountType.get(id);
        return t == null ? 0 : t;
    }

    /** Übergeordnetes Konto (parentaccount) eines Kontos, sonst leer. */
    public String accountParentOf(String id) {
        String p = accountParent.get(id);
        return p == null ? "" : p;
    }

    /** Währungskennzeichen eines beliebigen Kontos (bei Typ 15 = Wertpapier-ID E00000x). */
    public String accountCurrencyOf(String id) {
        String c = accountCurrency.get(id);
        return c == null ? "" : c;
    }

    /** Anzeigenamen der Depots (Investment-Konten, Typ 7). */
    public List<String> depotNames() {
        return new ArrayList<>(depotAccounts.keySet());
    }

    public String depotId(String name) {
        return name == null ? null : depotAccounts.get(name);
    }

    /** Alle Konto-IDs (für den Import über Stock-Konten eines Depots). */
    public java.util.Set<String> allAccountIds() {
        return accountName.keySet();
    }

    /** SECURITY-Anzeigedaten {name, symbol, currency} zur Wertpapier-ID, oder {@code null}. */
    public String[] securityInfo(String kmyId) {
        return securityInfo.get(kmyId);
    }

    /** Letzter Kurs {price, dateMillis} zur Wertpapier-ID, oder {@code null}. */
    public double[] securityPrice(String kmyId) {
        return securityPrice.get(kmyId);
    }

    public long maxTransactionNumber() {
        return maxTransactionNumber;
    }

    public int maxPayeeNumber() {
        return maxPayeeNumber;
    }

    // ---- Parsing ----

    /** Liest PAYEE- und ACCOUNT-Einträge; bricht ab, sobald der TRANSACTIONS-Block beginnt. */
    private void parseHeader() throws IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("TRANSACTIONS".equals(tag)) {
                        break; // Kopfdaten vollständig
                    } else if ("PAYEE".equals(tag)) {
                        String id = parser.getAttributeValue(null, "id");
                        String name = parser.getAttributeValue(null, "name");
                        if (id != null && name != null) {
                            payeeNameToId.put(name.trim().toLowerCase(Locale.GERMANY), id);
                            payeeIdToName.put(id, name);
                        }
                    } else if ("ACCOUNT".equals(tag)) {
                        String id = parser.getAttributeValue(null, "id");
                        String name = parser.getAttributeValue(null, "name");
                        String parent = parser.getAttributeValue(null, "parentaccount");
                        String type = parser.getAttributeValue(null, "type");
                        String currency = parser.getAttributeValue(null, "currency");
                        if (id != null && name != null) {
                            accountName.put(id, name);
                            accountParent.put(id, parent == null ? "" : parent);
                            accountType.put(id, parseIntSafe(type));
                            accountCurrency.put(id, currency == null ? "" : currency.trim());
                        }
                    }
                }
                event = parser.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException(ctx.getString(de.spahr.ausgaben.R.string.err_kmy_read), e);
        }
    }

    /**
     * Liest den (nach TRANSACTIONS stehenden) SECURITIES- und PRICES-Block: Wertpapier-Stammdaten und je
     * Wertpapier den <b>letzten</b> Kurs in seiner Handelswährung.
     */
    private void parseSecuritiesAndPrices() throws IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new StringReader(xml));
            int event = parser.getEventType();
            String curFrom = null;
            String curTo = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("SECURITY".equals(tag)) {
                        String id = parser.getAttributeValue(null, "id");
                        if (id != null) {
                            securityInfo.put(id, new String[]{
                                    orEmpty(parser.getAttributeValue(null, "name")).trim(),
                                    orEmpty(parser.getAttributeValue(null, "symbol")).trim(),
                                    orEmpty(parser.getAttributeValue(null, "trading-currency")).trim()});
                        }
                    } else if ("PRICEPAIR".equals(tag)) {
                        curFrom = parser.getAttributeValue(null, "from");
                        curTo = parser.getAttributeValue(null, "to");
                    } else if ("PRICE".equals(tag) && curFrom != null) {
                        String[] info = securityInfo.get(curFrom);
                        String tc = info == null ? "" : info[2];
                        // Kurs in der Handelswährung des Wertpapiers (bei EUR-Papieren „to=EUR").
                        if (tc.isEmpty() || tc.equalsIgnoreCase(curTo)) {
                            long d = parseKmyDate(parser.getAttributeValue(null, "date"));
                            double p = fractionToDouble(parser.getAttributeValue(null, "price"));
                            double[] prev = securityPrice.get(curFrom);
                            if (d >= 0 && p > 0 && (prev == null || d >= (long) prev[1])) {
                                securityPrice.put(curFrom, new double[]{p, d});
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && "PRICEPAIR".equals(parser.getName())) {
                    curFrom = null;
                    curTo = null;
                }
                event = parser.next();
            }
        } catch (XmlPullParserException e) {
            throw new IOException(ctx.getString(de.spahr.ausgaben.R.string.err_kmy_read), e);
        }
    }

    /** KMyMoney-Bruch „num/den" (oder Dezimalzahl) → double. */
    static double fractionToDouble(String v) {
        if (v == null || v.trim().isEmpty()) {
            return 0;
        }
        try {
            int slash = v.indexOf('/');
            if (slash < 0) {
                return Double.parseDouble(v.trim());
            }
            double num = Double.parseDouble(v.substring(0, slash).trim());
            double den = Double.parseDouble(v.substring(slash + 1).trim());
            return den == 0 ? 0 : num / den;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** „yyyy-MM-dd" → ms, sonst -1. */
    static long parseKmyDate(String s) {
        if (s == null || s.trim().isEmpty()) {
            return -1;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s.trim()).getTime();
        } catch (ParseException e) {
            return -1;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private void buildDerivedMaps() {
        for (Map.Entry<String, String> e : accountName.entrySet()) {
            String id = e.getKey();
            String name = e.getValue();
            int type = accountType.get(id) == null ? 0 : accountType.get(id);
            if (id.startsWith("AStd::")) {
                continue; // Standard-Wurzelkonten (id AStd::Asset/Expense/…) überspringen
            }
            if (type == TYPE_EXPENSE || type == TYPE_INCOME) {
                String path = buildPath(id);
                categoryToId.put(path.toLowerCase(Locale.GERMANY), id);
                categoryToId.put(name.trim().toLowerCase(Locale.GERMANY), id); // Blatt-Fallback
                categoryIdToPath.put(id, path);
            } else if (type == TYPE_INVESTMENT) {
                depotAccounts.put(name, id); // Depot – eigener Import-Pfad (Wertpapiere)
            } else if (type != TYPE_EQUITY && type != TYPE_STOCK) {
                // Wertpapier-Unterkonten (Typ 15) NICHT als wählbare Konten führen.
                selectableAccounts.put(name, id);
                assetNameToId.put(name.trim().toLowerCase(Locale.GERMANY), id);
            }
        }
    }

    /** Baut den Pfad „Haupt:Unter" durch Hochlaufen der parentaccount-Kette (ohne AStd::-Wurzel). */
    private String buildPath(String id) {
        List<String> parts = new ArrayList<>();
        String cur = id;
        int guard = 0;
        while (cur != null && !cur.isEmpty() && !cur.startsWith("AStd::") && guard++ < 64) {
            String name = accountName.get(cur);
            if (name == null) {
                break;
            }
            parts.add(0, name);
            cur = accountParent.get(cur);
        }
        return android.text.TextUtils.join(":", parts);
    }

    /** Höchste vorhandene Transaktions- und Payee-Nummer per Regex über das ganze Dokument. */
    private void scanMaxNumbers() {
        Matcher tm = Pattern.compile("id=\"T(\\d+)\"").matcher(xml);
        while (tm.find()) {
            long n = Long.parseLong(tm.group(1));
            if (n > maxTransactionNumber) {
                maxTransactionNumber = n;
            }
        }
        Matcher pm = Pattern.compile("id=\"P(\\d+)\"").matcher(xml);
        while (pm.find()) {
            int n = Integer.parseInt(pm.group(1));
            if (n > maxPayeeNumber) {
                maxPayeeNumber = n;
            }
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- Gzip-Helfer ----

    /** Entpackt gzip (Magic 1f 8b), sonst als UTF-8-Text interpretiert. */
    public static String gunzip(byte[] raw) throws IOException {
        if (raw == null || raw.length == 0) {
            return "";
        }
        boolean gz = raw.length >= 2 && (raw[0] & 0xFF) == 0x1f && (raw[1] & 0xFF) == 0x8b;
        if (!gz) {
            return new String(raw, StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length * 4));
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Packt XML als gzip (bleibt eine normale .kmy). */
    public static byte[] gzip(String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
