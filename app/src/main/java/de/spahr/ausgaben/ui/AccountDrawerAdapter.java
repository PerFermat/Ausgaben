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
 * Kontenliste in der Navigations-Schublade. Aufbau: „Alle Konten", dann – jeweils mit Überschrift –
 * die Anlage- und Verbindlichkeitskonten, am Ende die Depots. Kurzer Tipp wählt Konto/öffnet Depot,
 * langer Tipp importiert bzw. aktualisiert. Der gewählte Eintrag wird hervorgehoben.
 */
public class AccountDrawerAdapter extends RecyclerView.Adapter<AccountDrawerAdapter.VH> {

    public interface Listener {
        void onSelect(String account, boolean isAll);
        void onImport(String account, boolean isAll);
        void onDepotSelect(String depot);
        void onDepotImport(String depot);
    }

    private static final int KIND_ALL = 0;
    private static final int KIND_HEADER = 1;
    private static final int KIND_ACCOUNT = 2;
    private static final int KIND_DEPOT = 3;

    private static final class Row {
        final int kind;
        final String text;
        Row(int kind, String text) { this.kind = kind; this.text = text; }
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<String> assets = new ArrayList<>();
    private final List<String> liabilities = new ArrayList<>();
    private final List<String> depots = new ArrayList<>();
    private final String allLabel;
    private String selected = "";
    private final Listener listener;

    public AccountDrawerAdapter(String allLabel, Listener listener) {
        this.allLabel = allLabel;
        this.listener = listener;
    }

    /** Setzt die Kontolisten (getrennt nach Anlage/Verbindlichkeit). */
    public void setAccounts(List<String> assetAccounts, List<String> liabilityAccounts) {
        assets.clear();
        liabilities.clear();
        if (assetAccounts != null) assets.addAll(assetAccounts);
        if (liabilityAccounts != null) liabilities.addAll(liabilityAccounts);
        rebuild();
    }

    /** Setzt die Depots (hinter den Konten). */
    public void setDepots(List<String> depotNames) {
        depots.clear();
        if (depotNames != null) depots.addAll(depotNames);
        rebuild();
    }

    /** Markiert das gewählte Konto (leer = „Alle Konten"). */
    public void setSelected(String account) {
        this.selected = account == null ? "" : account;
        notifyDataSetChanged();
    }

    private void rebuild() {
        rows.clear();
        rows.add(new Row(KIND_ALL, allLabel));
        if (!assets.isEmpty()) {
            rows.add(new Row(KIND_HEADER, "accounts_asset"));
            for (String a : assets) rows.add(new Row(KIND_ACCOUNT, a));
        }
        if (!liabilities.isEmpty()) {
            rows.add(new Row(KIND_HEADER, "accounts_liability"));
            for (String a : liabilities) rows.add(new Row(KIND_ACCOUNT, a));
        }
        if (!depots.isEmpty()) {
            rows.add(new Row(KIND_HEADER, "accounts_depot"));
            for (String d : depots) rows.add(new Row(KIND_DEPOT, d));
        }
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
        Row r = rows.get(position);
        switch (r.kind) {
            case KIND_ALL:
                bindAccount(h, allLabel, true);
                break;
            case KIND_ACCOUNT:
                bindAccount(h, r.text, false);
                break;
            case KIND_DEPOT:
                bindDepot(h, r.text);
                break;
            case KIND_HEADER:
            default:
                bindHeader(h, r.text);
                break;
        }
    }

    private void bindHeader(VH h, String key) {
        int resId;
        if ("accounts_liability".equals(key)) {
            resId = R.string.accounts_liability;
        } else if ("accounts_depot".equals(key)) {
            resId = R.string.accounts_depot;
        } else {
            resId = R.string.accounts_asset;
        }
        h.text.setText(h.text.getResources().getString(resId));
        h.text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        // Invers-Darstellung: hell = dunkler Grund/weiße Schrift, dunkel = 50 % Grau/schwarze Schrift.
        boolean night = (h.text.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        h.text.setBackgroundColor(night ? 0xFF808080 : 0xFF303030);
        h.text.setTextColor(night ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
        // Kompakter als Konto-Zeilen und bündig an die folgende Konto-Zeile (kein sichtbarer Abstand).
        applyRowMetrics(h, true);
        h.text.setOnClickListener(null);
        h.text.setClickable(false);
        h.text.setOnLongClickListener(null);
        h.text.setLongClickable(false);
    }

    /** Setzt Zeilenhöhe/-abstände: Überschriften kompakt und unten bündig, sonst die Standard-Konto-Zeile. */
    private void applyRowMetrics(VH h, boolean header) {
        float d = h.text.getResources().getDisplayMetrics().density;
        int padH = Math.round(20 * d);
        if (header) {
            h.text.setMinHeight(0);
            h.text.setMinimumHeight(0);
            h.text.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.START);
            h.text.setPadding(padH, Math.round(16 * d), padH, Math.round(4 * d));
        } else {
            h.text.setMinHeight(Math.round(52 * d));
            h.text.setMinimumHeight(Math.round(52 * d));
            h.text.setGravity(android.view.Gravity.CENTER_VERTICAL);
            h.text.setPadding(padH, Math.round(12 * d), padH, Math.round(12 * d));
        }
    }

    private void bindAccount(VH h, String item, boolean isAll) {
        boolean isSelected = isAll ? selected.isEmpty() : item.equals(selected);
        applyRowMetrics(h, false);
        h.text.setText(item);
        h.text.setTypeface(Typeface.DEFAULT, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        h.text.setTextColor(primaryText(h.text));
        h.text.setBackgroundColor(isSelected
                ? h.text.getResources().getColor(R.color.saldo_bar_bg, null)
                : android.graphics.Color.TRANSPARENT);
        h.text.setClickable(true);
        h.text.setLongClickable(true);
        h.text.setOnClickListener(v -> listener.onSelect(isAll ? "" : item, isAll));
        h.text.setOnLongClickListener(v -> {
            listener.onImport(isAll ? "" : item, isAll);
            return true;
        });
    }

    private void bindDepot(VH h, String depot) {
        applyRowMetrics(h, false);
        h.text.setText(h.text.getResources().getString(R.string.kmy_choose_depot, depot));
        h.text.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        h.text.setTextColor(primaryText(h.text));
        h.text.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        h.text.setClickable(true);
        h.text.setLongClickable(true);
        h.text.setOnClickListener(v -> listener.onDepotSelect(depot));
        h.text.setOnLongClickListener(v -> {
            listener.onDepotImport(depot);
            return true;
        });
    }

    private static int primaryText(TextView t) {
        android.util.TypedValue tv = new android.util.TypedValue();
        t.getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        return t.getContext().getColor(tv.resourceId != 0 ? tv.resourceId : android.R.color.black);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;
        VH(TextView itemView) {
            super(itemView);
            this.text = itemView;
        }
    }
}
