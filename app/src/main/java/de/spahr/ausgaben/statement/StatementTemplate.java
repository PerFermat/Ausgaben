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

    /** Die Felder, die ausgelesen werden. */
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
        DATE,
        /**
         * Der Bruttobetrag in Kontowährung. Der Lerner legt dafür <b>keine</b> Regel an — das Brutto wird
         * in der Maske gerechnet und nicht eingetippt, eine daran geratene Beschriftung träfe irgendeine
         * Zeile mit derselben Zahl.
         *
         * <p>Von Hand ist das Feld dagegen nützlich: bei einem dollarnotierten Papier steht der gebuchte
         * Betrag in der Umrechnungszeile, bei einem euronotierten in der Bruttozeile. Als Kette
         * {@code [Umg. z. Dev.-Kurs, Brutto]} mit Währung EUR liest dieselbe Vorlage beide.</p>
         */
        GROSS
    }

    /** Eine der Aktionen {@code buy}, {@code sell}, {@code dividend}. */
    public final String action;
    private final Map<Field, AnchorRule> rules;

    /**
     * Eine Gebühr, die die Bank <b>nicht</b> ausdruckt — 0, wenn es keine gibt.
     *
     * <p>Scalable Capital nimmt je Order einen festen Betrag, der in der Abrechnung nirgends steht. Eine
     * Ankerregel kann ihn nicht finden, denn sie sucht nach einer Beschriftung; deshalb steht er hier an
     * der Vorlage und nicht in einer {@link AnchorRule}.</p>
     */
    public final long fixedFeeCents;
    /** Kategorie, unter der die feste Gebühr gebucht wird. */
    public final String fixedFeeCategory;
    /**
     * Ob die feste Gebühr im ausgedruckten Gesamtbetrag schon steckt.
     *
     * <p>Steckt sie nicht darin, ist der abgebuchte Betrag um sie höher als die Zahl auf dem Beleg — und
     * dann muss sie dort nachgetragen werden, sonst stimmte der Kontostand nicht.</p>
     */
    public final boolean fixedFeeInTotal;

    public StatementTemplate(String action, Map<Field, AnchorRule> rules) {
        this(action, rules, 0L, "", false);
    }

    public StatementTemplate(String action, Map<Field, AnchorRule> rules, long fixedFeeCents,
                             String fixedFeeCategory, boolean fixedFeeInTotal) {
        this.action = action;
        this.rules = new EnumMap<>(Field.class);
        this.rules.putAll(rules);
        this.fixedFeeCents = Math.max(0, fixedFeeCents);
        this.fixedFeeCategory = fixedFeeCategory == null ? "" : fixedFeeCategory.trim();
        this.fixedFeeInTotal = fixedFeeInTotal;
    }

    /** Dieselbe Vorlage mit anderer fester Gebühr – für die Regelseite. */
    public StatementTemplate withFixedFee(long cents, String category, boolean inTotal) {
        return new StatementTemplate(action, rules, cents, category, inTotal);
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

    /** Ob diese Vorlage zum Dokument gehört (siehe {@link #score}). */
    public boolean matches(PdfText text) {
        return score(text) > 0;
    }

    /**
     * Wie gut die Vorlage zum Dokument passt: die Zahl der Beschriftungen, die darin eine Zeile anführen.
     * {@code 0} heißt „gehört nicht dazu".
     *
     * <p>Erkannt wird die Abrechnung an der <b>Gesamtsumme</b>: fehlt deren Beschriftung, ist es eine
     * andere Bank oder eine andere Art — „Endbetrag zu Ihren Lasten" gegen „Gesamtbetrag zu Ihren
     * Gunsten" unterscheidet beides in einem. Alles Übrige darf fehlen.</p>
     *
     * <p>Vollständigkeit zu verlangen wäre falsch, und zwar still: eine Bank lässt die Valuta-Zeile weg,
     * wenn sie mit dem Zahltag zusammenfällt, und den Solidaritätszuschlag, wenn keiner anfällt. Bestünde
     * die Vorlage auf jeder Zeile, fiele sie bei solchen Abrechnungen ganz aus — und übernommen würde
     * <b>nichts</b>, obwohl Betrag, Stückzahl und Kurs sauber dastehen. Jedes Feld wird deshalb für sich
     * gesucht; was fehlt, bleibt leer.</p>
     */
    public int score(PdfText text) {
        if (text == null || rules.isEmpty()) {
            return 0;
        }
        AnchorRule net = rules.get(Field.NET);
        if (net == null || net.hits(text) == 0) {
            return 0;
        }
        // Gezählt werden Felder, nicht Beschriftungen: eine lange Rückfall-Kette macht eine Vorlage nicht
        // treffender, sie macht sie nur nachsichtiger.
        int score = 0;
        for (AnchorRule rule : rules.values()) {
            if (rule.hits(text) > 0) {
                score++;
            }
        }
        return score;
    }

    /**
     * Diese Vorlage, ergänzt um die Regeln von {@code older} für Felder, zu denen hier keine entstand.
     *
     * <p>Gebraucht beim Lernen: aus einer Abrechnung, in der eine Zeile fehlt, lässt sich für dieses Feld
     * nichts ableiten — und dann darf das bisher Gelernte nicht verlorengehen. Sonst verkürzte eine
     * Abrechnung ohne Solidaritätszuschlag die Steuerregel auf eine Zeile, und die nächste vollständige
     * läse zu wenig Steuer, ohne dass es auffiele.</p>
     */
    public StatementTemplate mergedOver(StatementTemplate older) {
        if (older == null) {
            return this;
        }
        Map<Field, AnchorRule> merged = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            AnchorRule mine = rules.get(field);
            AnchorRule old = older.rules.get(field);
            if (mine == null) {
                mine = old;
            } else if (isExcerptOf(mine, old)) {
                mine = old;
            }
            if (mine != null) {
                merged.put(field, mine);
            }
        }
        return new StatementTemplate(action, merged, keptFixedFee(older),
                keptFixedCategory(older), keptFixedInTotal(older));
    }

    /**
     * Die feste Gebühr überlebt jeden Lernvorgang.
     *
     * <p>Der Lerner setzt sie nie – sie steht ja nicht im Dokument, sondern wurde auf der Regelseite von
     * Hand eingetragen. Ohne diese Übernahme wäre sie nach der nächsten eingelesenen Abrechnung fort, und
     * niemand käme darauf, warum.</p>
     */
    private long keptFixedFee(StatementTemplate older) {
        return fixedFeeCents > 0 ? fixedFeeCents : older.fixedFeeCents;
    }

    private String keptFixedCategory(StatementTemplate older) {
        return fixedFeeCents > 0 ? fixedFeeCategory : older.fixedFeeCategory;
    }

    private boolean keptFixedInTotal(StatementTemplate older) {
        return fixedFeeCents > 0 ? fixedFeeInTotal : older.fixedFeeInTotal;
    }

    /**
     * Ob die neue Regel nur ein Ausschnitt der alten ist — sie liest dieselben Zeilen, bloß weniger
     * davon.
     *
     * <p>Dann hat diese Abrechnung schlicht weniger Zeilen gehabt, und nicht die Bank ihre Beschriftungen
     * geändert. Die alte Regel bleibt, denn sie liest beides richtig: bei der vollständigen Abrechnung
     * alle Zeilen, bei dieser die eine vorhandene. Ohne diese Unterscheidung verkürzte eine Dividende
     * ohne Solidaritätszuschlag die Steuerregel dauerhaft auf eine Zeile.</p>
     */
    /**
     * Diese Vorlage, aber mit der Reihenfolge von {@code older}: dessen Beschriftungen behalten Vorrang,
     * neue kommen dazu — hinten, wenn die alte Regel in {@code text} nichts fand, sonst vorne.
     *
     * <p>Die Gegenstück zu {@link #mergedOver}: wer eine Kette von Hand geordnet hat, soll sie durch das
     * nächste Lernen ergänzt bekommen und nicht ersetzt. Richtung, Summenkennung und Währung bleiben die
     * der bisherigen Regel — die Reihenfolge ist die Entscheidung, die von Hand getroffen wurde.</p>
     *
     * <p>Fand die alte Regel in dieser Abrechnung etwas — und sei es der falsche Wert, den der Nutzer
     * gerade korrigiert hat —, soll sie beim nächsten Mal nicht mehr zuerst gewinnen: die neue
     * Beschriftung kommt vor sie. Fand sie nichts, war die Zeile hier nur nicht vorhanden, und die alte
     * Reihenfolge bleibt vorn — dieselbe Regel liest weiterhin auch vollständigere Abrechnungen.</p>
     */
    public StatementTemplate appendedTo(StatementTemplate older, PdfText text) {
        if (older == null) {
            return this;
        }
        Map<Field, AnchorRule> merged = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            AnchorRule mine = rules.get(field);
            AnchorRule old = older.rules.get(field);
            if (old == null) {
                if (mine != null) {
                    merged.put(field, mine);
                }
                continue;
            }
            if (mine == null) {
                merged.put(field, old);
                continue;
            }
            java.util.List<String> anchors = new java.util.ArrayList<>(old.anchors);
            java.util.List<String> neu = new java.util.ArrayList<>();
            for (String anchor : mine.anchors) {
                if (!anchors.contains(anchor)) {
                    neu.add(anchor);
                }
            }
            if (!neu.isEmpty()) {
                boolean oldFoundEtwas = field == Field.DATE
                        ? old.readDate(text) > 0
                        : old.read(text) != null;
                if (oldFoundEtwas) {
                    anchors.addAll(0, neu);
                } else {
                    anchors.addAll(neu);
                }
            }
            merged.put(field, anchors.size() == old.anchors.size() ? old
                    : new AnchorRule(anchors, old.direction, old.sum, old.currency));
        }
        return new StatementTemplate(action, merged, keptFixedFee(older),
                keptFixedCategory(older), keptFixedInTotal(older));
    }

    private static boolean isExcerptOf(AnchorRule newer, AnchorRule older) {
        // Auf die Summenkennung kommt es dabei nicht an: gerade der Übergang von einer Summe auf eine
        // einzelne Zeile ist der Fall, um den es geht.
        return older != null && older.anchors.size() > newer.anchors.size()
                && older.anchors.containsAll(newer.anchors);
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
        AnchorRule gross = rules.get(Field.GROSS);
        e.grossCents = gross == null ? null : gross.readCents(text);
        e.shares = shares == null ? null : shares.read(text);
        e.price = price == null ? null : price.read(text);
        Long geleseneGebuehr = fee == null ? null : fee.readCents(text);
        e.feeCents = withFixedFee(geleseneGebuehr, fee);
        e.feeCategory = angesetzt(geleseneGebuehr, fee) ? fixedFeeCategory : "";
        // Eine Regel, die gesucht und nichts gefunden hat, sagt etwas anderes als eine fehlende Regel:
        // „stand nicht drin" statt „weiß ich nicht". Bei einer Dividende macht das den Unterschied — sonst
        // gilt die Steuer als unbekannt, und der Steuersatz aus den Einstellungen erfindet eine, obwohl
        // die Bank innerhalb des Freibetrags gar keine abgezogen hat.
        //
        // Bei Kauf und Verkauf ist die Unterscheidung entbehrlich: dort bedeutet ein leeres Gebührenfeld
        // ohnehin 0, und ein geschriebenes „0,00" verdeckte nur die Beschriftung des Feldes.
        if (fee != null && e.feeCents == null && "dividend".equals(action)) {
            e.feeCents = 0L;
        }
        e.netCents = net == null ? null : net.readCents(text);
        // Steckt die feste Gebühr nicht im ausgedruckten Gesamtbetrag, ist der abgebuchte Betrag um sie
        // höher – beim Verkauf die Gutschrift um sie niedriger. Nachgetragen wird nur, was oben auch
        // wirklich angesetzt wurde.
        if (e.netCents != null && !fixedFeeInTotal && angesetzt(geleseneGebuehr, fee)) {
            e.netCents += ("sell".equals(action) ? -1 : 1) * fixedFeeCents;
        }
        e.dateMillis = date == null ? -1 : date.readDate(text);
        return e;
    }

    /**
     * Ob die feste Gebühr bei dieser Abrechnung überhaupt zum Zuge kommt.
     *
     * <p>Darüber entscheidet der vorhandene Schalter «Mehrere Zeilen zusammenzählen»: er sagt bereits, ob
     * sich Gebührenbeträge addieren. Fand die Regel nichts – oder gibt es gar keine –, gilt der feste
     * Wert allein; das ist der Fall, um den es geht. Steht der Schalter aus und wurde etwas gefunden,
     * gewinnt der gefundene Wert.</p>
     */
    private boolean angesetzt(Long gelesen, AnchorRule fee) {
        if (fixedFeeCents <= 0) {
            return false;
        }
        return gelesen == null || (fee != null && fee.sum);
    }

    /** Die Gebühr, wie sie nach der Regel oben gilt. */
    private Long withFixedFee(Long gelesen, AnchorRule fee) {
        if (!angesetzt(gelesen, fee)) {
            return gelesen;
        }
        return (gelesen == null ? 0L : gelesen) + fixedFeeCents;
    }

    /**
     * Ob diese Vorlage dieselbe Auslese beschreibt wie {@code other} — gleiche Aktion, gleiche Regeln.
     *
     * <p>Damit lässt sich sagen, ob ein Lernvorgang überhaupt etwas Neues ergeben hat. Hat der Nutzer
     * nichts korrigiert, kommt beim Lernen dieselbe Vorlage heraus, und es gibt nichts zu fragen.</p>
     */
    public boolean sameAs(StatementTemplate other) {
        if (other == null) {
            return false;
        }
        if (action == null ? other.action != null : !action.equals(other.action)) {
            return false;
        }
        if (fixedFeeCents != other.fixedFeeCents || fixedFeeInTotal != other.fixedFeeInTotal
                || !fixedFeeCategory.equals(other.fixedFeeCategory)) {
            return false;
        }
        return rules.equals(other.rules);
    }

    /** Das Ergebnis einer Auslese. Nicht Erkanntes ist {@code null} bzw. -1. */
    public static final class Extraction {
        public String action;
        public String isin;
        public long dateMillis = -1;
        public Double shares;
        public Double price;
        public Long feeCents;
        /** Kategorie einer festen Gebühr; leer, wenn keine angesetzt wurde. */
        public String feeCategory = "";
        public Long netCents;
        /** Nur gesetzt, wenn von Hand eine Brutto-Regel angelegt wurde (siehe {@link Field#GROSS}). */
        public Long grossCents;

        /** Ob überhaupt etwas herauskam, das die Maske vorbelegen kann. */
        public boolean hasAnything() {
            return isin != null || dateMillis > 0 || shares != null || price != null
                    || feeCents != null || netCents != null || grossCents != null;
        }
    }
}
