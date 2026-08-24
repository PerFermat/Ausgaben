package de.spahr.ausgaben.util;

/**
 * Rechenkern der Wertpapier-Erfassung: ergänzt aus den eingegebenen Feldern die fehlenden.
 *
 * <p>Fünf Größen, zwei Gleichungen – {@link Field#GROSS} gehört beiden an:</p>
 * <pre>
 *   Anzahl × Stückpreis = Betrag            (bei einer Dividende: × Dividende je Stück = Brutto)
 *   Gesamtsumme         = Betrag + Gebühr   (Kauf)
 *   Gesamtsumme         = Betrag − Gebühr   (Verkauf)
 *   Netto               = Brutto − Steuer   (Dividende)
 * </pre>
 *
 * <p>Übergeben werden nur die Felder, die der <b>Nutzer selbst</b> gefüllt hat; alles andere ist
 * {@code null} und wird – soweit ableitbar – berechnet zurückgegeben. Bei Kauf/Verkauf gilt ein leeres
 * Gebührenfeld als 0, bei einer Dividende dagegen als „noch offen": dort belegt der Steuersatz aus den
 * Einstellungen die beiden übrigen Geldfelder vor, solange erst eines von ihnen gesetzt ist.</p>
 *
 * <p>Reine Rechenklasse ohne Android-Bezug, damit sie als Unit-Test prüfbar bleibt.</p>
 */
public final class SecurityAmounts {

    /** Die Eingabefelder der Maske. */
    public enum Field {
        /** Stückzahl. */
        SHARES,
        /** Stückpreis bzw. Dividende je Stück. */
        PRICE,
        /** Betrag (Kauf/Verkauf, in der Maske nicht sichtbar) bzw. Brutto (Dividende). */
        GROSS,
        /** Gebühr (Kauf/Verkauf) bzw. Steuer (Dividende). */
        FEE,
        /** Gesamtsumme (Kauf/Verkauf) bzw. Netto (Dividende). */
        NET
    }

    /** Stückzahlen unterhalb dieser Schwelle gelten als „nicht gesetzt" (Division). */
    private static final double EPS = 1e-9;

    /** Zulässige Abweichung der Geldprobe: Ein Cent, sonst schlägt sie bei krummen Steuersätzen an. */
    private static final long TOLERANCE_CENTS = 1;

    private SecurityAmounts() {
    }

    /** Was der Nutzer eingegeben hat. Nicht gefüllte Felder sind {@code null}. */
    public static final class Input {
        public Double shares;
        public Double price;
        public Long grossCents;
        public Long feeCents;
        public Long netCents;
        /** Eine der Aktionen {@code buy}, {@code sell}, {@code dividend}. */
        public String action = "buy";
        /** Steuersatz als Anteil (0,26375 = 26,375 %); 0 = keine Vorbelegung. Nur für Dividenden. */
        public double taxRate;
        /**
         * Das Feld der Stück-Gruppe, das beim letzten Durchgang berechnet wurde – es bleibt bevorzugt
         * das berechnete. {@code null}, solange noch nichts berechnet wurde.
         */
        public Field lastComputed;
        /** Das Feld, das der Nutzer gerade geändert hat; es bleibt in jedem Fall stehen. */
        public Field justEdited;
    }

    /** Das Ergebnis: dieselben fünf Größen, ergänzt um alles Ableitbare. */
    public static final class Result {
        public Double shares;
        public Double price;
        public Long grossCents;
        public Long feeCents;
        public Long netCents;
        /** Welches Feld der Stück-Gruppe diesmal berechnet wurde (für den nächsten Durchgang merken). */
        public Field computed;
        /**
         * Brutto, Steuer und Netto sind alle drei eingegeben, gehen aber nicht auf. Die Maske zeigt dann
         * eine Fehlermeldung und sperrt das Speichern; gerechnet wird nichts.
         */
        public boolean conflict;
    }

