package de.spahr.ausgaben.ui;

import android.text.InputFilter;
import android.text.Spanned;

/**
 * Lässt in einem Betragsfeld nur die Zeichen einer erlaubten kleinen Rechnung zu und verhindert schon
 * <b>während der Eingabe</b> offensichtlich ungültige Strukturen. Erlaubt: Ziffern, ein Dezimaltrennzeichen
 * (Komma oder Punkt) je Zahl, <b>{@code +}</b> und <b>{@code *}</b>. Nicht erlaubt: {@code - / ( )},
 * Buchstaben, führende Operatoren, doppelte Operatoren und ein zweites Dezimaltrennzeichen in derselben Zahl.
 *
 * <p>Geprüft wird der jeweils entstehende Text als gültiger <i>Anfang</i> einer Rechnung (ein am Ende
 * stehender Operator wie {@code 10+} ist zulässig, weil die Zahl noch folgt). Die endgültige Auswertung
 * übernimmt {@link de.spahr.ausgaben.settings.AmountExpression}.</p>
 */
public class CalcInputFilter implements InputFilter {

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                               int dstart, int dend) {
        String result = dest.subSequence(0, dstart)
                + source.subSequence(start, end).toString()
                + dest.subSequence(dend, dest.length());
        return isTypablePrefix(result) ? null : "";   // null = Änderung übernehmen, "" = ablehnen
    }

    /** {@code true}, wenn {@code s} ein gültiger Anfang einer Rechnung aus Zahlen, {@code +} und {@code *} ist. */
    static boolean isTypablePrefix(String s) {
        boolean prevDigit = false;        // letztes Zeichen war eine Ziffer
        boolean sepInNumber = false;      // aktuelle Zahl hat schon ein Trennzeichen
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                prevDigit = true;
            } else if (c == ',' || c == '.') {
                if (sepInNumber) {
                    return false;         // zweites Trennzeichen in derselben Zahl
                }
                sepInNumber = true;
                prevDigit = false;
            } else if (c == '+' || c == '*') {
                if (!prevDigit) {
                    return false;         // Operator am Anfang, nach Operator oder nach Trennzeichen
                }
                prevDigit = false;
                sepInNumber = false;      // neue Zahl beginnt
            } else {
                return false;             // unerlaubtes Zeichen
            }
        }
        return true;
    }
}
