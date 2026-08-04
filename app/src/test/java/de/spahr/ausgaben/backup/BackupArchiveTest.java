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
import java.util.LinkedHashMap;

/** Aufbau der Sicherungsdatei (ZIP mit Manifest, Datenbank und Einstellungen). */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BackupArchiveTest {

    private static final byte[] DB = "SQLite format 3\0…".getBytes(StandardCharsets.UTF_8);

    @Test
    public void datenUndEinstellungenUeberstehenDieRunde() throws Exception {
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        prefs.put("ausgaben_settings", "{\"currency\":{\"t\":\"s\",\"v\":\"€\"}}");
        prefs.put("ausgaben_places", "{}");

        BackupArchive.Content c = BackupArchive.read(BackupArchive.write(DB, prefs, 9));

        assertTrue(c.hasData());
        assertTrue(c.hasSettings());
        assertArrayEquals(DB, c.db);
        assertEquals(2, c.prefs.size());
        assertTrue(c.prefs("ausgaben_settings").contains("€"));
        assertNull(c.prefs("gibtsnicht"));
    }

    @Test
    public void archivOhneEinstellungenMeldetDasAuch() throws Exception {
        BackupArchive.Content c = BackupArchive.read(
                BackupArchive.write(DB, new LinkedHashMap<>(), 9));
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
