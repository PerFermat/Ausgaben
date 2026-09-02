package de.spahr.ausgaben.ui;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/**
 * Der Anzeigename hinter einer {@link Uri} aus dem Dateiwähler.
 *
 * <p>Stand zweimal im Code, in zwei leicht verschiedenen Fassungen: einmal mit ausdrücklicher
 * Spaltenauswahl, einmal mit {@code query(uri, null, …)}. Letzteres lässt sich jeden Anbieter alle
 * Spalten zusammenstellen — bei einem Dokument in der Cloud kann das einen Netzzugriff bedeuten, für
 * einen einzigen Namen.</p>
 */
final class DocumentName {

    private DocumentName() {
    }

    /**
     * @return der Anzeigename, sonst das letzte Stück des Pfads, sonst {@code ""} — nie {@code null}
     */
    static String of(Context context, Uri uri) {
        if (uri == null) {
            return "";
        }
        try (Cursor c = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // Der Name ist eine Erinnerungshilfe; nicht jeder Anbieter liefert einen. Dafür bricht
            // nichts ab – dann tut der Pfad es auch.
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }
}
