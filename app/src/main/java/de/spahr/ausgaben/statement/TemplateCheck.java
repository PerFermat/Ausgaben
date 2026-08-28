package de.spahr.ausgaben.statement;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import de.spahr.ausgaben.pdf.PdfText;

/**
 * Die Probe aufs Gelernte: greifen die frisch gemerkten Regeln auf der Abrechnung, aus der sie
 * stammen?
 *
 * <p>Bisher war das eine Frage der Zeit — gelernt wurde beim Erfassen der ersten Abrechnung, und ob die
 * Regeln taugen, zeigte sich erst Wochen später bei der nächsten. Traf der Lerner daneben, saß man
 * wieder vor einer leeren Maske und wusste nicht, warum. Dabei liegt im Augenblick des Merkens alles
 * vor: die Regeln und das Dokument. Also wird gemessen statt gehofft.</p>
 *
 * <p>Gelesen wird über {@link StatementTemplate#apply(PdfText)} — nicht Regel für Regel. Nur so misst die
 * Prüfung wirklich das, was beim nächsten Import herauskäme: {@code apply} schlägt auch eine feste
 * Ordergebühr auf und berichtigt damit den Gesamtbetrag.</p>
 */
public final class TemplateCheck {

    private TemplateCheck() {
    }

    /** Stück- und Kursangaben werden auf sechs Nachkommastellen verglichen, nicht auf das letzte Bit. */
    private static final double EPSILON = 0.0000005;

    /** Was in der Maske stand — der Sollwert, gegen den gemessen wird. */
    public static final class Expected {

        /** Die Aktion der Vorlage: {@code buy}, {@code sell} oder {@code dividend}. */
        public String action;
        public long dateMillis = -1;
        public Double shares;
        public Double price;
        public Long feeCents;
        public Long netCents;
        public Long grossCents;

        /**
         * Die Felder, die der Nutzer <b>selbst</b> geändert hat.
         *
         * <p>Sie entscheiden über den Fall „gar keine Regel gelernt": Brutto und Kurs rechnet die Maske
         * sich selbst aus, für sie gibt es oft mit gutem Grund keine Regel. Ein Feld dagegen, das jemand
         * eigens berichtigt hat, um es der App beizubringen, muss hinterher auch gefunden werden.</p>
         */
        public final Set<StatementTemplate.Field> typed =
                EnumSet.noneOf(StatementTemplate.Field.class);
    }

    /** Woran es liegt. */
    public enum Kind {
        /** Für ein eigens berichtigtes Feld ist gar keine Regel entstanden. */
        NO_RULE,
        /** Es gibt eine Regel, sie findet in dieser Abrechnung aber nichts. */
        NOT_FOUND,
        /** Die Regel findet etwas anderes, als in der Maske stand. */
        WRONG
    }

    /** Ein einzelner Mangel. */
    public static final class Complaint {

        public final StatementTemplate.Field field;
        public final Kind kind;
        /**
         * Soll- und Istwert. Beim Datum in Millisekunden, bei Geldfeldern in Euro, sonst der Zahlwert
         * selbst; {@code actual} ist nur bei {@link Kind#WRONG} gemeint.
         */
        public final double expected;
        public final double actual;

        Complaint(StatementTemplate.Field field, Kind kind, double expected, double actual) {
            this.field = field;
            this.kind = kind;
            this.expected = expected;
            this.actual = actual;
        }
    }

    /**
     * Die Felder in der Reihenfolge, in der sie auch auf der Regelseite stehen. Bei einer Dividende
     * fehlen Anzahl und Kurs — eine Ertragsgutschrift bucht keine Stücke.
     */
    private static StatementTemplate.Field[] fieldsFor(String action) {
        if ("dividend".equals(action)) {
            return new StatementTemplate.Field[]{StatementTemplate.Field.DATE,
                    StatementTemplate.Field.NET, StatementTemplate.Field.FEE,
                    StatementTemplate.Field.GROSS};
        }
        return new StatementTemplate.Field[]{StatementTemplate.Field.DATE,
                StatementTemplate.Field.NET, StatementTemplate.Field.FEE,
                StatementTemplate.Field.GROSS, StatementTemplate.Field.SHARES,
                StatementTemplate.Field.PRICE};
    }

