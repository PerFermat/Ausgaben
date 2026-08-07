package de.spahr.ausgaben.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.AccountKind;
import de.spahr.ausgaben.db.Repository;

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
     * Konten mit dem Symbol ihrer Kontenart. Die Kontenarten kommen aus der Datenbank, deshalb wird die
     * Liste erst gesetzt, wenn sie da sind – bis dahin steht im Feld ohnehin schon der bisherige Wert.
     */
    public static void accounts(Repository repository, AutoCompleteTextView field, List<String> names) {
        if (field == null) {
            return;
        }
        repository.getAccountKinds(kinds -> attach(field, accountAdapter(field.getContext(), names, kinds)));
    }

    /** Wie {@link #accounts}, aber für mehrere Felder mit derselben Liste (Umbuchen: von/nach). */
    public static void accounts(Repository repository, List<String> names,
                                AutoCompleteTextView... fields) {
        if (fields == null || fields.length == 0) {
            return;
        }
        Context context = fields[0].getContext();
        repository.getAccountKinds(kinds -> {
            ArrayAdapter<String> adapter = accountAdapter(context, names, kinds);
            for (AutoCompleteTextView field : fields) {
                attach(field, adapter);
            }
        });
    }

    public static void payees(AutoCompleteTextView field, List<String> names) {
        attach(field, iconAdapter(field.getContext(), names, R.drawable.ic_payee));
    }

    public static void places(AutoCompleteTextView field, List<String> places) {
        attach(field, iconAdapter(field.getContext(), places, R.drawable.ic_place));
    }

    /** Aufzählungen ohne Sachbezug (Sprache, Export-Modus, Ansicht): gleiche Zeile, kein Symbol. */
    public static void plain(AutoCompleteTextView field, List<String> labels) {
        attach(field, plainAdapter(field.getContext(), labels));
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
                                                      Map<String, Integer> kinds) {
        return new RowAdapter(context, names) {
            @Override
            int iconFor(String value) {
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

    private static ArrayAdapter<String> iconAdapter(Context context, List<String> values,
                                                    @DrawableRes int icon) {
        return new RowAdapter(context, values) {
            @Override
            int iconFor(String value) {
                return icon;
            }
        };
    }

    /** Zeichnet {@link R.layout#item_picker_row}; das Symbol legt die jeweilige Unterklasse fest. */
    private abstract static class RowAdapter extends ArrayAdapter<String> {

        private final int iconTint;

        RowAdapter(Context context, List<String> values) {
            // Eigene Kopie: die Aufrufer reichen teils feste Listen (Arrays.asList) herein, und ein
            // ArrayAdapter besteht darauf, seinen Bestand ändern zu dürfen.
            super(context, R.layout.item_picker_row, R.id.pickerText,
                    values == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(values));
            this.iconTint = iconTint(context);
        }

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
            int res = iconFor(value);
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