    /**
     * Ergänzt die fehlenden Werte.
     *
     * <p>Sind Anzahl, Stückpreis und Betrag gleichzeitig von Hand gesetzt, ist die Stück-Gruppe
     * überbestimmt. Dann gibt eines der drei nach, und zwar nach dieser Ordnung: bevorzugt wieder das
     * zuletzt berechnete Feld; hat der Nutzer ausgerechnet dieses korrigiert, rückt der Stückpreis nach,
     * danach der Betrag und zuletzt die Anzahl. So bleibt stehen, was der Nutzer zuletzt angefasst hat,
     * und die Anzahl – die Größe, die man am sichersten weiß – gibt als Letzte nach.</p>
     */
    public static Result solve(Input in) {
        Result r = new Result();
        r.shares = in.shares;
        r.price = in.price;
        r.grossCents = in.grossCents;
        r.feeCents = in.feeCents;
        r.netCents = in.netCents;

        final boolean dividend = isDividend(in.action);
        // Kauf/Verkauf: ein leeres Gebührenfeld bedeutet „keine Gebühr". Bei einer Dividende bedeutet es
        // dagegen „noch nicht bekannt" – dort füllt der Steuersatz die Lücke.
        if (!dividend && r.feeCents == null) {
            r.feeCents = 0L;
        }

        // Bei Kauf/Verkauf ist der Betrag gar nicht sichtbar – dort steht er über die eingegebene
        // Gesamtsumme fest. Erst danach zeigt sich, ob die Stück-Gruppe überbestimmt ist.
        boolean grossPinned = r.grossCents != null;
        if (r.grossCents == null && r.netCents != null && r.feeCents != null) {
            r.grossCents = r.netCents - sign(in.action) * r.feeCents;
            grossPinned = true;
        }

        // Überbestimmte Stück-Gruppe: eines der drei Felder wird wieder zum berechneten. Der Betrag
        // scheidet aus, wenn ihn die eingegebene Gesamtsumme festnagelt.
        if (r.shares != null && r.price != null && r.grossCents != null) {
            Field give = yieldingField(in, grossPinned);
            r.computed = give;
            switch (give) {
                case SHARES:
                    r.shares = null;
                    break;
                case PRICE:
                    r.price = null;
                    break;
                default:
                    r.grossCents = null;
                    // Bei Kauf/Verkauf steht der Betrag nicht selbst in der Maske: dort gibt die
                    // Gesamtsumme nach, aus der er stammt – sonst müsste die Anzahl herhalten.
                    if (grossPinned) {
                        r.netCents = null;
                    }
                    break;
            }
        }

        // Alle drei Geldfelder von Hand gesetzt: nicht rechnen, sondern prüfen.
        if (in.grossCents != null && in.feeCents != null && in.netCents != null) {
            long expected = in.grossCents + sign(in.action) * in.feeCents;
            if (Math.abs(expected - in.netCents) > TOLERANCE_CENTS) {
                r.conflict = true;
                return r;
            }
        }

        // Beide Gleichungen abwechselnd anwenden, bis sich nichts mehr ergibt. Zwei Runden genügen für
        // jede Kette (Netto → Brutto → Stückpreis), die dritte ist die Abbruchprobe.
        for (int pass = 0; pass < 3; pass++) {
            boolean progress = fillMoney(r, in, dividend);
            progress |= fillShares(r);
            if (!progress) {
                break;
            }
        }
        return r;
    }

    /** Welches Feld der Stück-Gruppe nachgibt, wenn alle drei gesetzt sind. */
    private static Field yieldingField(Input in, boolean grossPinned) {
        if (in.lastComputed != null && in.lastComputed != in.justEdited && isShareGroup(in.lastComputed)
                && !(grossPinned && in.lastComputed == Field.GROSS)) {
            return in.lastComputed;
        }
        if (in.justEdited != Field.PRICE) {
            return Field.PRICE;
        }
        // Der Stückpreis ist gerade die Nutzereingabe: dann rückt der Betrag (und mit ihm die
        // Gesamtsumme) nach. Die Anzahl gibt als Letzte nach – sie weiß man am sichersten.
        return Field.GROSS;
    }

    private static boolean isShareGroup(Field f) {
        return f == Field.SHARES || f == Field.PRICE || f == Field.GROSS;
    }

    /** {@code Betrag = Anzahl × Stückpreis} in beide Richtungen. */
    private static boolean fillShares(Result r) {
        if (r.grossCents == null && r.shares != null && r.price != null) {
            r.grossCents = Math.round(r.shares * r.price * 100.0);
            return true;
        }
        if (r.price == null && r.grossCents != null && r.shares != null && Math.abs(r.shares) > EPS) {
            r.price = r.grossCents / 100.0 / r.shares;
            return true;
        }
        if (r.shares == null && r.grossCents != null && r.price != null && Math.abs(r.price) > EPS) {
            r.shares = r.grossCents / 100.0 / r.price;
            return true;
        }
        return false;
    }

    /**
     * {@code Netto = Brutto ± Gebühr} in alle Richtungen; bei einer Dividende springt zusätzlich der
     * Steuersatz ein, solange erst eines der drei Geldfelder feststeht.
     */
    private static boolean fillMoney(Result r, Input in, boolean dividend) {
        int s = sign(in.action);
        if (r.netCents == null && r.grossCents != null && r.feeCents != null) {
            r.netCents = r.grossCents + s * r.feeCents;
            return true;
        }
        if (r.grossCents == null && r.netCents != null && r.feeCents != null) {
            r.grossCents = r.netCents - s * r.feeCents;
            return true;
        }
        if (r.feeCents == null && r.grossCents != null && r.netCents != null) {
            r.feeCents = s * (r.netCents - r.grossCents);
            return true;
        }
        if (!dividend || in.taxRate <= 0 || in.taxRate >= 1) {
            return false;
        }
        // Genau ein Geldfeld bekannt: die beiden anderen über den Steuersatz vorbelegen.
        if (r.grossCents != null && r.feeCents == null && r.netCents == null) {
            r.feeCents = Math.round(r.grossCents * in.taxRate);
            return true;
        }
        if (r.feeCents != null && r.grossCents == null && r.netCents == null) {
            r.grossCents = Math.round(r.feeCents / in.taxRate);
            return true;
        }
        if (r.netCents != null && r.grossCents == null && r.feeCents == null) {
            r.grossCents = Math.round(r.netCents / (1.0 - in.taxRate));
            return true;
        }
        return false;
    }

    /** Vorzeichen der Gebühr: beim Kauf erhöht sie die Gesamtsumme, sonst mindert sie den Ertrag. */
    private static int sign(String action) {
        return "buy".equals(action) ? 1 : -1;
    }

    private static boolean isDividend(String action) {
        return "dividend".equals(action);
    }
}
