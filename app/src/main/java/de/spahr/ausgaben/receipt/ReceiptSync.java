package de.spahr.ausgaben.receipt;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.spahr.ausgaben.net.RemotePath;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Hintergrund-Synchronisierung der Belegfotos ins konfigurierte Netzwerkverzeichnis unter
 * {@code <Basis>/Belege/<Jahr>/}. Die Basis ist im kmy-Modus der <b>Ordner der KMyMoney-Datei</b> (dort
 * liegt auch der {@code Backup}-Ordner), im CSV-Modus der eingestellte Sync-Ordner. Kein WorkManager: ein
 * einzelner I/O-Thread lädt die offenen Dateien hoch, ausgelöst beim App-Öffnen und nach der Aufnahme.
 */
public final class ReceiptSync {

    private static final String REMOTE_SUBDIR = "Belege";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ReceiptSync() {
    }

    /**
     * Der Beleg-Ordner auf dem Server, relativ zur konfigurierten Wurzel: neben der KMyMoney-Datei bzw. im
     * Sync-Ordner.
     */
    public static String remoteBase(SettingsStore settings) {
        String base = settings.isKmyMode()
                ? RemotePath.folderOf(settings.getKmyPath())
                : settings.getFolder();
        return RemotePath.join(base, REMOTE_SUBDIR);
    }

    /**
     * Der frühere Ablageort (immer der Sync-Ordner). Wird nur noch <b>gelesen</b>, damit Belege, die vor
     * der Umstellung im kmy-Modus hochgeladen wurden, weiter gefunden werden.
     */
    private static String legacyBase(SettingsStore settings) {
        return RemotePath.join(settings.getFolder(), REMOTE_SUBDIR);
    }

    /** Lädt alle offenen Belege hoch (No-op ohne Remote-Konfiguration bzw. ohne offene Dateien). */
    public static void syncPending(Context context) {
        final Context ctx = context.getApplicationContext();
        final SettingsStore settings = new SettingsStore(ctx);
        if (!settings.hasRemoteConfig()) {
            return;
        }
        final Set<String> pending = Receipts.pending(ctx);
        if (pending.isEmpty()) {
            return;
        }
        IO.execute(() -> {
            RemoteStorage storage;
            try {
                storage = RemoteStorage.from(settings);
            } catch (Exception e) {
                return;
            }
            final String belege = remoteBase(settings);
            for (String entry : pending) {
                String file = Receipts.entryFile(entry);
                int year = Receipts.entryYear(entry);
                File local = Receipts.localFile(ctx, file);
                if (year < 0 || !local.exists()) {
                    Receipts.removePending(ctx, file); // ungültig/verschwunden → nicht endlos erneut versuchen
                    continue;
                }
                try {
                    String yearFolder = belege + "/" + year;
                    storage.ensureFolder(belege);
                    storage.ensureFolder(yearFolder);
                    storage.uploadBytes(yearFolder, file, readAll(local));
                    Receipts.removePending(ctx, file);
                } catch (Exception e) {
                    // offline / Fehler → bleibt offen, nächster Versuch beim nächsten Aufruf
                }
            }
        });
    }

    /**
     * Stellt sicher, dass der Beleg lokal vorliegt; lädt ihn sonst vom Netzlaufwerk nach. Blockierend –
     * vom Aufrufer auf einem Hintergrund-Thread nutzen. Liefert die lokale Datei oder {@code null}.
     *
     * <p>{@code year} ist der Jahresordner; {@code -1} lässt ihn aus dem Dateinamen ableiten (Altbelege
     * mit Jahres-Präfix).</p>
     */
    public static File ensureLocal(Context context, String file, int year) {
        final Context ctx = context.getApplicationContext();
        File local = Receipts.localFile(ctx, file);
        if (local.exists()) {
            return local;
        }
        SettingsStore settings = new SettingsStore(ctx);
        int y = year >= 0 ? year : NoteReceipt.yearOf(file);
        if (!settings.hasRemoteConfig() || y < 0) {
            return null;
        }
        RemoteStorage storage;
        try {
            storage = RemoteStorage.from(settings);
        } catch (Exception e) {
            return null;
        }
        // Neuer Ort zuerst, danach der frühere – so bleiben vorhandene Uploads erreichbar.
        for (String base : new String[]{remoteBase(settings), legacyBase(settings)}) {
            try {
                byte[] bytes = storage.downloadBytes(base + "/" + y, file);
                try (FileOutputStream fos = new FileOutputStream(local)) {
                    fos.write(bytes);
                }
                return local;
            } catch (Exception e) {
                local.delete(); // halb geschriebene Datei nicht stehen lassen
            }
        }
        return null;
    }

    /** Wie {@link #ensureLocal(Context, String, int)} mit dem Jahr aus dem Dateinamen (Altbelege). */
    public static File ensureLocal(Context context, String file) {
        return ensureLocal(context, file, -1);
    }

    /** Vom Aufrufer stellbarer Abbruch (z. B. wenn die Belegseite inzwischen recycelt wurde). */
    public interface Cancelled {
        boolean get();
    }

    /** Ergebnis von {@link #ensureLocalWaiting}. */
    public static final class Loaded {
        /** Die lokale Datei, sobald vorhanden; sonst {@code null}. */
        public final File file;
        /** {@code true} = keine Verbindung → der Aufrufer zeigt eine Fehlermeldung. */
        public final boolean offline;

        Loaded(File file, boolean offline) {
            this.file = file;
            this.offline = offline;
        }
    }

    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_DELAY_MS = 3000L;

    /**
     * Wie {@link #ensureLocal}, aber wartend: bei bestehender Verbindung, aber (noch) nicht vorhandener
     * Datei wird mehrfach erneut versucht (der Aufrufer lässt derweil den Hinweis „Wird geladen …" stehen).
     * Ergebnis: Datei gefunden ({@code file != null}), keine Verbindung ({@code offline}) oder online, aber
     * (noch) nicht da ({@code file == null && !offline}) – dann bleibt es beim Hinweis, keine Fehlermeldung.
     * <b>Blockierend</b> – vom Aufrufer auf einem Hintergrund-Thread nutzen.
     */
    public static Loaded ensureLocalWaiting(Context context, String file, int year, Cancelled cancelled) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (cancelled != null && cancelled.get()) {
                return new Loaded(null, false);
            }
            File local = ensureLocal(context, file, year);
            if (local != null && local.exists()) {
                return new Loaded(local, false);
            }
            if (!de.spahr.ausgaben.net.Net.isOnline(context)) {
                return new Loaded(null, true);
            }
            if (attempt + 1 < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new Loaded(null, false);
                }
            }
        }
        return new Loaded(null, false); // online, aber nicht auffindbar → Hinweis bleibt stehen
    }

    private static byte[] readAll(File f) throws java.io.IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
