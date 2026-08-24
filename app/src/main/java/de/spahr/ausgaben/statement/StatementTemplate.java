package de.spahr.ausgaben.statement;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Was die App über die Abrechnungen <b>einer</b> Bank für <b>eine</b> Bewegungsart gelernt hat: je Feld
 * eine {@link AnchorRule}, dazu die Aktion, die zu solchen Dokumenten gehört.
 *
 * <p>Eine Vorlage erkennt sich an ihren eigenen Ankertexten wieder — kommen alle im Dokument vor, ist es
 * eine Abrechnung dieser Bank und dieser Art. Eigene Erkennungsmerkmale braucht es dafür nicht: die
 * Wortwahl der Beschriftungen ist ohnehin bankspezifisch.</p>
 */
public final class StatementTemplate {

    /** Die Felder, die ausgelesen werden. Der Bruttobetrag fehlt bewusst — er wird gerechnet. */
    public enum Field {
        /** Stückzahl. */
        SHARES,
        /** Stückpreis; bei Dividenden nicht ausgelesen (dort steht er in Fremdwährung). */
        PRICE,
        /** Gebühr bzw. Steuer, oft die Summe mehrerer Zeilen. */
        FEE,
        /** Gesamtsumme bzw. Nettogutschrift — der Wert, der aufs Konto geht. */
        NET,
        /** Das gebuchte Datum unter den mehreren, die eine Abrechnung trägt. */
        DATE
    }

    /** Eine der Aktionen {@code buy}, {@code sell}, {@code dividend}. */
    public final String action;
    private final Map<Field, AnchorRule> rules;

    public StatementTemplate(String action, Map<Field, AnchorRule> rules) {
        this.action = action;
        this.rules = new EnumMap<>(Field.class);
        this.rules.putAll(rules);
    }

    public Map<Field, AnchorRule> rules() {
        return new LinkedHashMap<>(rules);
    }

    public AnchorRule rule(Field field) {
        return rules.get(field);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Ob diese Vorlage zum Dokument passt: <b>jeder</b> Ankertext muss vorkommen. Fehlt einer, ist es
     * eine andere Bank oder eine andere Art von Abrechnung — dann lieber nichts vorbelegen.
     */
    public boolean matches(PdfText text) {
        if (text == null || rules.isEmpty()) {
            return false;
        }
        String all = text.text().toLowerCase(java.util.Locale.ROOT);
        for (AnchorRule rule : rules.values()) {
            for (String anchor : rule.anchors) {
                if (!all.contains(anchor.toLowerCase(java.util.Locale.ROOT))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Liest die Werte aus dem Dokument. Was keine Regel trifft, bleibt leer — <b>geraten wird nichts</b>:
     * ein falsch vorbelegter Betrag, den man übersieht, ist schlimmer als ein leeres Feld.
     */
    public Extraction apply(PdfText text) {
        Extraction e = new Extraction();
        e.action = action;
        e.isin = StatementScan.isin(text);
        AnchorRule shares = rules.get(Field.SHARES);
        AnchorRule price = rules.get(Field.PRICE);
        AnchorRule fee = rules.get(Field.FEE);
        AnchorRule net = rules.get(Field.NET);
        AnchorRule date = rules.get(Field.DATE);
        e.shares = shares == null ? null : shares.read(text);
        e.price = price == null ? null : price.read(text);
        e.feeCents = fee == null ? null : fee.readCents(text);
        e.netCents = net == null ? null : net.readCents(text);
        e.dateMillis = date == null ? -1 : date.readDate(text);
        return e;
    }

    /** Das Ergebnis einer Auslese. Nicht Erkanntes ist {@code null} bzw. -1. */
    public static final class Extraction {
        public String action;
        public String isin;
        public long dateMillis = -1;
        public Double shares;
        public Double price;
        public Long feeCents;
        public Long netCents;

        /** Ob überhaupt etwas herauskam, das die Maske vorbelegen kann. */
        public boolean hasAnything() {
            return isin != null || dateMillis > 0 || shares != null || price != null
                    || feeCents != null || netCents != null;
        }
    }
}
