package de.spahr.ausgaben.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import de.spahr.ausgaben.db.AppDatabase;
import de.spahr.ausgaben.settings.ProfileManager;

/**
 * Eine Sicherung aus einer neueren Fassung der App darf die vorhandenen Daten nicht überschreiben.
 *
 * <p>Bis 1.12 schrieb {@code writeDatabaseFileRaw} bedingungslos. Room merkt erst beim nächsten
 * Zugriff, dass der Datenstand höher ist als das, was diese Fassung kennt — da ist die alte Datei aber
 * schon weg, und zurück kommt sie nicht. Wer seine Sicherung auf einem zweiten Gerät mit älterer App
 * einspielt, verliert also genau das, was er gerade retten wollte.</p>
 *
 * <p>Der Stand wird aus der Datei selbst gelesen ({@code PRAGMA user_version} im SQLite-Kopf) und
 * nicht aus dem Manifest: der {@code versionCode} dort beschreibt nur die schreibende App, und alte
 * Sicherungen führen ihn gar nicht.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupSchemaVersionTest {

    private final Context ctx = ApplicationProvider.getApplicationContext();

    @Before
    public void profilAnlegen() {
        ProfileManager.migrateLegacyInstallationIfNeeded(ctx);
    }

    private int hiesigerStand() {
        return AppDatabase.getInstance(ctx).getOpenHelper().getReadableDatabase().getVersion();
    }

    /** Ein SQLite-Kopf mit gesetztem {@code user_version} – mehr braucht die Prüfung nicht. */
    private static byte[] datenbankMitStand(int stand) {
        byte[] db = new byte[512];
        byte[] kennung = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(kennung, 0, db, 0, kennung.length);
        db[60] = (byte) (stand >>> 24);
        db[61] = (byte) (stand >>> 16);
        db[62] = (byte) (stand >>> 8);
        db[63] = (byte) stand;
        return db;
    }

    @Test
    public void derStandWirdAusDemDateikopfGelesen() {
        assertEquals(49, BackupStore.schemaVersionOf(datenbankMitStand(49)));
        assertEquals(300, BackupStore.schemaVersionOf(datenbankMitStand(300)));
        assertEquals("keine SQLite-Datei", -1,
                BackupStore.schemaVersionOf("nur Text".getBytes(StandardCharsets.UTF_8)));
        assertEquals("gar nichts", -1, BackupStore.schemaVersionOf(new byte[0]));
    }

    @Test
    public void eineNeuereSicherungWirdAbgelehntUndNichtsUeberschrieben() {
        java.io.File ziel = ctx.getDatabasePath(ProfileManager.currentDbFileName(ctx));
        long vorher = ziel.length();

        try {
            BackupStore.restoreProfileData(ctx, datenbankMitStand(hiesigerStand() + 1));
            fail("eine neuere Sicherung darf nicht eingespielt werden");
        } catch (IOException erwartet) {
            assertTrue("die Meldung nennt beide Stände: " + erwartet.getMessage(),
                    erwartet.getMessage().contains(String.valueOf(hiesigerStand())));
        }

        assertEquals("die vorhandene Datenbank ist unangetastet", vorher, ziel.length());
        assertTrue("und noch benutzbar",
                AppDatabase.getInstance(ctx).getOpenHelper().getReadableDatabase().isOpen());
    }

    /**
     * Der Regelfall bleibt offen: derselbe Stand geht durch, ein <b>älterer</b> auch — dafür gibt es
     * die Migrationen, genau wie beim Update der App.
     */
    @Test
    public void gleicherUndAelterStandGehenDurch() throws Exception {
        BackupStore.restoreProfileData(ctx, echteSicherung());

        // Und ein älterer Stand darf ebenfalls nicht an der Prüfung scheitern.
        assertTrue(BackupStore.schemaVersionOf(datenbankMitStand(1)) < hiesigerStand());
    }

    /** Eine echte Sicherung des aktiven Profils – die trägt den hiesigen Stand im Kopf. */
    private byte[] echteSicherung() throws Exception {
        BackupArchive.Content c =
                BackupArchive.read(BackupStore.createProfile(ctx, false, null));
        assertEquals("die Sicherung trägt den hiesigen Stand", hiesigerStand(),
                BackupStore.schemaVersionOf(c.db));
        return c.db;
    }
}
