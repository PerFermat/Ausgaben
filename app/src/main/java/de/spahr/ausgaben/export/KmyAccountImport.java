package de.spahr.ausgaben.export;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Booking;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.SettingsStore;
import de.spahr.ausgaben.util.ProgressListener;

/**
 * Aktualisiert vorhandene Konten aus der .kmy: Datei laden, Buchungen der Zielkonten bauen und in der
 * Datenbank ersetzen. Neue Konten legt der Import bewusst nicht an – dafür gibt es „Konto hinzufügen".
 *
 * <p>Die Oberfläche liefert nur die Rückmeldungen ({@link Ui}); Banner, Abfragen und Meldungen bleiben
 * Sache der jeweiligen Ansicht. So können Konto- und Depotansicht denselben Import anstoßen.</p>
 */
public final class KmyAccountImport {

    /**
     * Rückmeldungen an die Oberfläche. {@link #phase} und {@link #noMatchingAccount}/{@link #failed}
     * kommen aus einem <b>Hintergrund-Thread</b> – die Umsetzung muss selbst auf den Main-Thread
     * wechseln. Nur {@link #finished()} läuft bereits im Main-Thread.
     */
    public interface Ui {
        /** Fortschritts-Empfänger für eine Phase (bildet auf {@code from..to} Prozent ab). */
        ProgressListener phase(String label, int from, int to);

        /** Keines der gewünschten Konten steht in der .kmy. */
        void noMatchingAccount();

        void failed(Exception e);

        void finished();
    }

    private KmyAccountImport() {
    }

    /**
     * Startet den Import im Hintergrund.
     *
     * @param knownAccounts in der App vorhandene Konten (Grundlage für „alle Konten")
     * @param account       einzelnes Konto oder {@code null} für alle vorhandenen Konten
     * @param depots        zusätzlich zu aktualisierende Depots (leer/{@code null} = keine)
     * @param schedules     zusätzlich die geplanten Buchungen neu einlesen
     */
    public static void start(Context context, SettingsStore settings, Repository repository,
                             List<String> knownAccounts, final String account,
                             final List<String> depots, final boolean schedules, final Ui ui) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                String path = settings.getKmyPath();
                byte[] raw = RemoteStorage.from(settings).downloadBytes(folderOf(path), fileOf(path),
                        ui.phase(app.getString(R.string.import_stage_download),
                                ImportPhase.DOWNLOAD_FROM, ImportPhase.DOWNLOAD_TO));
                KmyImporter importer = new KmyImporter(
                        new KmyDocument(raw, app,
                                ui.phase(app.getString(R.string.import_stage_reading),
                                        ImportPhase.READ_FILE_FROM, ImportPhase.READ_FILE_TO)),
                        app);
                List<String> available = importer.accountNames();
                List<String> targets = new ArrayList<>();
                if (account == null) {
                    // Nur bereits vorhandene App-Konten, die es auch in der .kmy gibt.
                    for (String acc : knownAccounts) {
                        if (containsIgnoreCase(available, acc)) {
                            targets.add(acc);
                        }
                    }
                } else if (containsIgnoreCase(available, account)) {
                    targets.add(account);
                }
                final List<String> depotTargets = new ArrayList<>();
                if (depots != null) {
                    for (String d : depots) {
                        if (containsIgnoreCase(importer.depotNames(), d)) {
                            depotTargets.add(d);
                        }
                    }
                }
                if (targets.isEmpty() && depotTargets.isEmpty() && !schedules) {
                    ui.noMatchingAccount();
                    return;
                }
                if (targets.isEmpty()) {
                    // Nur Depots und/oder Planungen – die Buchungsphase entfällt.
                    afterAccounts(app, repository, importer, depotTargets, schedules, ui);
                    return;
                }
                // Ein Lesedurchlauf für ALLE Konten (vorher: einer je Konto über die ganze Datei).
                LinkedHashMap<String, List<Booking>> map = importer.bookingsForAccounts(targets,
                        ui.phase(app.getString(R.string.import_stage_bookings),
                                ImportPhase.BOOKINGS_FROM, ImportPhase.BOOKINGS_TO));
                for (String acc : targets) {
                    // Währungskennzeichen aus der KMyMoney-Datei je Konto übernehmen.
                    repository.setAccountCurrency(acc, importer.currencyOf(acc));
                }
                // Anlage/Verbindlichkeit bzw. Einnahme/Ausgabe für ALLE Konten und Kategorien der .kmy
                // klassifizieren, nicht nur für die aktualisierten.
                repository.applyAccountTypes(importer.accountTypes());
                repository.applyCategoryTypes(importer.categoryTypes());
                // Kein separates „Buchungen werden gespeichert" beim Konto-Aktualisieren – nur die
                // laufende Phase weiterzählen.
                repository.replaceImportAccounts(map,
                        ui.phase(app.getString(R.string.import_running_banner),
                                ImportPhase.SAVE_FROM, ImportPhase.SAVE_TO),
                        res -> afterAccounts(app, repository, importer, depotTargets, schedules, ui));
            } catch (Exception e) {
                ui.failed(e);
            }
        }).start();
    }

    /**
     * Nach den Konten: Depots der Reihe nach, danach die geplanten Buchungen – alles aus derselben,
     * bereits geladenen Datei. Läuft rekursiv über die Rückrufe des Repositorys (Main-Thread), die
     * eigentliche Arbeit jeweils in einem Hintergrund-Thread.
     */
    private static void afterAccounts(Context app, Repository repository, KmyImporter importer,
                                      List<String> depots, boolean schedules, Ui ui) {
        if (!depots.isEmpty()) {
            final String depot = depots.get(0);
            final List<String> rest = new ArrayList<>(depots.subList(1, depots.size()));
            ui.phase(app.getString(R.string.import_stage_depot, depot),
                    ImportPhase.SAVE_TO, ImportPhase.SAVE_TO).onProgress(0, 1);
            new Thread(() -> {
                try {
                    KmyImporter.DepotData data = importer.importDepot(depot);
                    repository.replaceDepotImport(depot, data.securities, data.transactions, data.prices,
                            () -> afterAccounts(app, repository, importer, rest, schedules, ui));
                } catch (Exception e) {
                    ui.failed(e);
                }
            }).start();
            return;
        }
        if (schedules) {
            ui.phase(app.getString(R.string.import_stage_scheduled),
                    ImportPhase.SAVE_TO, ImportPhase.SAVE_TO).onProgress(0, 1);
            new Thread(() -> {
                try {
                    repository.applyScheduledTransactions(importer.scheduledTransactions(), ui::finished);
                } catch (Exception e) {
                    ui.failed(e);
                }
            }).start();
            return;
        }
        ui.finished();
    }

    private static boolean containsIgnoreCase(List<String> list, String name) {
        for (String s : list) {
            if (s.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    static String folderOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? "" : p.substring(0, slash);
    }

    static String fileOf(String path) {
        String p = path.trim();
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }
}
