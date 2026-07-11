package de.spahr.ausgaben.settings;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Charakterisierungstests für {@link MoneyFormat} im Standardzustand (kein {@code refresh}): Dezimalkomma,
 * keine Tausendertrennung, Währung an. Betrifft u. a. {@code SplitRowController} und die Budget-/Depot-Anzeige.
 * Kein Test ruft {@code refresh(Context)} auf, damit der statische Standardzustand erhalten bleibt.
 */
public class MoneyFormatTest {

    @Test
    public void plain_defaultCommaNoGrouping() {
        assertEquals("12,34", MoneyFormat.plain(1234));
        assertEquals("0,00", MoneyFormat.plain(0));
        assertEquals("2000,00", MoneyFormat.plain(200000));
    }

    @Test
    public void plain_negativeSign() {
        assertEquals("-0,50", MoneyFormat.plain(-50));
    }

    @Test
    public void display_appendsCurrencyWhenPresent() {
        assertEquals("12,34 EUR", MoneyFormat.display(1234, "EUR"));
    }

    @Test
    public void display_noCurrencyWhenBlankOrNull() {
        assertEquals("12,34", MoneyFormat.display(1234, ""));
        assertEquals("12,34", MoneyFormat.display(1234, null));
    }
}
