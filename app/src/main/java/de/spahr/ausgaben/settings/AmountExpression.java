package de.spahr.ausgaben.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rechnet einen eingetippten Betrag aus – auch als kleine Rechnung, z. B. {@code 12,50+3,20} (geteilte
 * Restaurantrechnung) oder {@code 3*4,50}. Bewusst <b>eingeschränkt</b>: erlaubt sind nur Zahlen mit
 * Dezimaltrennung (Komma oder Punkt), <b>Addition ({@code +})</b> und <b>Multiplikation ({@code *})</b> –
 * mit üblicher Priorität (Punkt vor Strich). Subtraktion, Division, Klammern und Funktionen sind
 * <b>nicht</b> zulässig. Eine reine Zahl verhält sich wie vorher.
 *
 * <p>Rechnet durchgängig mit {@link BigDecimal} (kein {@code double}), damit Geldbeträge nicht durch
 * Binärbrüche verfälscht werden. Ungültige Eingaben ergeben {@code null} – nie eine Ausnahme.</p>
 */
public final class AmountExpression {

    private final String s;
    private int pos;

    private AmountExpression(String s) {
        this.s = s;
    }

    /**
     * Wertet {@code raw} aus. {@code null}, wenn die Eingabe leer oder keine gültige Rechnung ist.
     * Komma = Dezimaltrennzeichen; Leerzeichen werden ignoriert. Nur {@code +} und {@code *} sind erlaubt.
     */
    public static BigDecimal evaluate(String raw) {
        if (raw == null) {
            return null;
        }
        // Komma → Punkt; „×"/„·" als bequeme Multiplikations-Varianten. Alles andere (− / ( ) …) bleibt
        // stehen und lässt die Auswertung scheitern → null.
        String t = raw.trim().replace(" ", "").replace(",", ".").replace("×", "*").replace("·", "*");
        if (t.isEmpty()) {
            return null;
        }
        try {
            AmountExpression p = new AmountExpression(t);
            BigDecimal v = p.sum();
            return p.pos == t.length() ? v : null;   // Rest übrig → ungültig
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wertet {@code raw} aus und rundet auf Cent (kaufmännisch). {@code null}, wenn die Eingabe keine gültige
     * Rechnung ist oder das Ergebnis nicht in einen {@code long}-Centbetrag passt.
     */
    public static Long toCents(String raw) {
        BigDecimal value = evaluate(raw);
        if (value == null) {
            return null;
        }
        try {
            return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** Summe: Produkt ('+' Produkt)* */
    private BigDecimal sum() {
        BigDecimal v = product();
        while (pos < s.length() && s.charAt(pos) == '+') {
            pos++;
            v = v.add(product());
        }
        return v;
    }

    /** Produkt: Zahl ('*' Zahl)* */
    private BigDecimal product() {
        BigDecimal v = number();
        while (pos < s.length() && s.charAt(pos) == '*') {
            pos++;
            v = v.multiply(number());
        }
        return v;
    }

    /** Zahl: Ziffern mit höchstens einem Dezimalpunkt (kein Vorzeichen, keine Klammer). */
    private BigDecimal number() {
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Zahl erwartet");
        }
        return new BigDecimal(s.substring(start, pos));
    }
}
