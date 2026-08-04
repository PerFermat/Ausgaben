package de.spahr.ausgaben.backup;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Die Sicherungsdatei: ein ZIP mit {@code manifest.json}, der Datenbank {@code ausgaben.db} und je einer
 * Datei {@code prefs/&lt;Name&gt;.json} pro Einstellungs-Datei (siehe {@link PrefsCodec}).
 *
 * <p>Beides ist einzeln optional – beim Wiederherstellen kann man Daten und Einstellungen getrennt
 * einspielen. Ohne Manifest ist es keine Sicherung dieser App.</p>
 */
public final class BackupArchive {

    public static final String ENTRY_MANIFEST = "manifest.json";
    public static final String ENTRY_DB = "ausgaben.db";
    public static final String PREFS_DIR = "prefs/";
    /** Name der Prefs-Datei mit dem Server-Passwort (nur auf Wunsch enthalten). */
    public static final String PREFS_SECRET = "secret";
    /** Schlüssel des Server-Passworts in {@link #PREFS_SECRET}. */
    public static final String KEY_SERVER_PASSWORD = "server_password";

    private static final int FORMAT = 1;

    private BackupArchive() {
    }

    /** Gelesener Inhalt einer Sicherung. */
    public static final class Content {
        public final int format;
        public final long created;
        public final byte[] db;
        /** Prefs-Dateiname (ohne Endung) → JSON-Inhalt. */
        public final LinkedHashMap<String, String> prefs;

        Content(int format, long created, byte[] db, LinkedHashMap<String, String> prefs) {
            this.format = format;
            this.created = created;
            this.db = db;
            this.prefs = prefs;
        }

        public boolean hasData() {
            return db != null && db.length > 0;
        }

        public boolean hasSettings() {
            return !prefs.isEmpty();
        }

        /** JSON der Prefs-Datei oder {@code null}. */
        public String prefs(String name) {
            return prefs.get(name);
        }
    }

    /**
     * Baut die Sicherung.
     *
     * @param db         Inhalt von {@code ausgaben.db}
     * @param prefs      Prefs-Dateiname (ohne Endung) → JSON aus {@link PrefsCodec#toJson(Map)}
     * @param versionCode versionCode der schreibenden App (nur zur Information im Manifest)
     */
    public static byte[] write(byte[] db, Map<String, String> prefs, int versionCode)
            throws IOException, JSONException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            JSONObject manifest = new JSONObject()
                    .put("format", FORMAT)
                    .put("versionCode", versionCode)
                    .put("created", System.currentTimeMillis())
                    .put("hasData", db != null && db.length > 0)
                    .put("hasSettings", prefs != null && !prefs.isEmpty());
            put(zip, ENTRY_MANIFEST, manifest.toString().getBytes(StandardCharsets.UTF_8));
            if (db != null && db.length > 0) {
                put(zip, ENTRY_DB, db);
            }
            if (prefs != null) {
                for (Map.Entry<String, String> e : prefs.entrySet()) {
                    put(zip, PREFS_DIR + e.getKey() + ".json",
                            e.getValue().getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return buffer.toByteArray();
    }

    /**
     * Liest eine Sicherung.
     *
     * @throws IOException wenn es kein ZIP ist oder das Manifest fehlt (also keine Sicherung dieser App)
     */
    public static Content read(byte[] data) throws IOException {
        byte[] manifest = null;
        byte[] db = null;
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (ENTRY_MANIFEST.equals(name)) {
                    manifest = readAll(zip);
                } else if (ENTRY_DB.equals(name)) {
                    db = readAll(zip);
                } else if (name.startsWith(PREFS_DIR) && name.endsWith(".json")) {
                    prefs.put(name.substring(PREFS_DIR.length(), name.length() - ".json".length()),
                            new String(readAll(zip), StandardCharsets.UTF_8));
                }
            }
        }
        if (manifest == null) {
            throw new IOException("Kein Manifest – keine Sicherung von Ausgaben");
        }
        try {
            JSONObject m = new JSONObject(new String(manifest, StandardCharsets.UTF_8));
            return new Content(m.optInt("format", 0), m.optLong("created", 0L), db, prefs);
        } catch (JSONException e) {
            throw new IOException("Manifest beschädigt", e);
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
