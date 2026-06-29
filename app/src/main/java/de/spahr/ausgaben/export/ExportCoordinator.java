package de.spahr.ausgaben.export;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.net.NextcloudUploader;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Bündelt den CSV-Export: gruppiert Buchungen nach Konto, erzeugt pro Konto eine Datei
 * (<Konto>-<Zeitstempel>.csv), speichert sie lokal und lädt sie auf Nextcloud hoch.
 */
public class ExportCoordinator {

    public interface ResultListener {
        /** Wird auf dem Main-Thread aufgerufen. {@code refreshNeeded} = Liste sollte neu geladen werden. */
        void onComplete(String message, boolean refreshNeeded);
    }

    private final Context appContext;
    private final Repository repository;
    private final SettingsStore settings;

    public ExportCoordinator(Context context, Repository repository, SettingsStore settings) {
        this.appContext = context.getApplicationContext();
        this.repository = repository;
        this.settings = settings;
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
            if (!settings.hasNextcloudConfig()) {
                post(listener, "Bitte zuerst die Nextcloud-Zugangsdaten in den Einstellungen hinterlegen", false);
                return;
            }

            List<Booking> bookings = onlyUnexported
                    ? repository.bookingDao().getUnexported()
                    : repository.bookingDao().getAllBookings();

            if (bookings.isEmpty()) {
                post(listener, onlyUnexported
                        ? "Keine neuen Buchungen zum Exportieren"
                        : "Keine Buchungen vorhanden", false);
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

            CsvExporter exporter = new CsvExporter();
            NextcloudUploader uploader = new NextcloudUploader();
            int okAccounts = 0;
            int okBookings = 0;
            boolean marked = false;
            List<String> errors = new ArrayList<>();

            for (Map.Entry<String, List<Booking>> entry : byAccount.entrySet()) {
                List<Booking> accountBookings = entry.getValue();
                String content = exporter.build(accountBookings);
                String fileName = exporter.buildFileName(entry.getKey());
                try {
                    saveLocalCopy(fileName, content);
                    uploader.upload(settings.getUrl(), settings.getUser(), settings.getPassword(),
                            settings.getFolder(), fileName, content);

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
                sb.append(okBookings).append(" Buchung(en) in ").append(okAccounts)
                        .append(" Datei(en) hochgeladen");
            }
            if (!errors.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("Fehlgeschlagen – ").append(android.text.TextUtils.join("; ", errors));
            }
            post(listener, sb.toString(), marked);
        });
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
