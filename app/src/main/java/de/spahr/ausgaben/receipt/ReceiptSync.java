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

import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Hintergrund-Synchronisierung der Belegfotos ins konfigurierte Netzwerkverzeichnis unter
 * {@code <Sync-Ordner>/Belege/<Jahr>/}. Kein WorkManager: ein einzelner I/O-Thread lädt die offenen Dateien
 * hoch, ausgelöst beim App-Öffnen und direkt nach der Aufnahme. Der Jahresordner steckt im Dateinamen-Präfix.
 */
public final class ReceiptSync {

    private static final String REMOTE_SUBDIR = "Belege";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ReceiptSync() {
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
            final String belege = join(settings.getFolder(), REMOTE_SUBDIR);
            for (String file : pending) {
                int year = NoteReceipt.yearOf(file);
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
     */
    public static File ensureLocal(Context context, String file) {
        final Context ctx = context.getApplicationContext();
        File local = Receipts.localFile(ctx, file);
        if (local.exists()) {
            return local;
        }
        SettingsStore settings = new SettingsStore(ctx);
        int year = NoteReceipt.yearOf(file);
        if (!settings.hasRemoteConfig() || year < 0) {
            return null;
        }
        try {
            RemoteStorage storage = RemoteStorage.from(settings);
            String yearFolder = join(settings.getFolder(), REMOTE_SUBDIR) + "/" + year;
            byte[] bytes = storage.downloadBytes(yearFolder, file);
            try (FileOutputStream fos = new FileOutputStream(local)) {
                fos.write(bytes);
            }
            return local;
        } catch (Exception e) {
            return null;
        }
    }

    private static String join(String base, String sub) {
        base = base == null ? "" : base.trim();
        if (base.isEmpty()) {
            return sub;
        }
        return base.endsWith("/") ? base + sub : base + "/" + sub;
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
