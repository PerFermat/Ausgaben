package de.spahr.ausgaben.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AccountKind;
import de.spahr.ausgaben.db.AccountOrder;
import de.spahr.ausgaben.db.Repository;
import de.spahr.ausgaben.settings.SettingsStore;

/**
 * Einzige Bezugsquelle für die Vorschlagslisten unter den Eingabefeldern (Konto, Empfänger, Ort und
 * schlichte Aufzählungen). Jede Zeile ist {@link R.layout#item_picker_row} – Symbol, Abstand, Text –
 * statt des nackten {@code android.R.layout.simple_list_item_1}.
 *
 * <p>Die Symbole sind fest und ergeben sich aus der Sache: bei Konten aus der Kontenart, sonst aus der
 * Art der Liste. Es gibt kein wählbares Symbol je Konto, also auch nichts zu pflegen.</p>
 *
 * <p>Die Kategorien haben mit {@link CategoryFilterAdapter} eine eigene Liste (Farbpunkt und Pfeil je
 * nach Einnahme/Ausgabe); {@link #attach(AutoCompleteTextView, ArrayAdapter)} gibt ihr denselben
 * Zuschnitt des Listenfensters.</p>
 */
public final class PickerAdapters {

    private PickerAdapters() {
    }

    /**
     * Konten in der Reihenfolge, in der man sie braucht: erst die Favoriten, dann die Konten der gerade
     * gewählten Kontengruppe, dann alle übrigen (siehe {@link AccountOrder#forPicker}). Jeder Block hat
     * sein eigenes Symbol – Stern, Symbol der Gruppe, sonst die Kontenart.
     *
     * <p>Beides kommt aus der Datenbank, deshalb wird die Liste erst gesetzt, wenn sie da ist – bis
     * dahin steht im Feld ohnehin schon der bisherige Wert. Die gewählte Gruppe holt sich diese Methode
     * selbst aus den Einstellungen, damit keine Aufrufstelle sie durchreichen muss.</p>
     */
    public static void accounts(Repository repository, AutoCompleteTextView field, List<String> names) {
        if (field == null) {
            return;
        }
        accounts(repository, names, new AutoCompleteTextView[]{field});
    }

    /** Wie {@link #accounts}, aber für mehrere Felder mit derselben Liste (Umbuchen: von/nach). */
    public static void accounts(Repository repository, List<String> names,
                                AutoCompleteTextView... fields) {
        if (fields == null || fields.length == 0) {
            return;
        }
        Context context = fields[0].getContext();
        long groupId = new SettingsStore(context).getAccountGroup();
        repository.getAccountKinds(kinds -> repository.getAccountPickerBlocks(groupId, blocks -> {
            List<String> ordered = AccountOrder.forPicker(names, blocks.favorites, blocks.group);
            ArrayAdapter<String> adapter = accountAdapter(context, ordered, kinds,
                    blockIcons(blocks));
            for (AutoCompleteTextView field : fields) {
                attach(field, adapter);
                PickerBehaviour.searchable(field, PickerBehaviour.Unknown.RESTORE);
            }
        }));
    }

    /**
     * Kontoname (klein geschrieben) auf das Symbol seines Blocks. Die Favoriten werden zuletzt
     * eingetragen und stechen damit die Gruppe aus – genau wie in der Reihenfolge, wo ein Konto, das
     * beides ist, oben bei den Favoriten steht.
     */
    private static Map<String, Integer> blockIcons(Repository.PickerBlocks blocks) {
        Map<String, Integer> icons = new HashMap<>();
        int groupIcon = AccountGroupPicker.iconFor(blocks.groupInfo);
        for (String name : blocks.group) {
            icons.put(name.toLowerCase(Locale.ROOT), groupIcon);
        }
        for (String name : blocks.favorites) {
            icons.put(name.toLowerCase(Locale.ROOT), R.drawable.ic_star);
        }
        return icons;
    }

