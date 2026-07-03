package de.spahr.ausgaben.wear;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Bestimmt auf der Uhr den aktuellen Standort (nur Koordinaten), um ihn zum Sprechzeitpunkt an die Buchung
 * anzuhängen. Rein lokal über {@link LocationManager}, kein Netzwerk. {@link #start()} wärmt den Empfang
 * vor, {@link #currentCoordinates()} liefert „lat, lon" oder {@code null}.
 */
public class WearLocation {

    /** Fixe älter als das gelten nicht mehr als „aktuell". */
    private static final long MAX_AGE_MS = 10 * 60 * 1000L;

    private final LocationManager manager;
    private Location latest;

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            latest = location;
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }
    };

    public WearLocation(Context context) {
        manager = (LocationManager) context.getApplicationContext()
                .getSystemService(Context.LOCATION_SERVICE);
    }

    /** Standort-Updates anfordern (GPS + Netzwerk). Ohne Berechtigung/Provider passiert still nichts. */
    public void start() {
        if (manager == null) {
            return;
        }
        requestFrom(LocationManager.NETWORK_PROVIDER);
        requestFrom(LocationManager.GPS_PROVIDER);
    }

    private void requestFrom(String provider) {
        try {
            if (manager.isProviderEnabled(provider)) {
                manager.requestLocationUpdates(provider, 2000L, 5f, listener);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    public void stop() {
        if (manager == null) {
            return;
        }
        try {
            manager.removeUpdates(listener);
        } catch (SecurityException ignored) {
        }
    }

    /** Bester aktueller Fix als „lat, lon" (6 Nachkommastellen) oder {@code null}. */
    @Nullable
    public String currentCoordinates() {
        Location best = latest;
        best = newer(best, lastKnown(LocationManager.GPS_PROVIDER));
        best = newer(best, lastKnown(LocationManager.NETWORK_PROVIDER));
        if (best == null || System.currentTimeMillis() - best.getTime() > MAX_AGE_MS) {
            return null;
        }
        return String.format(Locale.US, "%.6f, %.6f", best.getLatitude(), best.getLongitude());
    }

    @Nullable
    private Location lastKnown(String provider) {
        if (manager == null) {
            return null;
        }
        try {
            return manager.getLastKnownLocation(provider);
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Location newer(Location a, Location b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.getTime() > a.getTime() ? b : a;
    }
}
