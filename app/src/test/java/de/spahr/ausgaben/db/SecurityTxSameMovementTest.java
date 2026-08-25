package de.spahr.ausgaben.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

/**
 * Wann zwei Depot-Bewegungen dieselbe sind — die Grundlage des Hinweises auf eine doppelt eingelesene
 * Abrechnung.
 *
 * <p>Der Vergleich muss zweierlei aushalten: dieselbe Buchung aus zwei Quellen (KMyMoney-Import und
 * Abrechnung) darf nicht an der Uhrzeit scheitern, und ein einziger Cent Unterschied darf nicht
 * durchrutschen — sonst würde eine echte zweite Buchung verschluckt.</p>
 */
public class SecurityTxSameMovementTest {

    private static long tag(int jahr, int monat, int tag, int stunde) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(jahr, monat - 1, tag, stunde, 0, 0);
        return c.getTimeInMillis();
    }

    private static SecurityTx kauf(long datum) {
        SecurityTx tx = new SecurityTx("Depot", "S000001", "Vanguard All-World",
                datum, "buy", 6.09607, 100_000L, 100_000L, 490L);
        return tx;
    }

    @Test
    public void gleicheWerteSindDieselbeBewegung() {
        assertTrue(kauf(tag(2025, 8, 14, 0)).sameMovement(kauf(tag(2025, 8, 14, 0))));
    }

    @Test
    public void andereUhrzeitAmSelbenTagAendertNichts() {
        assertTrue(kauf(tag(2025, 8, 14, 0)).sameMovement(kauf(tag(2025, 8, 14, 17))));
    }

    @Test
    public void andererTagIstEineAndereBewegung() {
        assertFalse(kauf(tag(2025, 8, 14, 0)).sameMovement(kauf(tag(2025, 8, 15, 0))));
    }

    @Test
    public void einCentUnterschiedZaehlt() {
        SecurityTx andere = kauf(tag(2025, 8, 14, 0));
        andere.amountCents = 100_001L;
        assertFalse(kauf(tag(2025, 8, 14, 0)).sameMovement(andere));
    }

    @Test
    public void andereGebuehrZaehlt() {
        SecurityTx andere = kauf(tag(2025, 8, 14, 0));
        andere.feeCents = 0L;
        assertFalse(kauf(tag(2025, 8, 14, 0)).sameMovement(andere));
    }

    @Test
    public void andereStueckzahlZaehlt() {
        SecurityTx andere = kauf(tag(2025, 8, 14, 0));
        andere.shares = 6.09608;
        assertFalse(kauf(tag(2025, 8, 14, 0)).sameMovement(andere));
    }

    @Test
    public void einAnderesDepotIstEineAndereBewegung() {
        SecurityTx andere = kauf(tag(2025, 8, 14, 0));
        andere.depot = "Zweitdepot";
        assertFalse(kauf(tag(2025, 8, 14, 0)).sameMovement(andere));
    }

    /**
     * Was die Bewegung nicht ausmacht: dass sie noch auf den Export wartet, über welches Konto sie
     * läuft und welche Kategorie daran hängt. Sonst meldete sich eine gerade erfasste Buchung beim
     * nächsten Einlesen nicht als das, was sie ist.
     */
    @Test
    public void kontoKategorieUndVormerkungZaehlenNicht() {
        SecurityTx andere = kauf(tag(2025, 8, 14, 0));
        andere.id = 42;
        andere.pending = true;
        andere.moneyAccount = "Anderes Konto";
        andere.feeCategory = "Gebühren";
        assertTrue(kauf(tag(2025, 8, 14, 0)).sameMovement(andere));
    }
}
