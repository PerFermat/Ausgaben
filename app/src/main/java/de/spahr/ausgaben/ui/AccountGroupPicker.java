package de.spahr.ausgaben.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AccountGroup;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Auswahl der Kontengruppe – gemeinsam genutzt von der Kontenschublade und den Beständen. Sie klappt als
 * Fenster unter ihrem Anker auf, nicht mehr als eigener Dialog in der Bildschirmmitte.
 *
 * <p>Jede Zeile trägt das Symbol ihrer Herkunft (Stern für die Favoriten, Bankgebäude für die Gruppen aus
 * der .kmy, Ordner für selbst angelegte); die gerade gültige Gruppe trägt ein Häkchen. Gelöscht wird hier
 * nichts: eine eigene Gruppe verschwindet von selbst, sobald ihr im Zuordnungsdialog das letzte Konto
 * entzogen wird.</p>
 *
 * <p>Gezeichnet wird mit einem {@link ListPopupWindow} statt einem {@code PopupMenu}, weil ein Menü seine
 * Symbole erst ab Android 10 zeigt – die App läuft ab Android 8.</p>
 */
final class AccountGroupPicker {

    private AccountGroupPicker() {
    }

    /**
     * Klappt die Auswahl unter {@code anchor} auf; {@code onChanged} läuft nach einer Änderung.
     *
     * @param span Fläche, deren Breite das Fenster einnehmen soll – in der Schublade deren ganzes Feld,
     *             nicht bloß die Überschrift, sonst blieben von langen Gruppennamen nur Stummel übrig.
     *             {@code null} = so breit wie der Anker.
     */
    static void show(Activity activity, View anchor, View span, Repository repository,
                     SettingsStore settings, Runnable onChanged) {
        repository.getSelectableAccountGroups(groups -> {
            final List<AccountGroup> entries = new ArrayList<>(groups);
            final long current = settings.getAccountGroup();
            final View breite = span == null ? anchor : span;
            final ListPopupWindow popup = new ListPopupWindow(activity);
            popup.setAnchorView(anchor);
            popup.setModal(true);
            popup.setBackgroundDrawable(
                    androidx.core.content.ContextCompat.getDrawable(activity, R.drawable.popup_background));
            popup.setWidth(breite.getWidth());
            popup.setHorizontalOffset(leftOf(breite) - leftOf(anchor));
            popup.setAdapter(new GroupAdapter(activity, entries, current));
            popup.setOnItemClickListener((parent, view, position, id) -> {
                settings.setAccountGroup(position == 0 ? 0L : entries.get(position - 1).id);
                popup.dismiss();
                onChanged.run();
            });
            popup.show();
        });
    }

    /** Linke Kante im Fenster – die Verschiebung des Listenfensters rechnet sich daraus. */
    private static int leftOf(View view) {
        int[] pos = new int[2];
        view.getLocationInWindow(pos);
        return pos[0];
    }

    /** „Alle Konten" an erster Stelle, danach die Gruppen in der Reihenfolge der Datenbank. */
    private static final class GroupAdapter extends BaseAdapter {

        private final Activity activity;
        private final List<AccountGroup> entries;
        private final long current;
        private final int tint;

        GroupAdapter(Activity activity, List<AccountGroup> entries, long current) {
            this.activity = activity;
            this.entries = entries;
            this.current = current;
            this.tint = PickerAdapters.iconTint(activity);
        }

        @Override
        public int getCount() {
            return entries.size() + 1;
        }

        @Override
        public AccountGroup getItem(int position) {
            return position == 0 ? null : entries.get(position - 1);
        }

        @Override
        public long getItemId(int position) {
            AccountGroup group = getItem(position);
            return group == null ? 0L : group.id;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(activity).inflate(R.layout.item_group_row, parent, false);
            AccountGroup group = getItem(position);
            ((TextView) row.findViewById(R.id.groupText)).setText(
                    group == null ? activity.getString(R.string.account_all) : group.name);

            ImageView icon = row.findViewById(R.id.groupIcon);
            icon.setImageResource(iconFor(group));
            icon.setColorFilter(tint);

            ImageView check = row.findViewById(R.id.groupCheck);
            boolean active = group == null ? current <= 0 : current == group.id;
            check.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
            check.setColorFilter(tint);
            return row;
        }

        private int iconFor(AccountGroup group) {
            if (group == null) {
                return R.drawable.ic_wallet;
            }
            if (AccountGroup.SOURCE_FAVORITES.equals(group.sourceKey)) {
                return R.drawable.ic_star;
            }
            return group.auto ? R.drawable.ic_bank : R.drawable.ic_group;
        }
    }
}
