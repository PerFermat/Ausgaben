package de.spahr.ausgaben.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.Account;
import de.spahr.ausgaben.db.AccountGroup;
import de.spahr.ausgaben.db.Repository;

/**
 * Zuordnung eines Kontos zu den Kontengruppen: eine Ankreuzliste aller selbst angelegten Gruppen, oben
 * ein freies Feld für eine neue. Der Haken sagt, ob das Konto in der Gruppe steht; geschrieben wird erst
 * bei „OK", und zwar alles auf einmal.
 *
 * <p>Gruppen aus der KMyMoney-Datei (Banknamen, Favoriten) stehen nicht in der Liste – sie spiegeln nur
 * die Datei, und eine Zuordnung von Hand wäre beim nächsten Import wieder fort.</p>
 *
 * <p>Die zweite Schaltfläche schließt das Konto bzw. öffnet es wieder. Sie übernimmt die Haken mit,
 * bevor sie das tut – sonst wären sie stillschweigend verloren.</p>
 */
final class AccountGroupsDialog {

    private AccountGroupsDialog() {
    }

    /**
     * @param closable true, wenn das Konto geschlossen bzw. wieder geöffnet werden darf; sonst fehlt die
     *                 zweite Schaltfläche
     * @param onDone   läuft nach jeder Änderung, damit die Liste sich neu aufbaut
     */
    static void show(Activity activity, Repository repository, Account account,
                     List<AccountGroup> groups, Set<Long> member, boolean closable, Runnable onDone) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.dialog_account_groups, null);
        final MaterialCheckBox newCheck = view.findViewById(R.id.groupsNewCheck);
        final EditText newName = view.findViewById(R.id.groupsNewName);
        LinearLayout container = view.findViewById(R.id.groupsContainer);

        // Wer tippt, meint die neue Gruppe – der Haken folgt der Eingabe von selbst und der alte
        // Fehlerhinweis verschwindet mit der ersten Berichtigung.
        newName.addTextChangedListener(new SimpleWatcher(() -> {
            newCheck.setChecked(newName.getText().toString().trim().length() > 0);
            newName.setError(null);
        }));

        // Wer den Haken setzt, will den Namen tippen – also Kursor und Tastatur gleich mit.
        // setOnClickListener und nicht setOnCheckedChangeListener: den Haken setzt der Beobachter oben
        // beim Tippen selbst, und das darf die Tastatur nicht erneut anstoßen.
        newCheck.setOnClickListener(v -> {
            if (newCheck.isChecked()) {
                Keyboard.show(newName);
            } else {
                Keyboard.hide(newName);
            }
        });

        final MaterialCheckBox[] boxes = new MaterialCheckBox[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            AccountGroup group = groups.get(i);
            View row = inflater.inflate(R.layout.item_group_check, container, false);
            ((TextView) row.findViewById(R.id.rowText)).setText(group.name);
            MaterialCheckBox box = row.findViewById(R.id.rowCheck);
            box.setChecked(member.contains(group.id));
            // Auch ein Tipp auf den Namen schaltet den Haken – die Fläche daneben ist der größere Griff.
            row.setOnClickListener(v -> box.setChecked(!box.isChecked()));
            container.addView(row);
            boxes[i] = box;
        }

        AppDialog builder = new AppDialog(activity);
        builder.setTitle(account.name);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok, null); // Klick wird unten selbst verdrahtet
        if (closable) {
            builder.setNeutralButton(account.closed
                    ? R.string.accounts_manage_reopen : R.string.accounts_manage_close, null);
        }
        AlertDialog dialog = builder.create();
        dialog.show();

        // Eigene Klickbehandlung statt setPositiveButton(…, listener): bei einem belegten Namen soll der
        // Dialog offen bleiben, und das gewöhnliche Verhalten schließt ihn immer.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                apply(activity, repository, account, groups, boxes, newCheck, newName,
                        dialog, false, onDone));
        if (closable) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    apply(activity, repository, account, groups, boxes, newCheck, newName,
                            dialog, true, onDone));
        }
    }

    /** Übernimmt die Haken; {@code alsoClose} schaltet danach den Zustand des Kontos um. */
    private static void apply(Activity activity, Repository repository, Account account,
                              List<AccountGroup> groups, MaterialCheckBox[] boxes,
                              MaterialCheckBox newCheck, EditText newName, AlertDialog dialog,
                              boolean alsoClose, Runnable onDone) {
        Set<Long> selected = new HashSet<>();
        for (int i = 0; i < boxes.length; i++) {
            if (boxes[i].isChecked()) {
                selected.add(groups.get(i).id);
            }
        }
        String name = newCheck.isChecked() ? newName.getText().toString().trim() : "";
        repository.applyAccountGroups(account.name, selected, name, result -> {
            if (result == Repository.GROUPS_NAME_FROM_FILE) {
                newName.setError(activity.getString(R.string.accounts_group_name_from_file));
                newName.requestFocus();
                return; // Dialog bleibt offen, nichts wurde geschrieben
            }
            if (alsoClose) {
                repository.setAccountClosed(account.name, !account.closed, () -> {
                    android.widget.Toast.makeText(activity, activity.getString(account.closed
                                    ? R.string.account_reopened_done : R.string.account_closed_done,
                            account.name), android.widget.Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    onDone.run();
                });
                return;
            }
            dialog.dismiss();
            onDone.run();
        });
    }
}
