package de.spahr.ausgaben.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Adapter für die Kategorie-Auswahl im Filter. Baut aus den vorhandenen Kategorien einen Baum:
 * `Hauptkategorie:Unterkategorie` wird am ersten „:" gesplittet, Hauptkategorien fett, Unterkategorien
 * eingerückt. Ohne Text-Filterung – die Liste dient nur der Auswahl.
 */
public class CategoryFilterAdapter extends ArrayAdapter<CategoryFilterAdapter.CatItem> {

    /** Ein Eintrag in der Auswahl. {@code value} ist der zu filternde Kategorietext ("" = alle). */
    public static class CatItem {
        public final String label;
        public final String value;
        public final boolean isMain;

        CatItem(String label, String value, boolean isMain) {
            this.label = label;
            this.value = value;
            this.isMain = isMain;
        }
    }

    private final List<CatItem> items;
    private final int indentPx;

    public CategoryFilterAdapter(@NonNull Context context, String allLabel, List<String> categories) {
        super(context, android.R.layout.simple_list_item_1);
        this.items = buildTree(allLabel, categories);
        this.indentPx = Math.round(24 * context.getResources().getDisplayMetrics().density);
        addAll(this.items);
    }

    private static List<CatItem> buildTree(String allLabel, List<String> categories) {
        // Hauptkategorie -> Menge der Unterkategorien (leer, wenn keine)
        TreeMap<String, TreeSet<String>> tree = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String c : categories) {
            if (c == null || c.trim().isEmpty()) {
                continue;
            }
            String cat = c.trim();
            int i = cat.indexOf(':');
            String main = i >= 0 ? cat.substring(0, i).trim() : cat;
            String sub = i >= 0 ? cat.substring(i + 1).trim() : "";
            TreeSet<String> subs = tree.get(main);
            if (subs == null) {
                subs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                tree.put(main, subs);
            }
            if (!sub.isEmpty()) {
                subs.add(sub);
            }
        }

        List<CatItem> out = new ArrayList<>();
        out.add(new CatItem(allLabel, "", false));
        for (Map.Entry<String, TreeSet<String>> e : tree.entrySet()) {
            String main = e.getKey();
            out.add(new CatItem(main, main, true));
            for (String sub : e.getValue()) {
                out.add(new CatItem(sub, main + ":" + sub, false));
            }
        }
        return out;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        TextView tv = (TextView) super.getView(position, convertView, parent);
        CatItem item = getItem(position);
        if (item != null) {
            tv.setText(item.label);
            if (item.isMain) {
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                tv.setPadding(0, tv.getPaddingTop(), tv.getPaddingRight(), tv.getPaddingBottom());
            } else {
                tv.setTypeface(Typeface.DEFAULT);
                // „(alle Kategorien)" nicht einrücken, echte Unterkategorien schon.
                int left = item.value.contains(":") ? indentPx : 0;
                tv.setPadding(left, tv.getPaddingTop(), tv.getPaddingRight(), tv.getPaddingBottom());
            }
        }
        return tv;
    }

    /** Kein Text-Filtern: immer die ganze Liste anzeigen. */
    @NonNull
    @Override
    public Filter getFilter() {
        return NO_FILTER;
    }

    private final Filter NO_FILTER = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults r = new FilterResults();
            r.values = items;
            r.count = items.size();
            return r;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            notifyDataSetChanged();
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            return resultValue instanceof CatItem ? ((CatItem) resultValue).value : "";
        }
    };
}
