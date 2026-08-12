package de.spahr.ausgaben.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * Der Umkreis-Filter ({@link RadiusFilter}): die Stufen des Knopfes und wer im Kreis liegt.
 */
public class RadiusFilterTest {

    /** Mittelpunkt für alle Proben. */
    private static final double[] HIER = {48.0, 11.0};

    /** Ein Punkt {@code meter} nördlich des Mittelpunkts (ein Breitengrad ≈ 111.320 m). */
    private static double[] nordlich(double meter) {
        return new double[]{HIER[0] + meter / 111_320.0, HIER[1]};
    }

    private static String notizMit(double[] p) {
        return "Einkauf GPS: " + p[0] + ", " + p[1];
    }

    private static List<double[]> aliasBei(double[] p) {
        return Collections.singletonList(p);
    }

    @Test
    public void stufenLaufenImKreis() {
        assertEquals(100, RadiusFilter.next(0));
        assertEquals(500, RadiusFilter.next(100));
        assertEquals(1000, RadiusFilter.next(500));
        assertEquals(10_000, RadiusFilter.next(1000));
        assertEquals(0, RadiusFilter.next(10_000));
    }

    @Test
    public void beschriftungInMeterUndKilometer() {
        assertEquals("100 m", RadiusFilter.label(100));
        assertEquals("500 m", RadiusFilter.label(500));
        assertEquals("1 km", RadiusFilter.label(1000));
        assertEquals("10 km", RadiusFilter.label(10_000));
    }

    @Test
    public void nurWasImUmkreisLiegt() {
        assertTrue(RadiusFilter.matches(HIER, 100, notizMit(nordlich(80)), null));
        assertFalse(RadiusFilter.matches(HIER, 100, notizMit(nordlich(300)), null));
        assertTrue(RadiusFilter.matches(HIER, 500, notizMit(nordlich(300)), null));
    }

    @Test
    public void aliasSpringtEin() {
        // Buchung ohne eigenes GPS: der gelernte Standort ihres Empfängers zählt.
        assertTrue(RadiusFilter.matches(HIER, 100, "Bäcker", aliasBei(nordlich(50))));
        assertFalse(RadiusFilter.matches(HIER, 100, "Bäcker", aliasBei(nordlich(4000))));
    }

    @Test
    public void eigeneKoordinatenSchlagenDenAlias() {
        // Die Buchung ist woanders entstanden – daß der Empfänger auch hier eine Filiale hat, hilft nicht.
        assertFalse(RadiusFilter.matches(HIER, 100, notizMit(nordlich(4000)), aliasBei(nordlich(10))));
    }

    @Test
    public void ohneKoordinatenKeinTreffer() {
        assertFalse(RadiusFilter.matches(HIER, 100, "Bäcker", null));
        assertFalse(RadiusFilter.matches(HIER, 100, null, Collections.emptyList()));
    }

    @Test
    public void ohneStandortBleibtNichtsUebrig() {
        assertFalse(RadiusFilter.matches(null, 100, notizMit(HIER), aliasBei(HIER)));
    }

    /** Ausgeschalteter Umkreis läßt alles durch – auch Buchungen ganz ohne Koordinaten. */
    @Test
    public void ausLaesstAllesDurch() {
        assertTrue(RadiusFilter.matches(null, 0, "Bäcker", null));
    }
}