    /** Empfänger – das einzige Feld, in dem ein unbekannter Name stehenbleiben darf: er ist der neue. */
    public static void payees(AutoCompleteTextView field, List<String> names) {
        payees(field, names, null);
    }

    /**
     * Wie {@link #payees(AutoCompleteTextView, List)}, mit den Empfängern in der Nähe als Vorspann: sie
     * stehen mit dem Peilpfeil ganz oben, der nächste zuerst, und noch einmal an ihrem alphabetischen
     * Platz. Sobald jemand tippt, bleibt allein die alphabetische Trefferliste stehen.
     */
    public static void payees(AutoCompleteTextView field, List<String> names, List<String> nearby) {
        attach(field, payeeAdapter(field.getContext(), names, nearby));
        PickerBehaviour.searchable(field, PickerBehaviour.Unknown.KEEP);
    }

    private static ArrayAdapter<String> payeeAdapter(Context context, List<String> names,
                                                     List<String> nearby) {
        return new RowAdapter(context, names, nearby) {
            @Override
            int iconFor(String value) {
                return R.drawable.ic_payee;
            }
        };
    }

    public static void places(AutoCompleteTextView field, List<String> places) {
        attach(field, iconAdapter(field.getContext(), places, R.drawable.ic_place));
        PickerBehaviour.searchable(field, PickerBehaviour.Unknown.RESTORE);
    }

    /** Kategorien: eigener Adapter (Farbpunkt, Ein-/Ausgabe-Pfeil), sonst wie Konto und Ort. */
    public static void categories(AutoCompleteTextView field, ArrayAdapter<?> adapter) {
        attach(field, adapter);
        PickerBehaviour.searchable(field, PickerBehaviour.Unknown.RESTORE);
    }

    /** Aufzählungen ohne Sachbezug (Sprache, Export-Modus, Servertyp): reine Auswahl, keine Suche. */
    public static void plain(AutoCompleteTextView field, List<String> labels) {
        attach(field, plainAdapter(field.getContext(), labels));
    }

    /**
     * Aufzählung mit dem Verhalten der Kontenfelder – für die Sichtenwahl der Auswertung und der
     * Vorschau, die Konten, „Konto · Ort" und Sonderzeilen mischt.
     */
    public static void plainSearchable(AutoCompleteTextView field, List<String> labels) {
        attach(field, plainAdapter(field.getContext(), labels));
        PickerBehaviour.searchable(field, PickerBehaviour.Unknown.RESTORE);
    }

    /** Wie {@link #plain}, für Listen außerhalb eines Eingabefelds (Auswahl-Dialoge mit ListView). */
    public static ArrayAdapter<String> plainAdapter(Context context, List<String> labels) {
        return iconAdapter(context, labels, 0);
    }

    /** Setzt eine Liste und gibt dem Listenfenster den 16dp-Zuschnitt der Dialoge. */
    public static void attach(AutoCompleteTextView field, ArrayAdapter<?> adapter) {
        if (field == null) {
            return;
        }
        field.setThreshold(0);
        field.setDropDownBackgroundResource(R.drawable.popup_background);
        field.setAdapter(adapter);
    }

    /** Nur das Listenfenster zuschneiden – für Felder, die ihre Liste selbst mitbringen. */
    public static void round(AutoCompleteTextView field) {
        if (field != null) {
            field.setDropDownBackgroundResource(R.drawable.popup_background);
        }
    }

    public static ArrayAdapter<String> accountAdapter(Context context, List<String> names,
                                                      Map<String, Integer> kinds,
                                                      Map<String, Integer> blockIcons) {
        return new RowAdapter(context, names) {
            @Override
            int iconFor(String value) {
                Integer block = blockIcons == null || value == null
                        ? null : blockIcons.get(value.toLowerCase(Locale.ROOT));
                if (block != null) {
                    return block; // Favorit oder Konto der gewählten Gruppe: Block sticht Kontenart
                }
                Integer kind = kinds == null || value == null
                        ? null : kinds.get(value.toLowerCase(Locale.ROOT));
                if (kind == null) {
                    return R.drawable.ic_wallet;
                }
                switch (kind) {
                    case AccountKind.LIABILITY:
                        return R.drawable.ic_expense;
                    case AccountKind.DEPOT:
                        return R.drawable.ic_chart;
                    default:
                        return R.drawable.ic_wallet;
                }
            }
        };
    }

