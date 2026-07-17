package de.spahr.ausgaben.ui;

import android.text.InputFilter;
import android.text.Spanned;

/**
 * Lässt in einem Betragsfeld nur die Zeichen einer erlaubten kleinen Rechnung zu und verhindert schon
 * <b>während der Eingabe</b> offensichtlich ungültige Strukturen. Erlaubt: Ziffern, ein Dezimaltrennzeichen
 * (Komma oder Punkt) je Zahl, <b>{@code +}</b>, <b>{@code *}</b> und <b>{@code -}</b> (Vorzeichen oder
 * Subtraktion). Nicht erlaubt: {@code / ( )}, Buchstaben, führendes {@code +}/{@code *}, doppelte
 * {@code +}/{@code *} und ein zweites Dezimaltrennzeichen in derselben Zahl.
 *
 * <p>Geprüft wird der jeweils entstehende Text als gültiger <i>Anfang</i> einer Rechnung (ein am Ende
 * stehender Operator wie {@code 10+} ist zulässig, weil die Zahl noch folgt). Die endgültige Auswertung
 * übernimmt {@link de.spahr.ausgaben.settings.AmountExpression}.</p>
 */
public class CalcInputFilter implements InputFilter {

    private static final int START = 0, DIGIT = 1, SEP = 2, OP = 3, MINUS = 4;

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                               int dstart, int dend) {
        String result = dest.subSequence(0, dstart)
                + source.subSequence(start, end).toString()
                + dest.subSequence(dend, dest.length());
        return isTypablePrefix(result) ? null : "";   // null = Änderung übernehmen, "" = ablehnen
    }

    /** {@code true}, wenn {@code s} ein gültiger Anfang einer Rechnung aus Zahlen, {@code + - *} ist. */
    static boolean isTypablePrefix(String s) {
        int last = START;
        boolean sepInNumber = false;      // aktuelle Zahl hat schon ein Trennzeichen
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                last = DIGIT;
            } else if (c == ',' || c == '.') {
                if (sepInNumber) {
                    return false;         // zweites Trennzeichen in derselben Zahl
                }
                sepInNumber = true;
                last = SEP;
            } else if (c == '+' || c == '*') {
                if (last != DIGIT) {
                    return false;         // +/* nur nach einer (Teil-)Zahl, nicht am Anfang/nach Operator/Trennzeichen
                }
                sepInNumber = false;      // neue Zahl beginnt
                last = OP;
            } else if (c == '-') {
                if (last == SEP) {
                    return false;         // kein Minus direkt nach einem Trennzeichen (z. B. „1,-")
                }
                sepInNumber = false;      // Vorzeichen/Subtraktion → neue Zahl
                last = MINUS;
            } else {
                return false;             // unerlaubtes Zeichen
            }
        }
        return true;
    }
}
