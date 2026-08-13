package de.spahr.ausgaben.location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.PayeeCorrection;

/**
 * Die Empfänger in der Nähe – der Vorspann der Vorschlagsliste im Buchungs-Editor: „bei wem stehe ich
 * gerade?"
 *
 * <p>Woher ein Empfänger seine Koordinaten hat, sind dieselben zwei Quellen wie beim Umkreis-Filter
 * der Liste ({@link RadiusFilter}): die GPS-Marke in der Notiz seiner Buchungen und die gelernten
 * Standorte seiner Aliase. Hier gelten sie allerdings <b>zusammen</b> – gesucht ist die kürzeste
 * Entfernung, gleich woher der Punkt stammt.</p>
 *
 * <p>Rein rechnend, ohne Android: die Datenbankabfragen macht der Aufrufer.</p>
 */
public final class NearbyPayees {

    /** Weiter Entferntes taucht nicht auf – die größte Stufe des Umkreis-Knopfes. */
    public static final int MAX_M = 10_000;

    /** So viele Namen stehen höchstens im Vorspann. */
    public static final int LIMIT = 6;

    private NearbyPayees() {
    }

    /**
     * Die nächstgelegenen Empfänger, der nächste zuerst.
     *
     * @param gpsBookings Buchungen mit GPS-Marke in der Notiz (dürfen auch andere enthalten)
     * @param aliases     Aliase; ihre {@link PayeeCorrection#gpsPoints()} zählen für
     *                    {@link PayeeCorrection#corrected}
     * @param maxMeters   Höchstentfernung; darüber fällt ein Empfänger weg
     * @param limit       Länge der Liste
     */
    public static List<String> rank(List<Booking> gpsBookings, List<PayeeCorrection> aliases,
                                    double lat, double lon, int maxMeters, int limit) {
        // Je Empfänger (klein geschrieben) sein nächster Punkt; die Schreibweise kommt vom ersten Fund.
        Map<String, Treffer> beste = new HashMap<>();
        if (gpsBookings != null) {
            for (Booking b : gpsBookings) {
                double[] p = Geo.parse(b == null ? null : b.note);
                if (p != null) {
                    merke(beste, b.payee, Geo.distanceMeters(lat, lon, p[0], p[1]));
                }
            }
        }
        if (aliases != null) {
            for (PayeeCorrection a : aliases) {
                if (a == null) {
                    continue;
                }
                for (double[] p : a.gpsPoints()) {
                    merke(beste, a.corrected, Geo.distanceMeters(lat, lon, p[0], p[1]));
                }
            }
        }

        List<Treffer> treffer = new ArrayList<>();
        for (Treffer t : beste.values()) {
            if (t.meter <= maxMeters) {
                treffer.add(t);
            }
        }
        Collections.sort(treffer, (x, y) -> Double.compare(x.meter, y.meter));

        List<String> out = new ArrayList<>();
        for (Treffer t : treffer) {
            if (out.size() >= limit) {
                break;
            }
            out.add(t.name);
        }
        return out;
    }

    /** Wie {@link #rank} mit {@link #MAX_M} und {@link #LIMIT}. */
    public static List<String> rank(List<Booking> gpsBookings, List<PayeeCorrection> aliases,
                                    double lat, double lon) {
        return rank(gpsBookings, aliases, lat, lon, MAX_M, LIMIT);
    }

    private static void merke(Map<String, Treffer> beste, String payee, double meter) {
        if (payee == null || payee.trim().isEmpty()) {
            return;
        }
        String name = payee.trim();
        String key = name.toLowerCase(Locale.ROOT);
        Treffer da = beste.get(key);
        if (da == null) {
            beste.put(key, new Treffer(name, meter));
        } else if (meter < da.meter) {
            da.meter = meter;
        }
    }

    private static final class Treffer {
        final String name;
        double meter;

        Treffer(String name, double meter) {
            this.name = name;
            this.meter = meter;
        }
    }
}
