package de.spahr.ausgaben.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import de.spahr.ausgaben.R;

/**
 * Karten-Auswahl (OpenStreetMap/osmdroid, kein API-Key): zeigt die Karte mit einem festen Fadenkreuz in der
 * Mitte; „Übernehmen" liefert die Koordinaten der Kartenmitte zurück. Startzentrum sind die übergebenen
 * Koordinaten, sonst die letzte bekannte Position, sonst ein neutraler Default.
 */
public class MapPickerActivity extends LocalizedActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";
    /** Nur-Ansicht: feste Markierung an den übergebenen Koordinaten, keine Auswahl. */
    public static final String EXTRA_VIEW_ONLY = "view_only";

    private MapView map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // osmdroid-Konfiguration vor dem Aufbau der MapView setzen (User-Agent gegen Tile-Server-Sperren).
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map_picker);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);

        GeoPoint start = startPoint();
        map.getController().setZoom(start == DEFAULT ? 5.0 : 16.0);
        map.getController().setCenter(start);

        boolean viewOnly = getIntent().getBooleanExtra(EXTRA_VIEW_ONLY, false);
        MaterialButton btnPick = findViewById(R.id.btnPickHere);
        if (viewOnly) {
            // Nur-Ansicht: feste Markierung am Ort, kein Fadenkreuz/„Übernehmen" (keine Auswahl).
            findViewById(R.id.crosshair).setVisibility(android.view.View.GONE);
            btnPick.setVisibility(android.view.View.GONE);
            org.osmdroid.views.overlay.Marker marker = new org.osmdroid.views.overlay.Marker(map);
            marker.setPosition(start);
            marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                    org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM);
            marker.setDraggable(false);
            map.getOverlays().add(marker);
        } else {
            btnPick.setOnClickListener(v -> {
                IGeoPoint c = map.getMapCenter();
                Intent data = new Intent();
                data.putExtra(EXTRA_LAT, c.getLatitude());
                data.putExtra(EXTRA_LON, c.getLongitude());
                setResult(RESULT_OK, data);
                finish();
            });
        }
    }

    /** Etwa Deutschland-Mitte als neutraler Startpunkt, wenn nichts anderes bekannt ist. */
    private static final GeoPoint DEFAULT = new GeoPoint(51.0, 10.0);

    private GeoPoint startPoint() {
        double lat = getIntent().getDoubleExtra(EXTRA_LAT, 0);
        double lon = getIntent().getDoubleExtra(EXTRA_LON, 0);
        if (lat != 0 || lon != 0) {
            return new GeoPoint(lat, lon);
        }
        Location last = lastKnownLocation();
        if (last != null) {
            return new GeoPoint(last.getLatitude(), last.getLongitude());
        }
        return DEFAULT;
    }

    private Location lastKnownLocation() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) {
                return null;
            }
            Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            return gps != null ? gps : lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException e) {
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            map.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            map.onPause();
        }
    }
}
