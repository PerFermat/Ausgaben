package de.spahr.ausgaben.location;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Der Umkreis-Filter der Buchungsliste: „Was habe ich hier schon ausgegeben?"
 *
 * <p>Der Knopf im Filterdialog schaltet der Reihe nach durch {@link #STUFEN}; steht er auf einer
 * Stufe, bleiben nur Buchungen in diesem Umkreis um die eigene Position stehen. Woher eine Buchung
 * ihre Koordinaten hat, entscheidet {@link #matches}:</p>
 *
 * <ol>
 *   <li>aus der eigenen Notiz („GPS: lat, lon"), sonst</li>
 *   <li>aus den gelernten Standorten des Alias ihres Empfängers.</li>
 * </ol>
 *
 * <p>Ohne bekannte eigene Position bleibt nichts übrig – die Titelzeile zeigt dann „Filter aktiv (0)“,
 * und daran ist zu sehen, daß nur nichts in der Nähe liegt.</p>
 */
public final class RadiusFilter {

    /** Die Stufen des Knopfes in Metern, in der Reihenfolge des Durchschaltens; 0 = aus. */
    public static final int[] STUFEN = {0, 100, 500, 1000, 10_000};

    private RadiusFilter() {
    }

    /** Die nächste Stufe; hinter der letzten steht wieder „aus". */
    public static int next(int radiusM) {
        for (int i = 0; i < STUFEN.length; i++) {
            if (STUFEN[i] == radiusM) {
                return STUFEN[(i + 1) % STUFEN.length];
            }
        }
        return 0;
    }

    /** Die Stufe als Text: „100 m“, „1 km“. Meter und Kilometer heißen in beiden Sprachen gleich. */
    public static String label(int radiusM) {
        if (radiusM >= 1000) {
            return String.format(Locale.ROOT, "%d km", radiusM / 1000);
        }
        return String.format(Locale.ROOT, "%d m", radiusM);
    }

    /**
     * Liegt die Buchung im Umkreis?
     *
     * @param center      eigene Position {@code {lat, lon}} oder {@code null} (dann nie)
     * @param radiusM     Umkreis in Metern; 0 = aus (dann immer, der Aufrufer fragt gar nicht erst)
     * @param note        Buchungsnotiz, aus der die Koordinaten gelesen werden
     * @param aliasPoints Standorte des Alias ihres Empfängers – gelten nur ohne eigene Koordinaten
     */
    public static boolean matches(@Nullable double[] center, int radiusM,
                                  @Nullable String note, @Nullable List<double[]> aliasPoints) {
        if (radiusM <= 0) {
            return true;
        }
        if (center == null) {
            return false;
        }
        double[] eigene = Geo.parse(note);
        if (eigene != null) {
            // Die Buchung weiß selbst, wo sie entstanden ist – der Alias hat dann nichts zu sagen.
            return Geo.distanceMeters(center[0], center[1], eigene[0], eigene[1]) <= radiusM;
        }
        if (aliasPoints == null) {
            return false;
        }
        for (double[] p : aliasPoints) {
            if (Geo.distanceMeters(center[0], center[1], p[0], p[1]) <= radiusM) {
                return true;
            }
        }
        return false;
    }
}
