package de.spahr.ausgaben.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Reihenfolge der Konten und Kontenarten sowie die Suche in der Kontenschublade. */
public class AccountOrderTest {

    private static Account account(String name, int sortPos) {
        Account a = new Account(name);
        a.sortPos = sortPos;
        return a;
    }

    // ---- Kontenarten ----

    @Test
    public void kindSequenceOhneEintraegeIstDieVorgabe() {
        assertArrayEquals(new int[]{AccountKind.ASSET, AccountKind.LIABILITY, AccountKind.DEPOT},
                AccountOrder.kindSequence(Collections.emptyList()));
    }

    @Test
    public void kindSequenceFolgtDenGespeichertenPlaetzen() {
        List<AccountKindOrder> stored = Arrays.asList(
                new AccountKindOrder(AccountKind.DEPOT, 0),
                new AccountKindOrder(AccountKind.LIABILITY, 1),
                new AccountKindOrder(AccountKind.ASSET, 2));
        assertArrayEquals(new int[]{AccountKind.DEPOT, AccountKind.LIABILITY, AccountKind.ASSET},
                AccountOrder.kindSequence(stored));
    }

    @Test
    public void nichtGespeicherteKontenartenHaengenHintenAn() {
        List<AccountKindOrder> stored =
                Collections.singletonList(new AccountKindOrder(AccountKind.DEPOT, 0));
        assertArrayEquals(new int[]{AccountKind.DEPOT, AccountKind.ASSET, AccountKind.LIABILITY},
                AccountOrder.kindSequence(stored));
    }

    // ---- Konten innerhalb einer Kontenart ----

    @Test
    public void ohneSortierungEntscheidetDerName() {
        List<Account> accounts = new ArrayList<>(Arrays.asList(
                account("Tagesgeld", 0), account("bargeld", 0), account("Girokonto", 0)));
        AccountOrder.sortWithinKind(accounts);
        assertEquals("bargeld", accounts.get(0).name);
        assertEquals("Girokonto", accounts.get(1).name);
        assertEquals("Tagesgeld", accounts.get(2).name);
    }

    @Test
    public void sortierplatzSchlaegtDenNamen() {
        List<Account> accounts = new ArrayList<>(Arrays.asList(
                account("Zweitkonto", 1), account("Ampel", 2)));
        AccountOrder.sortWithinKind(accounts);
        assertEquals("Zweitkonto", accounts.get(0).name);
        assertEquals("Ampel", accounts.get(1).name);
    }

    @Test
    public void renumberVergibtLueckenloseePlaetzeAbEins() {
        List<Account> accounts = Arrays.asList(
                account("A", 0), account("B", 0), account("C", 0));
        List<Account> changed = AccountOrder.renumber(accounts);
        assertEquals(3, changed.size());
        assertEquals(1, accounts.get(0).sortPos);
        assertEquals(2, accounts.get(1).sortPos);
        assertEquals(3, accounts.get(2).sortPos);
    }

    @Test
    public void renumberMeldetNurTatsaechlichVerschobeneKonten() {
        List<Account> accounts = Arrays.asList(
                account("A", 1), account("B", 2), account("C", 9));
        List<Account> changed = AccountOrder.renumber(accounts);
        assertEquals(1, changed.size());
        assertEquals("C", changed.get(0).name);
        assertEquals(3, changed.get(0).sortPos);
    }

    // ---- Suche ----

    @Test
    public void sucheFindetTeiltrefferUnabhaengigVonGrossKlein() {
        assertTrue(AccountOrder.matches("Sparkasse", "kasse"));
        assertTrue(AccountOrder.matches("Barkasse", "KASSE"));
        assertTrue(AccountOrder.matches("Kassenbank", " kasse "));
        assertFalse(AccountOrder.matches("Girokonto", "kasse"));
    }

    @Test
    public void leereSucheZeigtAlles() {
        assertTrue(AccountOrder.matches("Girokonto", ""));
        assertTrue(AccountOrder.matches("Girokonto", null));
        assertTrue(AccountOrder.matches("Girokonto", "   "));
    }

    // ---- Reihenfolge der Auswahlfelder ----

    /** Die Kontenliste, wie sie aus der Datenbank kommt: nach Sortierplatz, dann nach Name. */
    private static final List<String> KONTEN = Arrays.asList(
            "Gemeinschaftskonto", "Girokonto", "Tagesgeld", "Visa", "ETF Depot");

    @Test
    public void forPickerStelltFavoritenVorDieGruppeUndDieGruppeVorDenRest() {
        List<String> favoriten = Arrays.asList("Girokonto", "Visa");
        List<String> gruppe = Arrays.asList("Gemeinschaftskonto", "Visa");

        assertEquals(Arrays.asList("Girokonto", "Visa", "Gemeinschaftskonto", "Tagesgeld", "ETF Depot"),
                AccountOrder.forPicker(KONTEN, favoriten, gruppe));
    }

    @Test
    public void forPickerZeigtKeinKontoZweimal() {
        List<String> ergebnis = AccountOrder.forPicker(KONTEN,
                Arrays.asList("Girokonto", "Visa"), Arrays.asList("Gemeinschaftskonto", "Visa"));

        assertEquals("Visa steht nur einmal da", 1, Collections.frequency(ergebnis, "Visa"));
        assertEquals("und kein Konto geht verloren", KONTEN.size(), ergebnis.size());
    }

    @Test
    public void forPickerLaesstDieListeInRuheWennEsWederFavoritenNochGruppeGibt() {
        assertEquals(KONTEN, AccountOrder.forPicker(KONTEN, Collections.emptyList(), null));
    }

    /**
     * Ein Favorit, der inzwischen geschlossen ist oder die Trägerzeile eines Depots, steht gar nicht
     * erst in der Kontenliste – und darf sie über den Umweg der Favoriten auch nicht betreten.
     */
    @Test
    public void forPickerHoltKeinKontoHerein() {
        List<String> ergebnis = AccountOrder.forPicker(KONTEN,
                Arrays.asList("Altes Sparbuch", "Visa"), Collections.emptyList());

        assertFalse(ergebnis.contains("Altes Sparbuch"));
        assertEquals(Arrays.asList("Visa", "Gemeinschaftskonto", "Girokonto", "Tagesgeld", "ETF Depot"),
                ergebnis);
    }

    @Test
    public void forPickerBehaeltInnerhalbJedesBlocksDieVorgegebeneReihenfolge() {
        List<String> ergebnis = AccountOrder.forPicker(KONTEN,
                Arrays.asList("Visa", "Girokonto"), Arrays.asList("Tagesgeld", "Gemeinschaftskonto"));

        assertEquals(Arrays.asList("Visa", "Girokonto", "Tagesgeld", "Gemeinschaftskonto", "ETF Depot"),
                ergebnis);
    }
}
