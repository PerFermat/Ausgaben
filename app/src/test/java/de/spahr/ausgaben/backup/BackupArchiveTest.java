package de.spahr.ausgaben.backup;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aufbau der Sicherungsdatei (ZIP mit Manifest, Datenbank(en) und Einstellungen). */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BackupArchiveTest {

    private static final byte[] DB = "SQLite format 3\0…".getBytes(StandardCharsets.UTF_8);

    @Test
    public void datenUndEinstellungenUeberstehenDieRundeProfilUmfang() throws Exception {
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        prefs.put("ausgaben_settings", "{\"currency\":{\"t\":\"s\",\"v\":\"€\"}}");
        prefs.put("ausgaben_places", "{}");
        Map<String, byte[]> dbs = Collections.singletonMap("p1", DB);

        BackupArchive.Content c = BackupArchive.read(
                BackupArchive.write(BackupArchive.SCOPE_PROFILE, dbs, prefs, 9));

        assertTrue(c.hasData());
        assertTrue(c.hasSettings());
        assertFalse(c.isAllProfiles());
        assertArrayEquals(DB, c.db);
        assertEquals(2, c.prefs.size());
        assertTrue(c.prefs("ausgaben_settings").contains("€"));
        assertNull(c.prefs("gibtsnicht"));
    }

    @Test
    public void mehrereDatenbankenUeberstehenDieRundeAlleProfileUmfang() throws Exception {
        Map<String, byte[]> dbs = new LinkedHashMap<>();
        dbs.put("p1", DB);
        dbs.put("p2", "andere db".getBytes(StandardCharsets.UTF_8));

        BackupArchive.Content c = BackupArchive.read(
                BackupArchive.write(BackupArchive.SCOPE_ALL, dbs, new LinkedHashMap<>(), 9));

        assertTrue(c.hasData());
        assertTrue(c.isAllProfiles());
        assertEquals(2, c.dbs.size());
        assertArrayEquals(DB, c.dbs.get("p1"));
    }

    @Test
    public void archivOhneEinstellungenMeldetDasAuch() throws Exception {
        BackupArchive.Content c = BackupArchive.read(
                BackupArchive.write(BackupArchive.SCOPE_PROFILE,
                        Collections.singletonMap("p1", DB), new LinkedHashMap<>(), 9));
        assertTrue(c.hasData());
        assertFalse(c.hasSettings());
    }

    @Test
    public void fremdeDateiWirdAbgelehnt() {
        try {
            BackupArchive.read(DB);   // eine reine Datenbank ist kein ZIP
            fail("ohne Manifest darf nicht gelesen werden");
        } catch (IOException expected) {
            // so soll es sein
        }
    }
}
