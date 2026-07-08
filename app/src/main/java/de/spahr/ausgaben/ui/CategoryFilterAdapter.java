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

import de.spahr.ausgaben.R;

/**
 * Adapter für die Kategorie-Auswahl. Oberste Ebene: die Gruppen-Überschriften „Ausgabe" und „Einnahme"
 * (fett, nicht als Wert nutzbar), darunter je die Kategorien als Baum (`Haupt:Unter`, Unterkategorien
 * eingerückt). Beim Tippen wird gefiltert, die zugehörige Gruppen-Überschrift bleibt aber sichtbar.
 */
public class CategoryFilterAdapter extends ArrayAdapter<CategoryFilterAdapter.CatItem> {

    private static final int KIND_ALL = 0;
    private static final int KIND_GROUP = 1;
    private static final int KIND_MAIN = 2;
    private static final int KIND_SUB = 3;

    /** Ein Eintrag. {@code value} = zu filternder Kategorietext ("" = alle bzw. Überschrift). */
    public static class CatItem {
        public final String label;
        public final String value;
        final int kind;
        final String group; // zu welcher Gruppe (Überschrift) dieser Eintrag gehört
        public final boolean isMain;

        CatItem(String label, String value, int kind, String group) {
            this.label = label;
            this.value = value;
            this.kind = kind;
            this.group = group;
            this.isMain = kind == KIND_MAIN;
        }
    }

    private final List<CatItem> all;
    private List<CatItem> shown;
    private final int indentPx;

    public CategoryFilterAdapter(@NonNull Context context, String allLabel,
                                 String expenseLabel, List<String> expenseCats,
                                 String incomeLabel, List<String> incomeCats) {
        super(context, android.R.layout.simple_list_item_1);
        this.all = build(allLabel, expenseLabel, expenseCats, incomeLabel, incomeCats);
        this.shown = new ArrayList<>(all);
        this.indentPx = Math.round(24 * context.getResources().getDisplayMetrics().density);
        addAll(this.shown);
    }

    private static List<CatItem> build(String allLabel, String expenseLabel, List<String> expenseCats,
                                       String incomeLabel, List<String> incomeCats) {
        List<CatItem> out = new ArrayList<>();
        if (allLabel != null) {
            out.add(new CatItem(allLabel, "", KIND_ALL, ""));
        }
        addGroup(out, expenseLabel, expenseCats);
        addGroup(out, incomeLabel, incomeCats);
        return out;
    }

    private static void addGroup(List<CatItem> out, String groupLabel, List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return;
        }
        out.add(new CatItem(groupLabel, "", KIND_GROUP, groupLabel));
        TreeMap<String, TreeSet<String>> tree = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String c : categories) {
            if (c == null || c.trim().isEmpty()) {
                continue;
            }
            String cat = c.trim();
            int i = cat.indexOf(':');
            String main = i >= 0 ? cat.substring(0, i).trim() : cat;
            String sub = i >= 0 ? cat.substring(i + 1).trim() : "";
            TreeSet<String> subs = tree.computeIfAbsent(main,
                    k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
            if (!sub.isEmpty()) {
                subs.add(sub);
            }
        }
        for (Map.Entry<String, TreeSet<String>> e : tree.entrySet()) {
            String main = e.getKey();
            out.add(new CatItem(main, main, KIND_MAIN, groupLabel));
            for (String sub : e.getValue()) {
                out.add(new CatItem(sub, main + ":" + sub, KIND_SUB, groupLabel));
            }
        }
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        TextView tv = (TextView) super.getView(position, convertView, parent);
        CatItem item = getItem(position);
        if (item == null) {
            return tv;
        }
        tv.setText(item.label);
        int top = tv.getPaddingTop();
        int right = tv.getPaddingRight();
        int bottom = tv.getPaddingBottom();
        if (item.kind == KIND_GROUP) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            tv.setAllCaps(true);
            tv.setTextColor(tv.getResources().getColor(R.color.grey_text, null));
            tv.setPadding(0, top, right, bottom);
        } else {
            tv.setAllCaps(false);
            tv.setTextColor(primaryText(tv));
            tv.setTypeface(item.kind == KIND_MAIN ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            int left = item.kind == KIND_SUB ? indentPx : 0;
            tv.setPadding(left, top, right, bottom);
        }
        return tv;
    }

    private static int primaryText(TextView t) {
        android.util.TypedValue tv = new android.util.TypedValue();
        t.getContext().getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true);
        return t.getContext().getColor(tv.resourceId != 0 ? tv.resourceId : android.R.color.black);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return filter;
    }

    private final Filter filter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<CatItem> result;
            String q = constraint == null ? "" : constraint.toString().trim().toLowerCase(Locale.getDefault());
            if (q.isEmpty()) {
                result = all;
            } else {
                // Passende Kategorien sammeln; eine Gruppen-Überschrift nur zeigen, wenn sie Treffer hat.
                result = new ArrayList<>();
                for (CatItem it : all) {
                    if (it.kind == KIND_ALL) {
                        result.add(it);
                    }
                }
                java.util.LinkedHashMap<String, List<CatItem>> byGroup = new java.util.LinkedHashMap<>();
                java.util.Map<String, CatItem> headers = new java.util.HashMap<>();
                for (CatItem it : all) {
                    if (it.kind == KIND_GROUP) {
                        headers.put(it.group, it);
                        byGroup.put(it.group, new ArrayList<>());
                    } else if ((it.kind == KIND_MAIN || it.kind == KIND_SUB)
                            && it.label.toLowerCase(Locale.getDefault()).contains(q)) {
                        List<CatItem> l = byGroup.get(it.group);
                        if (l != null) {
                            l.add(it);
                        }
                    }
                }
                for (Map.Entry<String, List<CatItem>> e : byGroup.entrySet()) {
                    if (!e.getValue().isEmpty()) {
                        result.add(headers.get(e.getKey()));
                        result.addAll(e.getValue());
                    }
                }
            }
            FilterResults r = new FilterResults();
            r.values = result;
            r.count = result.size();
            return r;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            shown = results.values == null ? new ArrayList<>() : (List<CatItem>) results.values;
            clear();
            addAll(shown);
            notifyDataSetChanged();
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            // Nur echte Kategorien liefern einen Wert; Überschriften/„alle" ergeben "".
            if (resultValue instanceof CatItem) {
                CatItem c = (CatItem) resultValue;
                return c.kind == KIND_MAIN || c.kind == KIND_SUB ? c.value : "";
            }
            return "";
        }
    };
}
