package de.spahr.ausgaben.settings;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet die Definition der Bargeld-Orte (geordnete Namensliste + Standardort) in SharedPreferences.
 * Liegt bewusst außerhalb der Room-Datenbank, damit die Orte „Datenbank zurücksetzen" und Importe überstehen.
 * Die Salden/Bewegungen der Orte stehen separat im Room-Journal {@code place_entry}.
 */
public class PlacesStore {

    public static final String NO_PLACE = "ohne Ort";

    private static final String PREFS = "ausgaben_places";
    private static final String KEY_PLACES = "places";
    private static final String KEY_DEFAULT = "default_place";

    private final SharedPreferences prefs;

    public PlacesStore(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Geordnete Liste der definierten Orte (ohne „ohne Ort"). */
    public List<String> getPlaces() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_PLACES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.optString(i, "").trim();
                if (!name.isEmpty() && !result.contains(name)) {
                    result.add(name);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void savePlaces(List<String> places) {
        JSONArray arr = new JSONArray();
        for (String p : places) {
            arr.put(p);
        }
        prefs.edit().putString(KEY_PLACES, arr.toString()).apply();
    }

    public void addPlace(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return;
        }
        List<String> places = getPlaces();
        if (!places.contains(n)) {
            places.add(n);
            savePlaces(places);
        }
    }

    public void removePlace(String name) {
        List<String> places = getPlaces();
        if (places.remove(name)) {
            savePlaces(places);
            if (name != null && name.equals(getDefaultPlace())) {
                setDefaultPlace(places.isEmpty() ? "" : places.get(0));
            }
        }
    }

    public void renamePlace(String oldName, String newName) {
        String n = newName == null ? "" : newName.trim();
        if (n.isEmpty()) {
            return;
        }
        List<String> places = getPlaces();
        int idx = places.indexOf(oldName);
        if (idx >= 0 && !places.contains(n)) {
            places.set(idx, n);
            savePlaces(places);
            if (oldName != null && oldName.equals(getDefaultPlace())) {
                setDefaultPlace(n);
            }
        }
    }

    /** Standardort für neue Buchungen (leer = „ohne Ort"). */
    public String getDefaultPlace() {
        return prefs.getString(KEY_DEFAULT, "").trim();
    }

    public void setDefaultPlace(String name) {
        prefs.edit().putString(KEY_DEFAULT, name == null ? "" : name.trim()).apply();
    }
}
