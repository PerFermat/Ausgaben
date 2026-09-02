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

    /**
     * Ein Eintragsname aus dem Archiv wird beim Einspielen zum Dateinamen: die Profil-ID geht über
     * {@code ProfileManager.dbFileNameFor} in {@code getDatabasePath}, der Name der Einstellungsdatei
     * direkt in {@code getSharedPreferences}. Mit {@code ..} im Namen schriebe ein untergeschobenes
     * Archiv außerhalb des vorgesehenen Ordners. Die App nimmt die Datei über einen Dateiwähler
     * entgegen und akzeptiert dort jeden Dateityp – sie darf sich also nicht darauf verlassen, dass
     * das Archiv von ihr selbst stammt.
     */
    @Test
    public void eintragMitPfadangabeBrichtDasEinlesenAb() {
        String[][] boesartig = {
                {"db/../../shared_prefs/gekapert.db"},
                {"db/subdir/p1.db"},
                {"prefs/../../databases/ausgaben.json"},
                {"prefs/ausgaben_settings/../x.json"},
        };
        for (String[] eintrag : boesartig) {
            try {
                BackupArchive.read(archivMitEintrag(eintrag[0]));
                fail("hätte abgelehnt werden müssen: " + eintrag[0]);
            } catch (IOException expected) {
                assertTrue("Meldung nennt den Eintrag: " + expected.getMessage(),
                        expected.getMessage().contains(eintrag[0]));
            }
        }
    }

    /** Die Namen, die selbst geschriebene Sicherungen führen, müssen weiter durchgehen. */
    @Test
    public void gewoehnlicheNamenGehenWeiterhinDurch() throws Exception {
        BackupArchive.Content c = BackupArchive.read(
                archivMitEintrag("db/3f2a9c1e-4b7d-4a10-9e33-0c8e5b1d7a42.db"));
        assertEquals(1, c.dbs.size());

        assertEquals(1, BackupArchive.read(archivMitEintrag("db/legacy.db")).dbs.size());
        assertEquals(1, BackupArchive.read(
                archivMitEintrag("prefs/ausgaben_settings.json")).prefs.size());
    }

    /**
     * Ein ZIP sagt nicht vorab, wie groß sein Inhalt ist: ein paar Kilobyte können sich zu Gigabyte
     * entfalten. Die App nimmt die Datei über einen Dateiwähler entgegen, der bewusst jeden Dateityp
     * annimmt — bis 1.12 lief sie damit ohne jede Grenze in den Speicher.
     *
     * <p>Gebaut wird hier keine echte Zip-Bombe, sondern ein Eintrag mit sehr gut komprimierbarem
     * Inhalt: ein Gigabyte Nullbytes schrumpft auf rund ein Megabyte. Das genügt, um zu zeigen, dass
     * beim Entpacken abgebrochen wird und nicht erst der Speicher ausgeht.</p>
     */
    @Test
    public void einUnplausibelGrosserInhaltBrichtDasEinlesenAb() throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(BackupArchive.ENTRY_MANIFEST));
            zip.write("{\"format\":2,\"scope\":\"profile\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(BackupArchive.ENTRY_DB));
            byte[] block = new byte[1024 * 1024];          // lauter Nullbytes
            for (int i = 0; i < 1024; i++) {               // zusammen ein Gigabyte
                zip.write(block);
            }
            zip.closeEntry();
        }

        try {
            BackupArchive.read(buffer.toByteArray());
            fail("ein Gigabyte darf nicht eingelesen werden");
        } catch (IOException expected) {
            assertTrue("die Meldung sagt, woran es liegt: " + expected.getMessage(),
                    expected.getMessage().contains("groß"));
        }
    }

    /** Ein von Hand gebautes Archiv – {@code write} könnte solche Namen gar nicht erzeugen. */
    private static byte[] archivMitEintrag(String name) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer)) {
            zip.putNextEntry(new java.util.zip.ZipEntry(BackupArchive.ENTRY_MANIFEST));
            zip.write(("{\"format\":2,\"scope\":\"all\",\"hasData\":true,\"hasSettings\":true}")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry(name));
            zip.write(name.endsWith(".json") ? "{}".getBytes(StandardCharsets.UTF_8) : DB);
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }
}
