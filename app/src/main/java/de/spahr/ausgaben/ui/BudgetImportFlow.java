package de.spahr.ausgaben.ui;

import android.app.Activity;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Budget;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.export.KmyDocument;
import de.spahr.ausgaben.export.KmyImporter;
import de.spahr.ausgaben.net.RemoteStorage;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Lädt die konfigurierte KMyMoney-Datei und speichert deren Budgetplanung als Soll des Zieljahres
 * (Herkunft {@code "kmy"}, nicht editierbar). Bei mehreren Budgetjahren fragt ein Dialog nach.
 * Wird von {@link BudgetActivity} (Leerzustand) und {@link SettingsActivity} genutzt.
 */
final class BudgetImportFlow {

    private BudgetImportFlow() {
    }

    /** Führt den Import aus; {@code onDone} läuft auf dem UI-Thread bei Erfolg. */
    static void run(Activity activity, SettingsStore settings, Repository repository,
                    int targetYear, Runnable onDone) {
        if (!settings.isKmyMode() || !settings.hasRemoteConfig()) {
            Toast.makeText(activity, R.string.export_no_config, Toast.LENGTH_LONG).show();
            return;
        }
        final String path = settings.getKmyPath();
        if (path.trim().isEmpty()) {
            Toast.makeText(activity, R.string.kmy_path_missing, Toast.LENGTH_LONG).show();
            return;
        }

        ProgressBar bar = new ProgressBar(activity);
        AlertDialog progress = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.progress_download)
                .setView(bar)
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            try {
                byte[] raw = RemoteStorage.from(settings).downloadBytes(folderOf(path), fileOf(path));
                KmyImporter importer = new KmyImporter(
                        new KmyDocument(raw, activity.getApplicationContext()),
                        activity.getApplicationContext());
                List<Integer> years = importer.budgetYears();
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (years.isEmpty()) {
                        Toast.makeText(activity, R.string.budget_import_none, Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (years.size() == 1) {
                        store(activity, repository, targetYear, importer, years.get(0), onDone);
                    } else {
                        String[] labels = new String[years.size()];
                        for (int i = 0; i < years.size(); i++) {
                            labels[i] = String.valueOf(years.get(i));
                        }
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.budget_import)
                                .setItems(labels, (d, which) ->
                                        store(activity, repository, targetYear, importer,
                                                years.get(which), onDone))
                                .show();
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(activity, R.string.import_failed, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void store(Activity activity, Repository repository, int targetYear,
                              KmyImporter importer, int sourceYear, Runnable onDone) {
        List<Budget> lines = new ArrayList<>();
        for (KmyDocument.BudgetEntry e : importer.budgetEntries(sourceYear)) {
            lines.add(new Budget(targetYear, e.category, e.isIncome, e.yearlyCents, Budget.SOURCE_KMY));
        }
        repository.replaceBudget(targetYear, Budget.SOURCE_KMY, lines, () -> {
            Toast.makeText(activity, R.string.budget_import_done, Toast.LENGTH_LONG).show();
            if (onDone != null) {
                onDone.run();
            }
        });
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
}
