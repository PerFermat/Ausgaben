package de.spahr.ausgaben.export;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Bündelt den CSV-Export: gruppiert Buchungen nach Konto, erzeugt pro Konto eine Datei
 * (<Konto>-<Zeitstempel>.csv). Mit Nextcloud-Konfiguration wird hochgeladen, sonst lokal in einen
 * per SAF gewählten Ordner geschrieben.
 */
public class ExportCoordinator {

    public interface ResultListener {
        /** Wird auf dem Main-Thread aufgerufen. {@code refreshNeeded} = Liste sollte neu geladen werden. */
        void onComplete(String message, boolean refreshNeeded);
    }

    private final Context appContext;
    private final Repository repository;
    private final SettingsStore settings;
    private final String localTreeUri;

    public ExportCoordinator(Context context, Repository repository, SettingsStore settings) {
        this(context, repository, settings, null);
    }

    /** {@code localTreeUri} = Ziel-Ordner (SAF-Tree) für den lokalen Export ohne Nextcloud. */
    public ExportCoordinator(Context context, Repository repository, SettingsStore settings,
                             String localTreeUri) {
        this.appContext = context.getApplicationContext();
        this.repository = repository;
        this.settings = settings;
        this.localTreeUri = localTreeUri;
    }

    /** Normaler Export: nur unexportierte Buchungen; markiert sie nach Erfolg als exportiert. */
    public void exportUnexported(ResultListener listener) {
        run(true, listener);
    }

    /** Kompletter Export: alle Buchungen; ändert keine exported-Flags. */
    public void exportAll(ResultListener listener) {
        run(false, listener);
    }

    private void run(boolean onlyUnexported, ResultListener listener) {
        repository.executor().execute(() -> {
            // Context in der aktuellen Sprache (der App-Context übernimmt sie erst nach Neustart).
            Context res = de.spahr.ausgaben.i18n.LocaleManager.localizedContext(appContext);
            boolean upload = settings.hasRemoteConfig();
            if (!upload && (localTreeUri == null || localTreeUri.isEmpty())) {
                post(listener, res.getString(de.spahr.ausgaben.R.string.export_target_missing), false);
                return;
            }

            List<Booking> bookings = onlyUnexported
                    ? repository.bookingDao().getUnexported()
                    : repository.bookingDao().getAllBookings();

            if (bookings.isEmpty()) {
                post(listener, res.getString(onlyUnexported
                        ? de.spahr.ausgaben.R.string.export_none
                        : de.spahr.ausgaben.R.string.export_no_bookings), false);
                return;
            }

            // Nach Konto gruppieren (Reihenfolge stabil halten)
            Map<String, List<Booking>> byAccount = new LinkedHashMap<>();
            for (Booking b : bookings) {
                List<Booking> list = byAccount.get(b.account);
                if (list == null) {
                    list = new ArrayList<>();
                    byAccount.put(b.account, list);
                }
                list.add(b);
            }

            // Kategorie-Teile (Splitbuchungen) einmalig nach Buchungs-ID gruppieren.
            Map<Long, List<de.spahr.ausgaben.db.BookingSplit>> splitsMap = new java.util.HashMap<>();
            for (de.spahr.ausgaben.db.BookingSplit s : repository.bookingDao().getAllSplits()) {
                List<de.spahr.ausgaben.db.BookingSplit> l = splitsMap.get(s.bookingId);
                if (l == null) {
                    l = new ArrayList<>();
                    splitsMap.put(s.bookingId, l);
                }
                l.add(s);
            }

            CsvExporter exporter = new CsvExporter();
            RemoteStorage storage = upload ? RemoteStorage.from(settings) : null;
            DocumentFile targetDir = upload ? null
                    : DocumentFile.fromTreeUri(appContext, Uri.parse(localTreeUri));
            int okAccounts = 0;
            int okBookings = 0;
            boolean marked = false;
            List<String> errors = new ArrayList<>();

            for (Map.Entry<String, List<Booking>> entry : byAccount.entrySet()) {
                List<Booking> accountBookings = entry.getValue();
                String content = exporter.build(entry.getKey(), accountBookings, splitsMap, res);
                String fileName = exporter.buildFileName(entry.getKey());
                try {
                    if (upload) {
                        saveLocalCopy(fileName, content);
                        storage.uploadText(settings.getFolder(), fileName, content);
                    } else {
                        writeToTree(res, targetDir, fileName, content);
                    }

                    if (onlyUnexported) {
                        List<Long> ids = new ArrayList<>();
                        for (Booking b : accountBookings) {
                            ids.add(b.id);
                        }
                        repository.bookingDao().markExported(ids);
                        marked = true;
                    }
                    okAccounts++;
                    okBookings += accountBookings.size();
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                    errors.add(entry.getKey() + ": " + msg);
                }
            }

            StringBuilder sb = new StringBuilder();
            if (okAccounts > 0) {
                sb.append(res.getString(upload
                        ? de.spahr.ausgaben.R.string.export_result_uploaded
                        : de.spahr.ausgaben.R.string.export_result_saved, okBookings, okAccounts));
            }
            if (!errors.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(res.getString(de.spahr.ausgaben.R.string.export_result_failed,
                        android.text.TextUtils.join("; ", errors)));
            }
            post(listener, sb.toString(), marked);
        });
    }

    private void writeToTree(Context res, DocumentFile dir, String fileName, String content)
            throws java.io.IOException {
        if (dir == null || !dir.canWrite()) {
            throw new java.io.IOException(res.getString(de.spahr.ausgaben.R.string.err_target_not_writable));
        }
        DocumentFile file = dir.createFile("text/csv", fileName);
        if (file == null) {
            throw new java.io.IOException(res.getString(de.spahr.ausgaben.R.string.err_file_create));
        }
        try (OutputStream os = appContext.getContentResolver().openOutputStream(file.getUri())) {
            if (os == null) {
                throw new java.io.IOException(res.getString(de.spahr.ausgaben.R.string.err_no_write_access));
            }
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void saveLocalCopy(String fileName, String content) throws java.io.IOException {
        File dir = appContext.getExternalFilesDir(null);
        if (dir == null) {
            dir = appContext.getFilesDir();
        }
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void post(ResultListener listener, String message, boolean refreshNeeded) {
        repository.mainHandler().post(() -> listener.onComplete(message, refreshNeeded));
    }
}
