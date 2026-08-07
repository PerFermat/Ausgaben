package de.spahr.ausgaben.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Einordnung der KMyMoney-Kontotypen in die drei Kontenarten. Neu ist Typ 7: die Trägerzeile eines
 * Depots, die weder als Anlage- noch als Verbindlichkeitskonto zählen darf.
 */
public class AccountKindTest {

    @Test
    public void verbindlichkeitenSindVierFuenfUndZehn() {
        assertEquals(AccountKind.LIABILITY, AccountKind.of(4));
        assertEquals(AccountKind.LIABILITY, AccountKind.of(5));
        assertEquals(AccountKind.LIABILITY, AccountKind.of(10));
    }

    @Test
    public void siebenIstEinDepot() {
        assertEquals(AccountKind.DEPOT, AccountKind.of(Account.KMY_TYPE_DEPOT));
        assertEquals(7, Account.KMY_TYPE_DEPOT);
    }

    @Test
    public void allesUebrigeIstAnlage() {
        assertEquals(AccountKind.ASSET, AccountKind.of(0));
        assertEquals(AccountKind.ASSET, AccountKind.of(1));
        assertEquals(AccountKind.ASSET, AccountKind.of(9));
    }

    @Test
    public void depotIstWederAnlageNochVerbindlichkeit() {
        Account depot = new Account("ETF Depot");
        depot.kmyType = Account.KMY_TYPE_DEPOT;
        assertTrue(depot.isDepot());
        assertFalse(depot.isLiability());

        Account giro = new Account("Girokonto");
        assertFalse(giro.isDepot());
        assertFalse(giro.isLiability());
    }
}
