package de.spahr.ausgaben.location;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kleine Geo-Hilfen für die Betrag-only-Erfassung: Koordinaten aus Text lesen (Buchungsnotiz „…GPS: lat,
 * lon" oder rohes „lat, lon" der Uhr/{@link LocationTagger}) und die Entfernung zweier Punkte in Metern.
 */
public final class Geo {

    /** Suchradius für einen Standort-Treffer. */
    public static final double RADIUS_M = 100.0;

    private static final Pattern PAIR = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)");

    private Geo() {
    }

    /** {@code {lat, lon}} aus dem Text (bevorzugt nach „GPS:"), sonst {@code null}. */
    @Nullable
    public static double[] parse(String s) {
        if (s == null) {
            return null;
        }
        String text = s;
        int gps = s.lastIndexOf("GPS:");
        if (gps >= 0) {
            text = s.substring(gps + 4);
        }
        Matcher m = PAIR.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return new double[]{Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Entfernung in Metern (Haversine). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
