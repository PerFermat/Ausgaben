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
 * Kontenliste in der Navigations-Schublade. Erster Eintrag ist „Alle Konten"; kurzer Tipp wählt das
 * Konto, langer Tipp löst den Import aus. Der gewählte Eintrag wird hervorgehoben.
 */
public class AccountDrawerAdapter extends RecyclerView.Adapter<AccountDrawerAdapter.VH> {

    public interface Listener {
        /** Kurzer Tipp: Konto anzeigen. {@code isAll} = „Alle Konten". */
        void onSelect(String account, boolean isAll);

        /** Langer Tipp: Konto (bzw. alle) importieren. */
        void onImport(String account, boolean isAll);
    }

    private final List<String> items = new ArrayList<>();
    private final String allLabel;
    private String selected = "";
    private final Listener listener;

    public AccountDrawerAdapter(String allLabel, Listener listener) {
        this.allLabel = allLabel;
        this.listener = listener;
    }

    /** Setzt die Kontenliste (ohne „Alle Konten"; das wird vorangestellt). */
    public void setAccounts(List<String> accounts) {
        items.clear();
        items.add(allLabel);
        if (accounts != null) {
            items.addAll(accounts);
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
        String item = items.get(position);
        boolean isAll = position == 0;
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

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;

        VH(TextView itemView) {
            super(itemView);
            this.text = itemView;
        }
    }
}
