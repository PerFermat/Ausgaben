package de.spahr.ausgaben.export;

import android.content.Context;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.net.NextcloudUploader;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Schreibt die noch nicht exportierten Buchungen direkt in eine KMyMoney-Datei auf Nextcloud:
 * herunterladen → entpacken → Transaktionen einfügen → Original sichern → gepackt zurückschreiben →
 * geschriebene Buchungen als exportiert markieren. Meldet den Fortschritt über {@link Listener}.
 */
public class KmyExportCoordinator {

    public interface Listener {
        /** Auf dem Main-Thread: Zwischenschritt (z. B. „Lade KMyMoney-Datei…"). */
        void onProgress(String stage);

        /** Auf dem Main-Thread: Endergebnis. */
        void onComplete(String message, boolean refreshNeeded);
    }

    private final Repository repository;
    private final SettingsStore settings;
    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.GERMANY);

    public KmyExportCoordinator(Context context, Repository repository, SettingsStore settings) {
        this.repository = repository;
        this.settings = settings;
    }

    public void exportUnexported(Listener listener) {
        repository.executor().execute(() -> {
            if (!settings.hasNextcloudConfig()) {
                complete(listener, "Bitte zuerst die Nextcloud-Zugangsdaten in den Einstellungen hinterlegen",
                        false);
                return;
            }
            String path = settings.getKmyPath();
            if (path.isEmpty()) {
                complete(listener, "Bitte die KMyMoney-Datei in den Einstellungen angeben", false);
                return;
            }
            String folder = folderOf(path);
            String file = fileOf(path);

            List<Booking> bookings = repository.bookingDao().getUnexported();
            if (bookings.isEmpty()) {
                complete(listener, "Keine neuen Buchungen zum Exportieren", false);
                return;
            }

            NextcloudUploader uploader = new NextcloudUploader();
            try {
                progress(listener, "Lade KMyMoney-Datei…");
                byte[] raw = uploader.downloadBytes(settings.getUrl(), settings.getUser(),
                        settings.getPassword(), folder, file);

                progress(listener, "Verarbeite Buchungen…");
                KmyDocument doc = new KmyDocument(raw);
                KmyExporter.Result res = new KmyExporter(doc).build(bookings, loadSplits());

                if (res.writtenIds.isEmpty()) {
                    complete(listener, "Keine Buchung konnte zugeordnet werden.\n" + skippedText(res),
                            false);
                    return;
                }

                progress(listener, "Sichere Original…");
                String backup = file + ".bak-" + tsFormat.format(new Date());
                uploader.uploadBytes(settings.getUrl(), settings.getUser(), settings.getPassword(),
                        folder, backup, raw);

                progress(listener, "Schreibe KMyMoney-Datei…");
                byte[] packed = KmyDocument.gzip(res.xml);
                uploader.uploadBytes(settings.getUrl(), settings.getUser(), settings.getPassword(),
                        folder, file, packed);

                repository.bookingDao().markExported(res.writtenIds);
                complete(listener, buildMessage(res, file, backup), true);
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                complete(listener, "Export fehlgeschlagen: " + msg, false);
            }
        });
    }

    /** Alle Kategorie-Teile (Splitbuchungen) nach Buchungs-ID gruppiert laden. */
    private java.util.Map<Long, List<de.spahr.ausgaben.db.BookingSplit>> loadSplits() {
        java.util.Map<Long, List<de.spahr.ausgaben.db.BookingSplit>> map = new java.util.HashMap<>();
        for (de.spahr.ausgaben.db.BookingSplit s : repository.bookingDao().getAllSplits()) {
            List<de.spahr.ausgaben.db.BookingSplit> list = map.get(s.bookingId);
            if (list == null) {
                list = new ArrayList<>();
                map.put(s.bookingId, list);
            }
            list.add(s);
        }
        return map;
    }

    private String buildMessage(KmyExporter.Result res, String file, String backup) {
        StringBuilder sb = new StringBuilder();
        sb.append(res.writtenIds.size()).append(" Buchung(en) in ").append(file).append(" geschrieben");
        if (res.newPayees > 0) {
            sb.append(", ").append(res.newPayees).append(" neue(r) Empfänger");
        }
        sb.append(".\nBackup: ").append(backup);
        if (!res.skipped.isEmpty()) {
            sb.append("\n").append(skippedText(res));
        }
        return sb.toString();
    }

    private String skippedText(KmyExporter.Result res) {
        if (res.skipped.isEmpty()) {
            return "";
        }
        List<String> show = res.skipped;
        String more = "";
        if (show.size() > 5) {
            show = new ArrayList<>(res.skipped.subList(0, 5));
            more = " … (+" + (res.skipped.size() - 5) + ")";
        }
        return res.skipped.size() + " übersprungen: " + TextUtils.join("; ", show) + more;
    }

    private static String folderOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    private static String fileOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }

    private void progress(Listener l, String stage) {
        repository.mainHandler().post(() -> l.onProgress(stage));
    }

    private void complete(Listener l, String message, boolean refresh) {
        repository.mainHandler().post(() -> l.onComplete(message, refresh));
    }
}
