package de.spahr.ausgaben.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.PayeeCorrection;

/**
 * Der Vorspann der Empfängerliste ({@link NearbyPayees}): wer steht oben und in welcher Reihenfolge.
 */
public class NearbyPayeesTest {

    /** Mittelpunkt für alle Proben. */
    private static final double LAT = 48.0;
    private static final double LON = 11.0;

    /** Ein Punkt {@code meter} nördlich des Mittelpunkts (ein Breitengrad ≈ 111.320 m). */
    private static double[] nordlich(double meter) {
        return new double[]{LAT + meter / 111_320.0, LON};
    }

    private static Booking buchung(String payee, double meter) {
        Booking b = new Booking();
        b.payee = payee;
        double[] p = nordlich(meter);
        b.note = "Einkauf GPS: " + p[0] + ", " + p[1];
        return b;
    }

    private static PayeeCorrection alias(String corrected, double meter) {
        PayeeCorrection a = new PayeeCorrection();
        a.corrected = corrected;
        a.setGpsPoints(Collections.singletonList(nordlich(meter)));
        return a;
    }

    private static List<String> rank(List<Booking> buchungen, List<PayeeCorrection> aliase) {
        return NearbyPayees.rank(buchungen, aliase, LAT, LON);
    }

    @Test
    public void derNaechsteStehtVorn() {
        List<String> nah = rank(Arrays.asList(
                buchung("Fern", 900),
                buchung("Nah", 50),
                buchung("Mittel", 300)), null);
        assertEquals(Arrays.asList("Nah", "Mittel", "Fern"), nah);
    }

    @Test
    public void jederEmpfaengerNurEinmalMitSeinemNaechstenPunkt() {
        List<String> nah = rank(Arrays.asList(
                buchung("REWE", 4000),
                buchung("Bäcker", 800),
                buchung("REWE", 200)), null);
        assertEquals(Arrays.asList("REWE", "Bäcker"), nah);
    }

    @Test
    public void aliasStandorteZaehlenGenauso() {
        List<String> nah = rank(
                Collections.singletonList(buchung("Bäcker", 800)),
                Collections.singletonList(alias("Apotheke", 100)));
        assertEquals(Arrays.asList("Apotheke", "Bäcker"), nah);
    }

    @Test
    public void aliasKannDenEigenenBuchungspunktUnterbieten() {
        List<String> nah = rank(
                Arrays.asList(buchung("REWE", 5000), buchung("Bäcker", 800)),
                Collections.singletonList(alias("REWE", 100)));
        assertEquals(Arrays.asList("REWE", "Bäcker"), nah);
    }

    @Test
    public void wasZuWeitWegIstFaelltRaus() {
        List<String> nah = rank(Arrays.asList(
                buchung("Hier", 500),
                buchung("Nachbarstadt", NearbyPayees.MAX_M + 1000)), null);
        assertEquals(Collections.singletonList("Hier"), nah);
    }

    @Test
    public void hoechstensSechs() {
        List<Booking> viele = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            viele.add(buchung("Laden " + i, 100 + i));
        }
        List<String> nah = rank(viele, null);
        assertEquals(NearbyPayees.LIMIT, nah.size());
        assertEquals("Laden 0", nah.get(0));
        assertEquals("Laden 5", nah.get(5));
    }

    @Test
    public void ohneKoordinatenUndOhneNamenBleibtNichts() {
        Booking ohneGps = new Booking();
        ohneGps.payee = "Ohne Ort";
        ohneGps.note = "nur eine Notiz";
        Booking ohneNamen = buchung("   ", 50);
        assertTrue(rank(Arrays.asList(ohneGps, ohneNamen), null).isEmpty());
        assertTrue(rank(null, null).isEmpty());
    }

    @Test
    public void grossKleinSchreibungFasstZusammen() {
        List<String> nah = rank(Arrays.asList(
                buchung("Bäcker", 300),
                buchung("bäcker", 100)), null);
        assertEquals(Collections.singletonList("Bäcker"), nah);
    }
}