    /**
     * Wendet die Vorlage auf die Abrechnung an und hält das Ergebnis gegen die Maske.
     *
     * <p>Übersprungen wird, wo es nichts zu vergleichen gibt: ein Feld ohne Wert in der Maske, und ein
     * Feld ohne Regel, das der Nutzer nicht angefasst hat. Eine leere Liste heißt: die Vorlage liest
     * diese Abrechnung genau so, wie sie am Ende dastand.</p>
     */
    public static List<Complaint> check(StatementTemplate template, PdfText text, Expected soll) {
        List<Complaint> out = new ArrayList<>();
        if (template == null || text == null || soll == null) {
            return out;
        }
        StatementTemplate.Extraction ist = template.apply(text);
        for (StatementTemplate.Field field : fieldsFor(soll.action)) {
            Complaint c = compare(template, soll, ist, field);
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    private static Complaint compare(StatementTemplate template, Expected soll,
                                     StatementTemplate.Extraction ist,
                                     StatementTemplate.Field field) {
        if (field == StatementTemplate.Field.DATE) {
            return compareDate(template, soll, ist);
        }
        Double expected = expected(soll, field);
        if (expected == null) {
            return null;   // die Maske trägt hier nichts – dann gibt es auch nichts zu messen
        }
        Double actual = actual(ist, field);
        if (template.rule(field) == null && !angesetzteGebuehr(template, ist, field)) {
            // Für das Brutto legt der Lerner mit Absicht nie eine Regel an (siehe
            // {@link StatementTemplate.Field#GROSS}) – es wird in der Maske gerechnet. Eine fehlende
            // Regel ist dort also kein Mangel, sondern das Übliche; sie zu bemängeln hieße, bei jeder
            // Dividende einen Fehler zu melden, den es nicht gibt.
            if (field == StatementTemplate.Field.GROSS) {
                return null;
            }
            return soll.typed.contains(field)
                    ? new Complaint(field, Kind.NO_RULE, expected, 0) : null;
        }
        if (actual == null) {
            return new Complaint(field, Kind.NOT_FOUND, expected, 0);
        }
        return gleich(field, expected, actual) ? null
                : new Complaint(field, Kind.WRONG, expected, actual);
    }

    /**
     * Beim Datum zählt der Kalendertag, nicht die Uhrzeit: die Maske trägt die Tageszeit der Eingabe
     * mit sich, die Regel liest Mitternacht.
     */
    private static Complaint compareDate(StatementTemplate template, Expected soll,
                                         StatementTemplate.Extraction ist) {
        if (soll.dateMillis <= 0) {
            return null;
        }
        if (template.rule(StatementTemplate.Field.DATE) == null) {
            return soll.typed.contains(StatementTemplate.Field.DATE)
                    ? new Complaint(StatementTemplate.Field.DATE, Kind.NO_RULE, soll.dateMillis, 0)
                    : null;
        }
        if (ist.dateMillis <= 0) {
            return new Complaint(StatementTemplate.Field.DATE, Kind.NOT_FOUND, soll.dateMillis, 0);
        }
        return tag(soll.dateMillis) == tag(ist.dateMillis) ? null
                : new Complaint(StatementTemplate.Field.DATE, Kind.WRONG,
                        soll.dateMillis, ist.dateMillis);
    }

    /**
     * Ob die Gebühr aus einem <b>festen</b> Betrag der Vorlage stammt. Dann gibt es zwar keine Regel,
     * aber sehr wohl einen Wert — und die Nachfrage nach einer fehlenden Regel wäre falsch.
     */
    private static boolean angesetzteGebuehr(StatementTemplate template,
                                             StatementTemplate.Extraction ist,
                                             StatementTemplate.Field field) {
        return field == StatementTemplate.Field.FEE
                && template.fixedFeeCents > 0 && ist.feeCents != null;
    }

    private static Double expected(Expected soll, StatementTemplate.Field field) {
        switch (field) {
            case NET:
                return soll.netCents == null ? null : soll.netCents / 100.0;
            case FEE:
                return soll.feeCents == null ? null : soll.feeCents / 100.0;
            case GROSS:
                return soll.grossCents == null ? null : soll.grossCents / 100.0;
            case SHARES:
                return soll.shares;
            default:
                return soll.price;
        }
    }

    private static Double actual(StatementTemplate.Extraction ist, StatementTemplate.Field field) {
        switch (field) {
            case NET:
                return ist.netCents == null ? null : ist.netCents / 100.0;
            case FEE:
                return ist.feeCents == null ? null : ist.feeCents / 100.0;
            case GROSS:
                return ist.grossCents == null ? null : ist.grossCents / 100.0;
            case SHARES:
                return ist.shares;
            default:
                return ist.price;
        }
    }

    /**
     * Geldbeträge werden auf den Cent verglichen — dieselbe Genauigkeit, mit der sie gebucht werden.
     * Eine Abweichung darunter gibt es in einer Abrechnung nicht.
     */
    private static boolean gleich(StatementTemplate.Field field, double a, double b) {
        if (field == StatementTemplate.Field.SHARES || field == StatementTemplate.Field.PRICE) {
            return Math.abs(a - b) < EPSILON;
        }
        return Math.round(a * 100.0) == Math.round(b * 100.0);
    }

    /** Der Tagesbeginn zu einem Zeitpunkt – die Uhrzeit spielt beim Buchungsdatum keine Rolle. */
    private static long tag(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