    /**
     * Stichwörter im Stichwort-Fenster: verhält sich wie das Kontofeld, und ganz oben stehen die
     * Stichwörter, die beim gewählten Empfänger schon vorkamen – dieselbe Machart wie der Vorspann
     * der Empfängerliste, nur mit dem Stichwort-Zeichen statt des Peilpfeils.
     */
    public static void tags(AutoCompleteTextView field, List<String> names, List<String> lead) {
        ArrayAdapter<String> adapter = new RowAdapter(field.getContext(), names, lead,
                R.drawable.ic_tag) {
            @Override
            int iconFor(String value) {
                return R.drawable.ic_tag;
            }
        };
        attach(field, adapter);
        limitRows(field, adapter, TAG_ROWS);
        PickerBehaviour.searchable(field, PickerBehaviour.Unknown.RESTORE);
    }

    /**
     * So viele Zeilen zeigt die Stichwortliste höchstens. Sie hängt in einem Dialogfenster, und sobald
     * die Bildschirmtastatur hochfährt, bleibt für eine lange Liste kein Platz mehr: sie klappt dann
     * nach oben auf, und ihre ersten Zeilen wandern über den Bildschirmrand hinaus.
     */
    private static final int TAG_ROWS = 6;

    /**
     * Deckelt die Höhe des Listenfensters auf {@code maxRows} Zeilen; darüber hinaus wird gerollt.
     * Kürzere Listen bleiben so hoch wie ihr Inhalt – sonst stünde unter zwei Einträgen Leerraum.
     *
     * <p>Die Höhe folgt der Trefferzahl, weil der Beobachter an jedem {@code notifyDataSetChanged()}
     * hängt, das der Suchlauf des Adapters auslöst.</p>
     */
    private static void limitRows(AutoCompleteTextView field, ArrayAdapter<?> adapter, int maxRows) {
        final int rowHeight = field.getResources().getDimensionPixelSize(R.dimen.picker_row_height);
        final Runnable apply = () -> field.setDropDownHeight(adapter.getCount() > maxRows
                ? maxRows * rowHeight
                : android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        adapter.registerDataSetObserver(new android.database.DataSetObserver() {
            @Override
            public void onChanged() {
                apply.run();
            }
        });
        apply.run();
    }

    private static ArrayAdapter<String> iconAdapter(Context context, List<String> values,
                                                    @DrawableRes int icon) {
        return new RowAdapter(context, values) {
            @Override
            int iconFor(String value) {
                return icon;
            }
        };
    }

    /** Adapter, der seinen ungefilterten Bestand kennt – der des Adapters schrumpft ja beim Suchen. */
    interface Stock {
        List<String> stock();
    }

    /** Zeichnet {@link R.layout#item_picker_row}; das Symbol legt die jeweilige Unterklasse fest. */
    private abstract static class RowAdapter extends ArrayAdapter<String> implements Stock {

        private final int iconTint;
        private final List<String> stock;
        /** Vorspann (nahe Empfänger): steht im leeren Feld über dem Bestand, beim Suchen nicht. */
        private final List<String> lead;
        /** Wieviele Zeilen der gerade angezeigten Liste zum Vorspann gehören. */
        private int leadShown;

        /** Zeichen der Vorspann-Zeilen; der Peilpfeil paßt nur zu den nahen Empfängern. */
        private final int leadIcon;

        RowAdapter(Context context, List<String> values) {
            this(context, values, null);
        }

        RowAdapter(Context context, List<String> values, List<String> lead) {
            this(context, values, lead, R.drawable.ic_near_me);
        }

        RowAdapter(Context context, List<String> values, List<String> lead, @DrawableRes int leadIcon) {
            // Eigene Kopie: die Aufrufer reichen teils feste Listen (Arrays.asList) herein, und ein
            // ArrayAdapter besteht darauf, seinen Bestand ändern zu dürfen. Von Anfang an mit Vorspann –
            // so steht dort auch dann das Richtige, wenn die Liste ohne Suchlauf aufklappt.
            super(context, R.layout.item_picker_row, R.id.pickerText, mit(lead, values));
            this.stock = values == null
                    ? new java.util.ArrayList<>() : new java.util.ArrayList<>(values);
            this.lead = lead == null
                    ? new java.util.ArrayList<>() : new java.util.ArrayList<>(lead);
            this.leadShown = this.lead.size();
            this.leadIcon = leadIcon;
            this.iconTint = iconTint(context);
        }

        /** Vorspann und Bestand hintereinander – die eine Liste, die im leeren Feld dasteht. */
        private static List<String> mit(List<String> lead, List<String> values) {
            List<String> alle = new java.util.ArrayList<>();
            if (lead != null) {
                alle.addAll(lead);
            }
            if (values != null) {
                alle.addAll(values);
            }
            return alle;
        }

        @Override
        public List<String> stock() {
            return stock;
        }

        /**
         * Sucht Teiltreffer an beliebiger Stelle statt nur am Wortanfang, den der {@link ArrayAdapter}
         * von Haus aus prüft: „kasse" soll auch „Sparkasse" finden – dieselbe Regel, nach der die Lupe
         * in der Kontenschublade sucht.
         */
        @NonNull
        @Override
        public Filter getFilter() {
            return filter;
        }

        private final Filter filter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<String> hits = new java.util.ArrayList<>();
                String query = constraint == null ? "" : constraint.toString();
                if (query.trim().isEmpty()) {
                    // Leeres Feld: der Vorspann steht oben, darunter der vollständige Bestand. Sobald
                    // gesucht wird, bleibt allein die Trefferliste – ganz ohne Zutun des Feldes.
                    hits.addAll(lead);
                }
                for (String value : stock) {
                    if (AccountOrder.matches(value, query)) {
                        hits.add(value);
                    }
                }
                FilterResults results = new FilterResults();
                results.values = hits;
                results.count = hits.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                String query = constraint == null ? "" : constraint.toString();
                leadShown = query.trim().isEmpty() ? lead.size() : 0;
                setNotifyOnChange(false); // sonst meldet schon das Leeren eine leere Liste
                clear();
                addAll((List<String>) results.values);
                notifyDataSetChanged();
            }
        };

