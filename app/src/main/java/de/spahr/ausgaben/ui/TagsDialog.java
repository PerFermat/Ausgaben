package de.spahr.ausgaben.ui;

import android.app.Activity;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import de.spahr.ausgaben.R;
import de.spahr.ausgaben.db.BookingTags;

/**
 * Das Fenster, in dem die Stichwörter einer Buchung oder eines Alias bearbeitet werden: oben die
 * vergebenen, jedes einzeln zu löschen, darunter ein Feld zum Hinzufügen.
 *
 * <p>Das Feld verhält sich wie das Konto- und das Kategoriefeld – Teiltreffersuche, und was auf
 * keinen Eintrag paßt, wird verworfen: eingebbar ist nur, was es in KMyMoney gibt. Ganz oben stehen
 * die Stichwörter, die beim gewählten Empfänger schon vorkamen.</p>
 *
 * <p>Buchungsmaske und Alias-Editor teilen sich dieses Fenster; geändert wird erst mit «Fertig», und
 * wirksam wird es erst, wenn der Aufrufer speichert.</p>
 */
final class TagsDialog {

    /** Nimmt den neuen Speicherwert entgegen (siehe {@link BookingTags}). */
    interface OnDone {
        void onDone(String tags);
    }

    private TagsDialog() {
    }

    /**
     * @param known   alle in KMyMoney vorhandenen Stichwörter – nur daraus lässt sich wählen
     * @param lead    die Stichwörter des Empfängers; sie stehen im Vorspann ganz oben
     * @param current der bisherige Wert
     */
    static void show(Activity activity, List<String> known, List<String> lead, String current,
                     OnDone onDone) {
        final String[] draft = {current == null ? "" : current};
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_tags, null, false);
        final android.widget.LinearLayout rows = view.findViewById(R.id.tagRows);
        final PickerTextView field = view.findViewById(R.id.editTagNew);

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            rows.removeAllViews();
            for (String name : BookingTags.parse(draft[0])) {
                View row = activity.getLayoutInflater().inflate(R.layout.item_tag_row, rows, false);
                ((android.widget.TextView) row.findViewById(R.id.textTagName)).setText(name);
                row.findViewById(R.id.btnTagDelete).setOnClickListener(v -> {
                    draft[0] = BookingTags.remove(draft[0], name);
                    rebuild[0].run();
                });
                rows.addView(row);
            }
            // Schon vergebene Stichwörter fallen aus den Vorschlägen – auch aus dem Vorspann.
            PickerAdapters.tags(field, BookingTags.without(known, draft[0]),
                    BookingTags.without(lead, draft[0]));
        };
        rebuild[0].run();

        PickerBehaviour.onCommitted(field, value -> {
            String clean = BookingTags.sanitize(value);
            if (clean.isEmpty()) {
                return;
            }
            draft[0] = BookingTags.add(draft[0], clean);
            field.setText("", false);
            rebuild[0].run();
        });

        new AppDialog(activity)
                .setTitle(R.string.tags_dialog_title)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.tags_done, (d, w) -> onDone.onDone(draft[0]))
                .show();
    }
}
