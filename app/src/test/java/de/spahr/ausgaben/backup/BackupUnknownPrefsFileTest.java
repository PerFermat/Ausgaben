package de.spahr.ausgaben.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import de.spahr.ausgaben.settings.ProfileManager;

/**
 * Eine Einstellungsdatei, die die App nicht kennt, wird beim Einspielen übergangen.
 *
 * <p>Der Name aus dem Archiv wird zum Dateinamen ({@code getSharedPreferences}). {@code BackupArchive}
 * lässt darin keine Pfadangaben mehr durch – ein Name wie {@code fremde_datei} ist aber formal
 * einwandfrei und käme trotzdem als neue Datei im App-Verzeichnis an. Geschrieben wird deshalb nur,
 * was auf einer bekannten Liste steht.</p>
 *
 * <p>Übergangen und nicht abgelehnt: eine Sicherung aus einer neueren Fassung mit einer weiteren
 * Einstellungsdatei soll sich weiterhin einspielen lassen – nur eben ohne den Teil, mit dem diese
 * Fassung nichts anfangen kann.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupUnknownPrefsFileTest {

    private static final String FREMD = "fremde_datei";

    private final Context ctx = ApplicationProvider.getApplicationContext();

    @Before
    public void resetProfileState() {
        ctx.getSharedPreferences("ausgaben_profiles", Context.MODE_PRIVATE).edit().clear().commit();
        ctx.getSharedPreferences("ausgaben_settings", Context.MODE_PRIVATE).edit().clear().commit();
        ctx.getSharedPreferences(FREMD, Context.MODE_PRIVATE).edit().clear().commit();
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
    }

    /** Archiv mit einer bekannten und einer unbekannten Einstellungsdatei. */
    private static byte[] archiv(String scope) throws Exception {
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        prefs.put("ausgaben_settings", PrefsCodec.toJson(
                Collections.<String, Object>singletonMap("currency", "€")));
        prefs.put(FREMD, PrefsCodec.toJson(
                Collections.<String, Object>singletonMap("beute", "hier stand nie etwas")));
        return BackupArchive.write(scope, null, prefs, 13);
    }

    @Test
    public void beimEinspielenEinesProfilsBleibtDieFremdeDateiLeer() throws Exception {
        BackupStore.restoreProfileSettings(ctx, BackupArchive.read(archiv(BackupArchive.SCOPE_PROFILE)));

        assertTrue("die unbekannte Datei wurde nicht geschrieben",
                ctx.getSharedPreferences(FREMD, Context.MODE_PRIVATE).getAll().isEmpty());
        String prefix = "p_" + new ProfileManager(ctx).getActiveProfileId() + "_";
        assertEquals("die bekannte Datei kam durch", "€",
                ctx.getSharedPreferences("ausgaben_settings", Context.MODE_PRIVATE)
                        .getString(prefix + "currency", null));
    }

    @Test
    public void beimEinspielenAllerProfileEbenso() throws Exception {
        BackupStore.restoreAllSettings(ctx, BackupArchive.read(archiv(BackupArchive.SCOPE_ALL)));

        assertTrue("die unbekannte Datei wurde nicht geschrieben",
                ctx.getSharedPreferences(FREMD, Context.MODE_PRIVATE).getAll().isEmpty());
        assertEquals("die bekannte Datei kam durch", "€",
                ctx.getSharedPreferences("ausgaben_settings", Context.MODE_PRIVATE)
                        .getString("currency", null));
    }

    /**
     * Und das Archiv bleibt lesbar – die unbekannte Datei taucht im gelesenen Inhalt auf, nur eben
     * ohne dass daraus eine Datei wird. Sonst hinge der Schutz allein daran, dass niemand später eine
     * weitere Prüfung an der falschen Stelle einbaut.
     */
    @Test
    public void dasArchivBleibtLesbar() throws Exception {
        Map<String, String> gelesen = BackupArchive.read(archiv(BackupArchive.SCOPE_ALL)).prefs;
        assertEquals(2, gelesen.size());
        assertTrue(gelesen.containsKey(FREMD));
    }
}
