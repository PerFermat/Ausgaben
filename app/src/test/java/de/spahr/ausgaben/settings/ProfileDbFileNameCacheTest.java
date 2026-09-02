package de.spahr.ausgaben.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Der Zwischenspeicher hinter {@code currentDbFileName} darf nichts verschlucken.
 *
 * <p>{@code AppDatabase.getInstance} fragt bei jedem Zugriff nach dem Dateinamen des aktiven Profils,
 * um einen Profilwechsel mitzubekommen — bis 1.12 wurde dafür jedes Mal die Profil-Liste aus JSON
 * gelesen. Gespart wird jetzt der Parse; als Schlüssel dienen die beiden Prefs-Werte selbst. Genau das
 * ist hier zu prüfen: dass ein veralteter Name <b>nicht</b> hängenbleibt — sonst arbeitete die App
 * nach einem Profilwechsel auf der Datenbank des vorigen Profils.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ProfileDbFileNameCacheTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    private SharedPreferences profilePrefs() {
        return ctx.getSharedPreferences("ausgaben_profiles", Context.MODE_PRIVATE);
    }

    @Before
    public void frischesProfil() {
        profilePrefs().edit().clear().commit();
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
    }

    @Test
    public void derselbeStandLiefertDenselbenNamen() {
        String einmal = ProfileManager.currentDbFileName(ctx);
        assertEquals(einmal, ProfileManager.currentDbFileName(ctx));
        assertEquals(einmal, ProfileManager.currentDbFileName(ctx));
    }

    @Test
    public void einProfilwechselWirktSofort() {
        String vorher = ProfileManager.currentDbFileName(ctx);
        ProfileManager pm = new ProfileManager(ctx);
        ProfileManager.Profile neu = pm.createProfile("Zweites");

        pm.switchTo(ctx, neu.id);

        String nachher = ProfileManager.currentDbFileName(ctx);
        assertNotEquals("die Datenbank des neuen Profils ist eine andere", vorher, nachher);
        assertEquals(ProfileManager.dbFileNameFor(neu.id), nachher);
    }

    /**
     * Und der Fall, an dem ein naiver Zwischenspeicher zerbräche: das Einspielen einer
     * Alle-Profile-Sicherung schreibt die Prefs-Datei <b>direkt</b> und geht an dieser Klasse vorbei.
     */
    @Test
    public void aucheineAmManagerVorbeiGeschriebeneProfillisteWirkt() {
        ProfileManager.currentDbFileName(ctx);   // füllt den Zwischenspeicher

        String fremdeId = "aaaabbbb-cccc-dddd-eeee-ffff00001111";
        profilePrefs().edit()
                .putString("profiles", "[{\"id\":\"" + fremdeId + "\",\"name\":\"Aus der Sicherung\","
                        + "\"dbFileName\":\"" + ProfileManager.dbFileNameFor(fremdeId) + "\"}]")
                .putString("active_profile_id", fremdeId)
                .commit();

        assertEquals(ProfileManager.dbFileNameFor(fremdeId),
                ProfileManager.currentDbFileName(ctx));
    }

    /**
     * Die verschlüsselte Prefs-Datei wird nur einmal aufgebaut. Jeder Aufbau kostet einen
     * Keystore-Zugriff, und {@code new SettingsStore(…)} entsteht an einigen Stellen pro Abfrage neu.
     */
    @Test
    public void dieVerschluesseltePrefsDateiWirdNurEinmalAufgebaut() {
        assertSame(SettingsStore.secretPrefs(ctx), SettingsStore.secretPrefs(ctx));
    }
}
