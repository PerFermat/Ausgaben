package de.spahr.ausgaben.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Die mengengewichtete Aufteilung des Fortschritts ({@link ImportBudget}).
 */
public class ImportBudgetTest {

    private static final String LESEN = "bookings.read";
    private static final String SCHREIBEN = "bookings.write";
    private static final String DEPOT_LESEN = "depot.read.Depot";
    private static final String DEPOT_SCHREIBEN = "depot.write.Depot";

    /** Ein Depot-Neueinlesen: keine Buchungsphase, also gehört dem Depot der ganze Rest. */
    @Test
    public void nurEinDepotBekommtDenGanzenRest() {
        ImportBudget b = new ImportBudget();
        b.add(DEPOT_LESEN, 8000 * ImportBudget.DEPOT_READ);
        b.add(DEPOT_SCHREIBEN, 5000 * ImportBudget.PRICE_WRITE);
        assertEquals(ImportBudget.HEAD_END, b.from(DEPOT_LESEN));
        assertEquals(ImportBudget.TAIL_END, b.to(DEPOT_SCHREIBEN));
        assertEquals(b.to(DEPOT_LESEN), b.from(DEPOT_SCHREIBEN));
    }

    @Test
    public void vieleBuchungenUndWenigKurseVerschiebenDieGrenzen() {
        ImportBudget b = new ImportBudget();
        b.add(LESEN, 10000 * ImportBudget.BOOKING_READ);
        b.add(SCHREIBEN, 10000 * ImportBudget.BOOKING_WRITE);
        b.add(DEPOT_SCHREIBEN, 100 * ImportBudget.PRICE_WRITE);
        // Die Buchungen tragen fast alles – dem Depot bleibt nur ein schmaler Streifen am Ende.
        assertTrue(b.from(DEPOT_SCHREIBEN) > 95);
    }

    @Test
    public void vieleKurseHolenSichIhrenAnteil() {
        ImportBudget b = new ImportBudget();
        b.add(LESEN, 1000 * ImportBudget.BOOKING_READ);
        b.add(SCHREIBEN, 1000 * ImportBudget.BOOKING_WRITE);
        b.add(DEPOT_SCHREIBEN, 20000 * ImportBudget.PRICE_WRITE);
        // 4000 Einheiten Kurse gegen 2000 Einheiten Buchungen: das Depot bekommt zwei Drittel.
        assertEquals(63, b.from(DEPOT_SCHREIBEN));
        assertEquals(ImportBudget.TAIL_END, b.to(DEPOT_SCHREIBEN));
    }

    @Test
    public void baenderStossenLueckenlosAneinanderUndSteigen() {
        ImportBudget b = new ImportBudget();
        b.add(LESEN, 500);
        b.add(SCHREIBEN, 500);
        b.add(DEPOT_LESEN, 500);
        b.add(DEPOT_SCHREIBEN, 500);
        String[] alle = {LESEN, SCHREIBEN, DEPOT_LESEN, DEPOT_SCHREIBEN};
        int vorher = ImportBudget.HEAD_END;
        for (String k : alle) {
            assertEquals(vorher, b.from(k));
            assertTrue(b.to(k) > b.from(k));
            vorher = b.to(k);
        }
        assertEquals(ImportBudget.TAIL_END, vorher);
    }

    /** Ein Lauf ohne Kurse: der Schritt bekommt kein Band und wird beim Anzeigen übersprungen. */
    @Test
    public void schrittOhneArbeitBekommtKeinBand() {
        ImportBudget b = new ImportBudget();
        b.add(LESEN, 1000);
        b.add(DEPOT_SCHREIBEN, 0);
        assertEquals(ImportBudget.TAIL_END, b.from(DEPOT_SCHREIBEN));
        assertEquals(ImportBudget.TAIL_END, b.to(DEPOT_SCHREIBEN));
    }

    @Test
    public void resizeVorDemStartVerschiebtDieBaender() {
        ImportBudget b = new ImportBudget();
        b.add(LESEN, 1000);
        b.add(SCHREIBEN, 1000);
        b.resize(SCHREIBEN, 3000);
        // Das Lesen trägt jetzt nur noch ein Viertel: 45 + 54/4 ≈ 59.
        assertEquals(59, b.from(SCHREIBEN));
    }

    /** Was schon läuft, bleibt stehen – sonst spränge die Anzeige mitten in der Phase zurück. */
    @Test
    public void resizeNachDemStartWirktNichtMehr() {
        ImportBudget b = new ImportBudget();
        b.add(LESEN, 1000);
        b.add(SCHREIBEN, 1000);
        int von = b.from(SCHREIBEN);
        b.resize(SCHREIBEN, 9000);
        assertEquals(von, b.from(SCHREIBEN));
    }

    @Test
    public void unbekannterSchluesselLandetAmEnde() {
        ImportBudget b = new ImportBudget();
        b.add(LESEN, 1000);
        assertEquals(ImportBudget.TAIL_END, b.from("gibt.es.nicht"));
    }
}
