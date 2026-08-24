package de.spahr.ausgaben.receipt;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.UUID;

/**
 * Ein einzelnes PDF als Beleg anhängen — für die eingelesene Depotabrechnung.
 *
 * <p>Die Buchungsmaske bringt dafür einen ganzen Apparat mit (mehrere Seiten, Umnummerieren, Entfernen,
 * Nachbearbeiten). Eine Abrechnung ist immer genau eine Datei; hier steht deshalb nur der kurze Weg —
 * kopieren, endgültig benennen, zum Hochladen vormerken. Ablage, Namensschema, Jahresordner und
 * Abgleich sind dieselben wie bei den Belegen der Buchungen.</p>
 */
public final class SingleReceipt {

    private SingleReceipt() {
    }

    /**
     * Kopiert das PDF in die Belegablage — noch unter einem vorläufigen Namen, denn erst beim Speichern
     * steht das Buchungsdatum und damit der Jahresordner fest.
     *
     * @return die vorläufige Datei, oder {@code null}, wenn sich die Quelle nicht lesen ließ
     */
    public static File stage(Context context, Uri source) {
        File tmp = new File(Receipts.dir(context), "pend_" + UUID.randomUUID() + NoteReceipt.PDF);
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) {
                return null;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return tmp.length() > 0 ? tmp : null;
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
    }

    /**
     * Macht aus der vorläufigen Datei einen endgültigen Beleg und liefert die Notiz mit dem
     * {@code BELEG (PDF):}-Tag zurück.
     *
     * @param createdAt Buchungsdatum; sein Jahr bestimmt den Ordner der Belege
     * @return die ergänzte Notiz, oder die unveränderte, wenn nichts anzuhängen war
     */
    public static String attach(Context context, File pending, String note, long createdAt) {
        if (pending == null || !pending.exists()) {
            return note;
        }
        String name = NoteReceipt.pageName(NoteReceipt.newBase(), 1, NoteReceipt.PDF);
        if (!pending.renameTo(Receipts.localFile(context, name))) {
            return note;
        }
        Receipts.addPending(context, name, yearOf(createdAt));
        ReceiptSync.syncPending(context);
        return NoteReceipt.withPdfName(note, NoteReceipt.tagOf(name));
    }

    private static int yearOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR);
    }
}
