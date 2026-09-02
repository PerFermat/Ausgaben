package de.spahr.ausgaben.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Der Rechenkern der Wertpapier-Erfassung: aus den eingegebenen Feldern werden die fehlenden ergänzt.
 * Geprüft werden die Ketten in beide Richtungen, das Nachziehen eines bereits berechneten Werts und die
 * Steuersatz-Vorbelegung der Dividende samt Widerspruchsprobe.
 */
public class SecurityAmountsTest {

    /** Kapitalertragsteuer + Soli. */
    private static final double TAX = 0.26375;

    private static SecurityAmounts.Input in(String action) {
        SecurityAmounts.Input i = new SecurityAmounts.Input();
        i.action = action;
        return i;
    }

    // ---- Kauf / Verkauf ----

    @Test
    public void kauf_ausAnzahlUndStückpreisFolgtDieGesamtsumme() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 20.0;
        i.price = 50.0;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(100000L, (long) r.grossCents);
        assertEquals(100000L, (long) r.netCents); // leeres Gebührenfeld zählt als 0
        assertFalse(r.conflict);
    }

    @Test
    public void kauf_gebührErhöhtDieGesamtsumme() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 20.0;
        i.price = 50.0;
        i.feeCents = 1000L;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(101000L, (long) r.netCents);
    }

    @Test
    public void verkauf_gebührMindertDenErlös() {
        SecurityAmounts.Input i = in("sell");
        i.shares = 20.0;
        i.price = 50.0;
        i.feeCents = 1000L;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(99000L, (long) r.netCents);
    }

    @Test
    public void kauf_korrigierteGesamtsummeZiehtDenStückpreisNach() {
        // Anzahl und Stückpreis stehen, der Nutzer korrigiert die berechnete Gesamtsumme.
        SecurityAmounts.Input i = in("buy");
        i.shares = 20.0;
        i.price = 50.0;
        i.netCents = 101000L;
        i.justEdited = SecurityAmounts.Field.NET;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(SecurityAmounts.Field.PRICE, r.computed);
        assertEquals(50.5, r.price, 1e-9);
        assertEquals(20.0, r.shares, 1e-9);   // die Anzahl bleibt unangetastet
        assertEquals(101000L, (long) r.netCents);
    }

    @Test
    public void kauf_nachgetrageneGebührLässtDieSummeStehenUndPasstDenStückpreisAn() {
        // Fortsetzung des Falls oben: der Stückpreis ist jetzt der berechnete Wert.
        SecurityAmounts.Input i = in("buy");
        i.shares = 20.0;
        i.netCents = 101000L;
        i.feeCents = 1000L;
        i.lastComputed = SecurityAmounts.Field.PRICE;
        i.justEdited = SecurityAmounts.Field.FEE;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(101000L, (long) r.netCents);
        assertEquals(100000L, (long) r.grossCents);
        assertEquals(50.0, r.price, 1e-9);
    }

    @Test
    public void kauf_geänderterStückpreisZiehtDieSummeNach() {
        // Der Stückpreis war das berechnete Feld – korrigiert der Nutzer genau ihn, rückt der Betrag nach.
        SecurityAmounts.Input i = in("buy");
        i.shares = 20.0;
        i.price = 60.0;
        i.netCents = 101000L;
        i.lastComputed = SecurityAmounts.Field.PRICE;
        i.justEdited = SecurityAmounts.Field.PRICE;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(20.0, r.shares, 1e-9);
        assertEquals(60.0, r.price, 1e-9);
        assertEquals(120000L, (long) r.grossCents);
    }

    @Test
    public void kauf_ausSummeUndStückpreisFolgtDieAnzahl() {
        SecurityAmounts.Input i = in("buy");
        i.price = 50.0;
        i.netCents = 100000L;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(20.0, r.shares, 1e-9);
    }

    @Test
    public void kauf_einZelnesFeldReichtNichtAus() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 20.0;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertNull(r.price);
        assertNull(r.grossCents);
        assertNull(r.netCents);
    }

    /**
     * Vorbelegung aus einer Abrechnung: Anzahl, Stückpreis und Summe stehen alle drei im Dokument. Ohne
     * {@code keepGiven} gäbe der Stückpreis nach und würde zurückgerechnet — aus 164,04 würden
     * 1.000,00 ÷ 6,09607 = 164,0401. Diese Zahl steht in der Abrechnung nirgends, und die Auslese könnte
     * daraus später keine Regel mehr lernen.
     */
    @Test
    public void ausDerAbrechnungÜbernommeneWerteVerdrängenEinanderNicht() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 6.09607;
        i.price = 164.04;
        i.netCents = 100000L;
        i.keepGiven = true;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(164.04, r.price, 1e-9);
        assertEquals(6.09607, r.shares, 1e-9);
        assertEquals(100000L, (long) r.netCents);
    }

    /** Ohne das Kennzeichen bleibt es beim bisherigen Verhalten: der Stückpreis gibt nach. */
    @Test
    public void ohneDasKennzeichenGibtDerStückpreisWeiterhinNach() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 6.09607;
        i.price = 164.04;
        i.netCents = 100000L;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(SecurityAmounts.Field.PRICE, r.computed);
        assertEquals(164.0401, r.price, 1e-4);
    }

    /** Auch bei fester Vorbelegung wird ergänzt, was fehlt. */
    @Test
    public void beiFesterVorbelegungWirdFehlendesTrotzdemErgänzt() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 6.09607;
        i.price = 164.04;
        i.keepGiven = true;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(Math.round(6.09607 * 164.04 * 100.0), (long) r.grossCents);
        assertEquals((long) r.grossCents, (long) r.netCents);
    }

    // ---- Dividende ----

    @Test
    public void dividende_bruttoAlleinBelegtSteuerUndNettoÜberDenSteuersatz() {
        SecurityAmounts.Input i = in("dividend");
        i.grossCents = 10000L;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(2638L, (long) r.feeCents);   // 26,375 % von 100,00 €
        assertEquals(7362L, (long) r.netCents);
    }

    /**
     * Rückwärts über den Steuersatz gerechnet trifft man das ursprüngliche Brutto nicht auf den Cent
     * genau – Runden ist nicht umkehrbar. Verlangt ist deshalb: nahe dran und in sich stimmig.
     */
    @Test
    public void dividende_nettoAlleinRechnetAufBruttoZurück() {
        SecurityAmounts.Input i = in("dividend");
        i.netCents = 7362L;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(10000L, r.grossCents, 3.0);
        assertEquals(7362L, r.grossCents - r.feeCents);
    }

    @Test
    public void dividende_steuerAlleinRechnetAufBruttoZurück() {
        SecurityAmounts.Input i = in("dividend");
        i.feeCents = 2638L;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(10000L, r.grossCents, 3.0);
        assertEquals((long) r.grossCents - 2638L, (long) r.netCents);
    }

    @Test
    public void dividende_zweiFelderSchlagenDenSteuersatz() {
        // Brutto und Steuer eingegeben: das Netto folgt exakt, der Steuersatz spielt keine Rolle mehr.
        SecurityAmounts.Input i = in("dividend");
        i.grossCents = 10000L;
        i.feeCents = 1000L;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(9000L, (long) r.netCents);
        assertFalse(r.conflict);
    }

    @Test
    public void dividende_dreiPassendeFelderSindKeinWiderspruch() {
        SecurityAmounts.Input i = in("dividend");
        i.grossCents = 10000L;
        i.feeCents = 2638L;
        i.netCents = 7362L;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertFalse(r.conflict);
    }

    @Test
    public void dividende_dreiUnpassendeFelderMelden() {
        SecurityAmounts.Input i = in("dividend");
        i.grossCents = 10000L;
        i.feeCents = 2638L;
        i.netCents = 5000L;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertTrue(r.conflict);
    }

    @Test
    public void dividende_einCentAbweichungIstNochInOrdnung() {
        SecurityAmounts.Input i = in("dividend");
        i.grossCents = 10000L;
        i.feeCents = 2638L;
        i.netCents = 7363L;
        i.taxRate = TAX;
        assertFalse(SecurityAmounts.solve(i).conflict);
    }

    @Test
    public void dividende_ausAnzahlUndJeStückFolgtBruttoUndDannDieSteuer() {
        SecurityAmounts.Input i = in("dividend");
        i.shares = 100.0;
        i.price = 1.5;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(15000L, (long) r.grossCents);
        assertEquals(3956L, (long) r.feeCents);
        assertEquals(11044L, (long) r.netCents);
    }

    @Test
    public void dividende_ohneSteuersatzBleibenDieAnderenFelderLeer() {
        SecurityAmounts.Input i = in("dividend");
        i.grossCents = 10000L;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertNull(r.feeCents);
        assertNull(r.netCents);
    }

    @Test
    public void dividende_steuerNullIstEineEingabeUndKeinFehlenderWert() {
        SecurityAmounts.Input i = in("dividend");
        i.grossCents = 10000L;
        i.feeCents = 0L;
        i.taxRate = TAX;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(10000L, (long) r.netCents);
    }

    // ---- Rundung ----

    @Test
    public void krummeStückzahlRundetAufCent() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 3.3333;
        i.price = 33.33;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertEquals(Math.round(3.3333 * 33.33 * 100.0), (long) r.grossCents);
    }

    @Test
    public void anzahlNullFührtNichtZurDivisionDurchNull() {
        SecurityAmounts.Input i = in("buy");
        i.shares = 0.0;
        i.netCents = 10000L;
        SecurityAmounts.Result r = SecurityAmounts.solve(i);
        assertNull(r.price);
    }

    /**
     * Die Schwelle, ab der zwei Stückzahlen als gleich und eine Position als geschlossen gilt, hängt an
     * der Zahl der geführten Nachkommastellen — sie ist genau die kleinste darstellbare Einheit.
     *
     * <p>Bis 1.12 stand {@code 1e-6} an vier Stellen wörtlich im Code, ohne dass jemand den
     * Zusammenhang sah. Wer {@code SHARE_DECIMALS} erhöht, muss die Schwelle mitziehen — dieser Test
     * sagt es ihm.</p>
     */
    @Test
    public void dieStueckzahlSchwelleIstDieKleinsteDarstellbareEinheit() {
        assertEquals(Math.pow(10, -de.spahr.ausgaben.settings.MoneyFormat.SHARE_DECIMALS),
                SecurityAmounts.SHARE_EPSILON, 1e-15);
    }
}
