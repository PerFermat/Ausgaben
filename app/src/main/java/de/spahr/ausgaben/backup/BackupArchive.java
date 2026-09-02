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
 * Die Sicherungsdatei: ein ZIP mit {@code manifest.json}, ein oder mehreren Datenbanken und je einer
 * Datei {@code prefs/&lt;Name&gt;.json} pro Einstellungs-Datei (siehe {@link PrefsCodec}).
 *
 * <p>Zwei Umfänge (siehe {@link #SCOPE_PROFILE}/{@link #SCOPE_ALL}): eine <b>Profil</b>-Sicherung
 * enthält genau eine Datenbank unter {@link #ENTRY_DB} und nur die Einstellungen des Profils, das sie
 * erstellt hat (ohne Profil-Präfix – portabel auf ein beliebiges Zielprofil). Eine <b>Alle-Profile</b>-
 * Sicherung enthält je Profil eine Datenbank unter {@code db/&lt;profileId&gt;.db} sowie die
 * Einstellungsdateien unverändert (mit allen Profil-Präfixen) plus die Profilliste selbst.</p>
 *
 * <p>Beides ist einzeln optional – beim Wiederherstellen kann man Daten und Einstellungen getrennt
 * einspielen. Ohne Manifest ist es keine Sicherung dieser App. Ältere Sicherungen ohne {@code scope} im
 * Manifest gelten als {@link #SCOPE_PROFILE} (das einzige Format, das es vor der Mehrprofil-Sicherung
 * gab).</p>
 */
public final class BackupArchive {

    public static final String ENTRY_MANIFEST = "manifest.json";
    public static final String ENTRY_DB = "ausgaben.db";
    public static final String DB_DIR = "db/";
    public static final String PREFS_DIR = "prefs/";
    /** Name der Prefs-Datei mit dem Server-Passwort (nur auf Wunsch enthalten). */
    public static final String PREFS_SECRET = "secret";
    /** Schlüssel des Server-Passworts in {@link #PREFS_SECRET} (Profil-Sicherung, ein Passwort). */
    public static final String KEY_SERVER_PASSWORD = "server_password";
    /** Name der Prefs-Datei mit der Profilliste (nur in einer Alle-Profile-Sicherung enthalten). */
    public static final String PREFS_PROFILES = "profiles";

    /** Nur die Datenbank und Einstellungen <b>eines</b> Profils, ohne Profil-Präfix – portabel. */
    public static final String SCOPE_PROFILE = "profile";
    /** Alle Profile samt Profilliste, Datenbanken und Einstellungsdateien 1:1. */
    public static final String SCOPE_ALL = "all";

    /**
     * Ab dieser Fassung stehen die Einstellungen profilweise im Archiv (Mehrprofil-Umbau, 1.12). Ältere
     * Sicherungen führen alle Schlüssel unpräfixiert nebeneinander und müssen beim Einspielen erst
     * sortiert werden – siehe {@code BackupStore.restoreProfileSettings}.
     */
    public static final int FORMAT_PROFILES = 2;

    private static final int FORMAT = FORMAT_PROFILES;

    /** Was als Profil-ID oder Name einer Einstellungsdatei durchgeht – siehe {@link #geprueft}. */
    private static final java.util.regex.Pattern NAME =
            java.util.regex.Pattern.compile("[A-Za-z0-9_-]+");

    private BackupArchive() {
    }

    /** Gelesener Inhalt einer Sicherung. */
    public static final class Content {
        public final int format;
        public final long created;
        public final String scope;
        /** Bei {@link #SCOPE_PROFILE}: die eine Datenbank; sonst {@code null}. */
        public final byte[] db;
        /** Bei {@link #SCOPE_ALL}: Profil-ID → Datenbank; bei {@link #SCOPE_PROFILE} leer. */
        public final LinkedHashMap<String, byte[]> dbs;
        /** Prefs-Dateiname (ohne Endung) → JSON-Inhalt. */
        public final LinkedHashMap<String, String> prefs;

        Content(int format, long created, String scope, byte[] db, LinkedHashMap<String, byte[]> dbs,
                LinkedHashMap<String, String> prefs) {
            this.format = format;
            this.created = created;
            this.scope = scope;
            this.db = db;
            this.dbs = dbs;
            this.prefs = prefs;
        }

        public boolean hasData() {
            return (db != null && db.length > 0) || !dbs.isEmpty();
        }

        public boolean hasSettings() {
            return !prefs.isEmpty();
        }

        public boolean isAllProfiles() {
            return SCOPE_ALL.equals(scope);
        }

        /** JSON der Prefs-Datei oder {@code null}. */
        public String prefs(String name) {
            return prefs.get(name);
        }
    }

    /**
     * Baut die Sicherung.
     *
     * @param scope       {@link #SCOPE_PROFILE} oder {@link #SCOPE_ALL}
     * @param dbs         bei {@code SCOPE_PROFILE} höchstens ein Eintrag (Schlüssel ohne Bedeutung), bei
     *                    {@code SCOPE_ALL} Profil-ID → Datenbankinhalt
     * @param prefs       Prefs-Dateiname (ohne Endung) → JSON aus {@link PrefsCodec#toJson(Map)}
     * @param versionCode versionCode der schreibenden App (nur zur Information im Manifest)
     */
    public static byte[] write(String scope, Map<String, byte[]> dbs, Map<String, String> prefs,
                                int versionCode) throws IOException, JSONException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        boolean hasData = dbs != null && !dbs.isEmpty();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            JSONObject manifest = new JSONObject()
                    .put("format", FORMAT)
                    .put("scope", scope)
                    .put("versionCode", versionCode)
                    .put("created", System.currentTimeMillis())
                    .put("hasData", hasData)
                    .put("hasSettings", prefs != null && !prefs.isEmpty());
            put(zip, ENTRY_MANIFEST, manifest.toString().getBytes(StandardCharsets.UTF_8));
            if (hasData) {
                if (SCOPE_ALL.equals(scope)) {
                    for (Map.Entry<String, byte[]> e : dbs.entrySet()) {
                        put(zip, DB_DIR + e.getKey() + ".db", e.getValue());
                    }
                } else {
                    put(zip, ENTRY_DB, dbs.values().iterator().next());
                }
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
        byte[] singleDb = null;
        LinkedHashMap<String, byte[]> dbs = new LinkedHashMap<>();
        LinkedHashMap<String, String> prefs = new LinkedHashMap<>();
        long[] entpackt = new long[1];
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (ENTRY_MANIFEST.equals(name)) {
                    manifest = readAll(zip, entpackt);
                } else if (ENTRY_DB.equals(name)) {
                    singleDb = readAll(zip, entpackt);
                } else if (name.startsWith(DB_DIR) && name.endsWith(".db")) {
                    dbs.put(geprueft(name, DB_DIR, ".db"), readAll(zip, entpackt));
                } else if (name.startsWith(PREFS_DIR) && name.endsWith(".json")) {
                    prefs.put(geprueft(name, PREFS_DIR, ".json"),
                            new String(readAll(zip, entpackt), StandardCharsets.UTF_8));
                }
            }
        }
        if (manifest == null) {
            throw new IOException("Kein Manifest – keine Sicherung von Ausgaben");
        }
        try {
            JSONObject m = new JSONObject(new String(manifest, StandardCharsets.UTF_8));
            String scope = m.optString("scope", SCOPE_PROFILE);
            return new Content(m.optInt("format", 0), m.optLong("created", 0L), scope, singleDb, dbs, prefs);
        } catch (JSONException e) {
            throw new IOException("Manifest beschädigt", e);
        }
    }

    /**
     * Der Name zwischen Ordner und Endung – geprüft, bevor er weiterverwendet wird.
     *
     * <p>Aus diesem Stück wird später ein <b>Dateiname</b>: die Profil-ID geht über
     * {@code ProfileManager.dbFileNameFor} in {@code getDatabasePath}, der Prefs-Name direkt in
     * {@code getSharedPreferences}. Ein Eintrag {@code db/../../shared_prefs/x.db} in einem
     * untergeschobenen Archiv würde damit außerhalb des vorgesehenen Ordners schreiben. Die App nimmt
     * die Datei beim Einspielen über einen Dateiwähler entgegen und akzeptiert dort bewusst jeden
     * Dateityp – sie kann sich also nicht darauf verlassen, dass das Archiv von ihr selbst stammt.</p>
     *
     * <p>Selbst geschriebene Sicherungen führen hier nur Profil-IDs (UUID oder {@code legacy}) und die
     * festen Namen der Einstellungsdateien; alles andere ist entweder beschädigt oder gebastelt und
     * bricht das Einspielen ab, statt einen Teil davon auszuführen.</p>
     */
    private static String geprueft(String entryName, String dir, String suffix) throws IOException {
        String key = entryName.substring(dir.length(), entryName.length() - suffix.length());
        if (key.isEmpty() || !NAME.matcher(key).matches()) {
            throw new IOException("Unerlaubter Eintrag in der Sicherung: " + entryName);
        }
        return key;
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    /**
     * Obergrenze für alles, was aus einem Archiv entpackt wird.
     *
     * <p>Ein ZIP sagt nicht vorab, wie groß sein Inhalt ist; ein paar Kilobyte können sich zu
     * Gigabyte entfalten. Das Archiv kommt über einen Dateiwähler herein, der bewusst jeden Dateityp
     * annimmt — die App bestimmt also nicht, was sie da bekommt, und lief bis 1.12 ohne jede Grenze in
     * den Speicher.</p>
     *
     * <p>256 MB sind großzügig: gesichert werden SQLite-Dateien und ein paar Einstellungen als JSON.
     * Wer diese Größe echt erreicht, hat ein anderes Gespräch zu führen als über diese Grenze.</p>
     */
    private static final long MAX_ENTPACKT = 256L * 1024 * 1024;

    /**
     * Liest einen Eintrag und zählt dabei mit, wie viel das Archiv insgesamt schon hergegeben hat.
     *
     * @param bisher Zähler über alle Einträge (ein Element, damit ihn die Schleife weiterträgt)
     */
    private static byte[] readAll(InputStream in, long[] bisher) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bisher[0] += n;
            if (bisher[0] > MAX_ENTPACKT) {
                throw new IOException("Die Sicherung ist unplausibel groß – Einspielen abgebrochen");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
