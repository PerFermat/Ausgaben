package de.spahr.ausgaben.ui;

import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.widget.EditText;

import de.spahr.ausgaben.settings.MoneyFormat;

/**
 * Richtet Zahlen- und Betragsfelder einheitlich ein: erlaubt sind Ziffern und <b>genau das in den
 * Einstellungen gewählte Dezimalzeichen</b> – Komma oder Punkt, nicht beides.
 *
 * <p>Nötig, weil {@code android:inputType="numberDecimal"} einen Zeichenfilter mitbringt, der nur den
 * Punkt kennt: er verschluckt das Komma sogar dann, wenn es von der eigenen Rechentastatur der App
 * kommt. Deshalb setzen die Betragsfelder ihren Zeichenvorrat hier im Code statt im Layout.</p>
 */
public final class AmountField {

    private static final String DIGITS = "0123456789";

    private AmountField() {
    }

    /**
     * Betragsfeld an der Rechentastatur: zusätzlich {@code + - *} für kleine Rechnungen; Struktur und
     * Reihenfolge prüft der {@link CalcInputFilter}.
     */
    public static void prepareCalc(EditText field) {
        field.setKeyListener(DigitsKeyListener.getInstance(
                DIGITS + MoneyFormat.decimalSeparator() + "+-*"));
        field.setFilters(new InputFilter[]{new CalcInputFilter()});
    }

    /**
     * Reines Zahlenfeld ohne Rechnen (Filtergrenzen): Ziffern, das Dezimalzeichen und das Minus –
     * Ausgaben sind negativ, und ohne Minus fiele es beim Vorbelegen der Grenzen still weg.
     */
    public static void prepareNumber(EditText field) {
        field.setKeyListener(DigitsKeyListener.getInstance(
                DIGITS + MoneyFormat.decimalSeparator() + "-"));
    }

    /** Prozentfeld: Ziffern und das Dezimalzeichen, sonst nichts – ein Vorzeichen ergäbe hier keinen Sinn. */
    public static void preparePercent(EditText field) {
        field.setKeyListener(DigitsKeyListener.getInstance(DIGITS + MoneyFormat.decimalSeparator()));
    }
}
