package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Die angezeigten Konten ({@link AccountScope}): welcher Empfänger bei der automatischen Auswahl
 * überhaupt in Frage kommt.
 */
public class AccountScopeTest {

    private static Booking buchung(String konto) {
        Booking b = new Booking();
        b.payee = "Rewe";
        b.account = konto;
        return b;
    }

    private static PayeeCorrection alias(String konto, String von, String nach) {
        PayeeCorrection a = new PayeeCorrection();
        a.corrected = "Rewe";
        a.account = konto;
        a.fromAccount = von;
        a.toAccount = nach;
        return a;
    }

    @Test
    public void ohneAuswahlZaehltAlles() {
        Set<String> keine = AccountScope.of("");
        assertTrue(keine.isEmpty());
        assertTrue(AccountScope.covers(keine, buchung("Giro")));
        assertTrue(AccountScope.covers(keine, alias("Giro", "", "")));
    }

    @Test
    public void buchungAusFremdemKontoFaelltWeg() {
        Set<String> bargeld = AccountScope.of("Bargeld");
        assertTrue(AccountScope.covers(bargeld, buchung("Bargeld")));
        assertFalse(AccountScope.covers(bargeld, buchung("Giro")));
    }

    @Test
    public void grossKleinUndLeerraumEgal() {
        Set<String> bargeld = AccountScope.of("  BarGeld ");
        assertTrue(AccountScope.covers(bargeld, buchung("bargeld ")));
    }

    @Test
    public void aliasOhneKontoBleibt() {
        Set<String> bargeld = AccountScope.of("Bargeld");
        assertTrue(AccountScope.covers(bargeld, alias("", "", "")));
        assertTrue(AccountScope.covers(bargeld, alias(null, null, null)));
    }

    @Test
    public void aliasMitFremdemKontoFaelltWeg() {
        Set<String> bargeld = AccountScope.of("Bargeld");
        assertFalse(AccountScope.covers(bargeld, alias("Giro", "", "")));
        assertTrue(AccountScope.covers(bargeld, alias("Bargeld", "", "")));
    }

    @Test
    public void beiUmbuchungGenuegtEineSeite() {
        Set<String> bargeld = AccountScope.of("Bargeld");
        assertTrue(AccountScope.covers(bargeld, alias("", "Giro", "Bargeld")));
        assertTrue(AccountScope.covers(bargeld, alias("", "Bargeld", "Giro")));
        assertFalse(AccountScope.covers(bargeld, alias("", "Giro", "Depot")));
    }

    @Test
    public void kontengruppeSammeltMehrereKonten() {
        Set<String> gruppe = AccountScope.of(Arrays.asList("Giro", "Bargeld"));
        assertEquals(2, gruppe.size());
        assertTrue(AccountScope.covers(gruppe, buchung("Giro")));
        assertTrue(AccountScope.covers(gruppe, buchung("Bargeld")));
        assertFalse(AccountScope.covers(gruppe, buchung("Depot")));
    }

    @Test
    public void leereNamenUndNullFallenWeg() {
        assertTrue(AccountScope.of((String) null).isEmpty());
        assertTrue(AccountScope.of("   ").isEmpty());
        assertTrue(AccountScope.of(Collections.<String>emptyList()).isEmpty());
        assertEquals(1, AccountScope.of(Arrays.asList("Giro", null, "  ")).size());
    }
}
