package de.spahr.ausgaben.ui;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AccountGroup;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Auswahl der Kontengruppe – gemeinsam genutzt von der Kontenschublade und den Beständen.
 *
 * <p>Antippen wählt die Gruppe, langes Antippen löscht eine selbst angelegte. Bankgruppen spiegeln nur
 * die KMyMoney-Datei und reagieren deshalb weder auf das eine noch auf das andere.</p>
 */
final class AccountGroupPicker {

    private AccountGroupPicker() {
    }

    /** Zeigt die Auswahl; {@code onChanged} läuft, wenn sich die gespeicherte Gruppe geändert hat. */
    static void show(Activity activity, Repository repository, SettingsStore settings,
                     Runnable onChanged) {
        repository.getSelectableAccountGroups(groups -> {
            final List<AccountGroup> entries = new ArrayList<>(groups);
            final List<String> labels = new ArrayList<>();
            labels.add(activity.getString(R.string.account_all));
            for (AccountGroup g : entries) {
                labels.add(g.name);
            }
            ListView list = new ListView(activity);
            list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, labels));
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity,
                    R.style.ThemeOverlay_Ausgaben_Dialog)
                    .setTitle(R.string.accounts_group_pick)
                    .setView(list)
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            list.setOnItemClickListener((parent, view, position, id) -> {
                settings.setAccountGroup(position == 0 ? 0L : entries.get(position - 1).id);
                dialog.dismiss();
                onChanged.run();
            });
            list.setOnItemLongClickListener((parent, view, position, id) -> {
                if (position == 0 || entries.get(position - 1).auto) {
                    return true; // „Alle Konten" und Bankgruppen lassen sich nicht löschen
                }
                dialog.dismiss();
                confirmDelete(activity, repository, settings, entries.get(position - 1), onChanged);
                return true;
            });
            dialog.show();
        });
    }

    private static void confirmDelete(Activity activity, Repository repository, SettingsStore settings,
                                      AccountGroup group, Runnable onChanged) {
        new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_Ausgaben_Dialog)
                .setTitle(R.string.accounts_group_delete_title)
                .setMessage(activity.getString(R.string.accounts_group_delete_message, group.name))
                .setPositiveButton(R.string.delete, (d, w) -> repository.deleteAccountGroup(group.id, () -> {
                    if (settings.getAccountGroup() == group.id) {
                        settings.setAccountGroup(0L);
                    }
                    onChanged.run();
                }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
