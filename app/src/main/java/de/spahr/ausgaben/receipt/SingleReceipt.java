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
     * Ein Beleg, dessen Name schon feststeht, der aber noch an seinem vorläufigen Platz liegt.
     *
     * <p>Der Name wird hier vergeben und nicht erst beim Ablegen – dadurch lässt sich die {@link #note}
     * mit dem Beleg-Tag <b>vor</b> dem Speichern bilden und die Datei trotzdem erst danach bewegen.</p>
     */
    public static final class Planned {
        /** Die Notiz mit dem {@code BELEG (PDF):}-Tag – so gehört sie gespeichert. */
        public final String note;
        private final File pending;
        private final String name;
        private final int year;

        private Planned(String note, File pending, String name, int year) {
            this.note = note;
            this.pending = pending;
            this.name = name;
            this.year = year;
        }

        /** Ob überhaupt etwas abzulegen ist. */
        public boolean hatBeleg() {
            return name != null;
        }
    }

    /**
     * Vergibt den endgültigen Namen und baut die Notiz — <b>bewegt aber noch nichts</b>.
     *
     * <p>Getrennt von {@link #attach(Context, Planned)}, damit die Reihenfolge stimmt: erst die
     * Buchung speichern, dann den Beleg ablegen und zum Hochladen anmelden. Andersherum — und so war es
     * bis 1.12 — wandert die Abrechnung in den Jahresordner und der Upload läuft an, während das
     * Speichern noch scheitern kann; zurück kommt die Datei dann nicht mehr, denn ihr vorläufiger Pfad
     * war zu dem Zeitpunkt schon vergessen.</p>
     *
     * @param createdAt Buchungsdatum; sein Jahr bestimmt den Ordner der Belege
     */
    public static Planned plan(File pending, String note, long createdAt) {
        if (pending == null || !pending.exists()) {
            return new Planned(note, null, null, 0);
        }
        String name = NoteReceipt.pageName(NoteReceipt.newBase(), 1, NoteReceipt.PDF);
        return new Planned(NoteReceipt.withPdfName(note, NoteReceipt.tagOf(name)), pending, name,
                yearOf(createdAt));
    }

    /**
     * Legt den geplanten Beleg endgültig ab und meldet ihn zum Hochladen an. Erst aufrufen, wenn die
     * zugehörige Buchung gespeichert ist.
     *
     * @return {@code false}, wenn sich die Datei nicht verschieben ließ — die Buchung trägt dann einen
     *         Beleg-Tag ohne Datei dahinter, und der Aufrufer sollte das sagen
     */
    public static boolean attach(Context context, Planned planned) {
        if (planned == null || !planned.hatBeleg()) {
            return true;
        }
        if (!planned.pending.renameTo(Receipts.localFile(context, planned.name))) {
            return false;
        }
        Receipts.addPending(context, planned.name, planned.year);
        ReceiptSync.syncPending(context);
        return true;
    }

    private static int yearOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR);
    }
}
