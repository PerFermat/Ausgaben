package de.spahr.ausgaben.settings;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Rechnet einen eingetippten Betrag aus – auch als kleine Rechnung, z. B. {@code 12,50+3,20} (geteilte
 * Restaurantrechnung) oder {@code 3*4,50}. Erlaubt sind {@code + − * / ( )} und Dezimaltrennung mit Komma
 * oder Punkt; eine reine Zahl verhält sich exakt wie vorher.
 *
 * <p>Rechnet durchgängig mit {@link BigDecimal} (kein {@code double}), damit Geldbeträge nicht durch
 * Binärbrüche verfälscht werden. Ungültige Eingaben ergeben {@code null} – nie eine Ausnahme.</p>
 */
public final class AmountExpression {

    /** Teilung mit ausreichender Genauigkeit; das Ergebnis wird erst beim Aufrufer auf Cent gerundet. */
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private final String s;
    private int pos;

    private AmountExpression(String s) {
        this.s = s;
    }

    /**
     * Wertet {@code raw} aus. {@code null}, wenn die Eingabe leer oder keine gültige Rechnung ist.
     * Komma = Dezimaltrennzeichen; Leerzeichen werden ignoriert.
     */
    public static BigDecimal evaluate(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim().replace(" ", "").replace(",", ".").replace("−", "-").replace("×", "*")
                .replace("·", "*").replace("÷", "/");
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

    /** Summe: Produkt (('+'|'-') Produkt)* */
    private BigDecimal sum() {
        BigDecimal v = product();
        while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
            char op = s.charAt(pos++);
            BigDecimal r = product();
            v = op == '+' ? v.add(r) : v.subtract(r);
        }
        return v;
    }

    /** Produkt: Faktor (('*'|'/') Faktor)* */
    private BigDecimal product() {
        BigDecimal v = factor();
        while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
            char op = s.charAt(pos++);
            BigDecimal r = factor();
            if (op == '*') {
                v = v.multiply(r);
            } else {
                if (r.signum() == 0) {
                    throw new ArithmeticException("Division durch 0");
                }
                v = v.divide(r, MC);
            }
        }
        return v;
    }

    /** Faktor: ('+'|'-')? ( '(' Summe ')' | Zahl ) */
    private BigDecimal factor() {
        if (pos >= s.length()) {
            throw new IllegalArgumentException("unerwartetes Ende");
        }
        char c = s.charAt(pos);
        if (c == '+') {
            pos++;
            return factor();
        }
        if (c == '-') {
            pos++;
            return factor().negate();
        }
        if (c == '(') {
            pos++;
            BigDecimal v = sum();
            if (pos >= s.length() || s.charAt(pos) != ')') {
                throw new IllegalArgumentException("Klammer nicht geschlossen");
            }
            pos++;
            return v;
        }
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