        /** 0 = kein Symbol; der Platz bleibt frei, damit alle Zeilen denselben Textanfang haben. */
        abstract int iconFor(String value);

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(getContext()).inflate(R.layout.item_picker_row, parent, false);
            String value = getItem(position);
            ((TextView) row.findViewById(R.id.pickerText)).setText(value);
            ImageView icon = row.findViewById(R.id.pickerIcon);
            // Der Vorspann trägt sein Symbol nach der Stelle, nicht nach dem Namen: derselbe Empfänger
            // steht ein zweites Mal weiter unten, dort mit dem gewöhnlichen Symbol seiner Liste.
            int res = position < leadShown ? leadIcon : iconFor(value);
            if (res == 0) {
                icon.setImageDrawable(null);
            } else {
                icon.setImageResource(res);
                icon.setColorFilter(iconTint);
            }
            return row;
        }

        @NonNull
        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            return getView(position, convertView, parent);
        }
    }

    /** Symbolfarbe: gedämpfte Textfarbe des Themes, damit die Symbole in beiden Modi zurücktreten. */
    static int iconTint(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorOnSurfaceVariant, value, true)) {
            return value.resourceId != 0
                    ? ContextCompat.getColor(context, value.resourceId) : value.data;
        }
        return ContextCompat.getColor(context, R.color.grey_text);
    }
}
