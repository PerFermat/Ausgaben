package de.spahr.ausgaben.ui;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;

/**
 * Kontenliste in der Navigations-Schublade. Erster Eintrag ist „Alle Konten"; danach die Konten,
 * am Ende die Depots. Kurzer Tipp wählt Konto/öffnet Depot, langer Tipp importiert bzw. aktualisiert.
 * Der gewählte Eintrag wird hervorgehoben.
 */
public class AccountDrawerAdapter extends RecyclerView.Adapter<AccountDrawerAdapter.VH> {

    public interface Listener {
        /** Kurzer Tipp auf ein Konto. {@code isAll} = „Alle Konten". */
        void onSelect(String account, boolean isAll);

        /** Langer Tipp auf ein Konto (bzw. alle): importieren. */
        void onImport(String account, boolean isAll);

        /** Kurzer Tipp auf ein Depot: Depot-Ansicht öffnen. */
        void onDepotSelect(String depot);

        /** Langer Tipp auf ein Depot: Depot neu importieren/aktualisieren. */
        void onDepotImport(String depot);
    }

    private final List<String> accounts = new ArrayList<>();
    private final List<String> depots = new ArrayList<>();
    private final String allLabel;
    private String selected = "";
    private final Listener listener;

    public AccountDrawerAdapter(String allLabel, Listener listener) {
        this.allLabel = allLabel;
        this.listener = listener;
    }

    /** Setzt die Kontenliste (ohne „Alle Konten"; das wird vorangestellt). */
    public void setAccounts(List<String> accounts) {
        this.accounts.clear();
        if (accounts != null) {
            this.accounts.addAll(accounts);
        }
        notifyDataSetChanged();
    }

    /** Setzt die Depots (werden hinter den Konten angezeigt). */
    public void setDepots(List<String> depots) {
        this.depots.clear();
        if (depots != null) {
            this.depots.addAll(depots);
        }
        notifyDataSetChanged();
    }

    /** Markiert das gewählte Konto (leer = „Alle Konten"). */
    public void setSelected(String account) {
        this.selected = account == null ? "" : account;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account, parent, false);
        return new VH((TextView) v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        if (position == 0) {
            bindAccount(h, allLabel, true);
            return;
        }
        int accIndex = position - 1;
        if (accIndex < accounts.size()) {
            bindAccount(h, accounts.get(accIndex), false);
            return;
        }
        bindDepot(h, depots.get(accIndex - accounts.size()));
    }

    private void bindAccount(VH h, String item, boolean isAll) {
        boolean isSelected = isAll ? selected.isEmpty() : item.equals(selected);
        h.text.setText(item);
        h.text.setTypeface(Typeface.DEFAULT, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        h.text.setBackgroundColor(isSelected
                ? h.text.getResources().getColor(R.color.saldo_bar_bg, null)
                : android.graphics.Color.TRANSPARENT);
        h.text.setOnClickListener(v -> listener.onSelect(isAll ? "" : item, isAll));
        h.text.setOnLongClickListener(v -> {
            listener.onImport(isAll ? "" : item, isAll);
            return true;
        });
    }

    private void bindDepot(VH h, String depot) {
        h.text.setText(h.text.getResources().getString(R.string.kmy_choose_depot, depot));
        h.text.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        h.text.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        h.text.setOnClickListener(v -> listener.onDepotSelect(depot));
        h.text.setOnLongClickListener(v -> {
            listener.onDepotImport(depot);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return 1 + accounts.size() + depots.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;

        VH(TextView itemView) {
            super(itemView);
            this.text = itemView;
        }
    }
}
