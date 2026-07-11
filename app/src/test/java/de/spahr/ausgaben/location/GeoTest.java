package de.spahr.ausgaben.location;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Charakterisierungstests für {@link Geo} – die Standort-Auflösung (AliasResolver.nearest*) baut darauf auf.
 * Halten das aktuelle Verhalten fest, damit spätere Refactorings es nicht unbemerkt verändern.
 */
public class GeoTest {

    @Test
    public void parse_rawPair() {
        assertArrayEquals(new double[]{48.1, 11.5}, Geo.parse("48.1, 11.5"), 1e-9);
    }

    @Test
    public void parse_afterGpsTag() {
        assertArrayEquals(new double[]{48.137, 11.575},
                Geo.parse("Einkauf GPS: 48.137, 11.575"), 1e-9);
    }

    @Test
    public void parse_negativeCoordinates() {
        assertArrayEquals(new double[]{-33.8, 151.2}, Geo.parse("-33.8, 151.2"), 1e-9);
    }

    @Test
    public void parse_nullAndNoMatch() {
        assertNull(Geo.parse(null));
        assertNull(Geo.parse("keine koordinaten"));
        assertNull(Geo.parse("GPS: unlesbar"));
    }

    @Test
    public void distance_zeroForSamePoint() {
        assertEquals(0.0, Geo.distanceMeters(48.1, 11.5, 48.1, 11.5), 1e-6);
    }

    @Test
    public void distance_oneDegreeLatitude() {
        // Ein Grad Breite ≈ r·(π/180) ≈ 111195 m.
        assertEquals(111194.9, Geo.distanceMeters(0, 0, 1, 0), 1.0);
    }

    @Test
    public void distance_isSymmetric() {
        double ab = Geo.distanceMeters(48.10, 11.50, 48.20, 11.60);
        double ba = Geo.distanceMeters(48.20, 11.60, 48.10, 11.50);
        assertEquals(ab, ba, 1e-6);
    }
}
