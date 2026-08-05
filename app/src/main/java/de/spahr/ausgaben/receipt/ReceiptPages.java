package de.spahr.ausgaben.receipt;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Mehrseitige Belege. In der Notiz einer Buchung steht – wie eh und je – <b>nur ein</b>
 * {@code BELEG:}-Tag; er benennt die erste Seite und legt damit die für die Buchung feste Basis
 * {@code <jahr>_<uuid>} fest. Die weiteren Seiten {@code <basis>_Seite2.jpg}, {@code …_Seite3.jpg} sucht
 * die App selbst – erst im lokalen Beleg-Ordner, dann bei Bedarf auf dem Netzlaufwerk.
 *
 * <p>Die Seiten bleiben immer <b>lückenlos</b> nummeriert: wird eine mittlere Seite gelöscht, rücken die
 * folgenden nach ({@link #renumber}). So bleibt der Tag in der Notiz gültig und die Suche kann bei der
 * ersten fehlenden Nummer aufhören.</p>
 */
public final class ReceiptPages {

    /** Obergrenze der Suche – gegen endloses Nachfragen beim Server bei einem kaputten Namen. */
    private static final int MAX_PAGES = 30;

    private ReceiptPages() {
    }

    /**
     * Alle Seiten zu dem in der Notiz hinterlegten Namen, in Seitenreihenfolge. <b>Blockierend</b> (kann den
     * Server befragen) – vom Aufrufer auf einem Hintergrund-Thread nutzen. Liefert eine leere Liste, wenn
     * sich nicht einmal die erste Seite auftreiben lässt.
     */
    public static List<String> find(Context context, String tagName, int year) {
        List<String> pages = new ArrayList<>();
        if (tagName == null || tagName.trim().isEmpty()) {
            return pages;
        }
        Context app = context.getApplicationContext();
        String base = NoteReceipt.baseOf(tagName);
        // Seite 1: zuerst die alte Schreibweise ohne Zusatz, dann die neue.
        String legacy = base + ".jpg";
        if (Receipts.localFile(app, legacy).exists()) {
            pages.add(legacy);
        }
        for (int n = pages.isEmpty() ? 1 : 2; n <= MAX_PAGES; n++) {
            String name = NoteReceipt.pageName(base, n);
            if (!Receipts.localFile(app, name).exists()) {
                break;
            }
            pages.add(name);
        }
        if (!pages.isEmpty()) {
            return pages;
        }
        // Nichts lokal (z. B. nach einem Handywechsel): vom Netzlaufwerk nachladen. Der Tag benennt bei
        // Altbelegen die Datei selbst, sonst nur die Basis – dann ist Seite 1 die erste zu holende Datei.
        String first = NoteReceipt.yearOf(tagName) >= 0 ? tagName : NoteReceipt.pageName(base, 1);
        File got = ReceiptSync.ensureLocal(app, first, year);
        if (got == null || !got.exists()) {
            return pages;
        }
        pages.add(first);
        for (int n = NoteReceipt.pageOf(first) + 1; n <= MAX_PAGES; n++) {
            String name = NoteReceipt.pageName(base, n);
            File f = ReceiptSync.ensureLocal(app, name, year);
            if (f == null || !f.exists()) {
                break;
            }
            pages.add(name);
        }
        return pages;
    }

    /**
     * Kleinste noch freie Seitennummer zu einer bereits vergebenen Liste. Die alte Schreibweise ohne Zusatz
     * belegt dabei die 1, sodass die nächste Aufnahme zu einem Altbeleg die 2 bekommt.
     */
    public static int nextFreePage(List<String> pages) {
        int next = 1;
        boolean again = true;
        while (again) {
            again = false;
            for (String p : pages) {
                if (p != null && NoteReceipt.pageOf(p) == next) {
                    next++;
                    again = true;
                    break;
                }
            }
        }
        return next;
    }

    /**
     * Bildet die Namen einer (nach dem Löschen evtl. lückenhaften) Seitenfolge lückenlos auf
     * {@code _p1…_p<n>} ab: Ergebnis ist der Zielname je Eingabeposition. Ein Name, der schon richtig
     * sitzt, steht unverändert drin – auch die alte Schreibweise ohne Seitenzusatz bleibt auf Position 1,
     * damit ein Altbeleg nicht unnötig umbenannt wird.
     */
    public static List<String> renumber(List<String> pages) {
        List<String> out = new ArrayList<>(pages.size());
        for (int i = 0; i < pages.size(); i++) {
            String name = pages.get(i);
            int want = i + 1;
            boolean legacyFirst = want == 1 && name != null && !NoteReceipt.hasPageSuffix(name);
            out.add(legacyFirst || name == null || NoteReceipt.pageOf(name) == want
                    ? name
                    : NoteReceipt.pageName(NoteReceipt.baseOf(name), want));
        }
        return out;
    }

    /**
     * Benennt eine Seite samt ihrem {@code _original} um und merkt beide zum Hochladen vor. Nichts zu tun,
     * wenn die Namen gleich sind.
     */
    public static void rename(Context context, String from, String to, int year) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        Context app = context.getApplicationContext();
        for (String[] pair : new String[][]{
                {from, to},
                {NoteReceipt.originalName(from), NoteReceipt.originalName(to)}}) {
            File src = Receipts.localFile(app, pair[0]);
            if (src.exists() && src.renameTo(Receipts.localFile(app, pair[1]))) {
                Receipts.removePending(app, pair[0]);
                Receipts.addPending(app, pair[1], year);
            }
        }
    }

    /**
     * Schiebt die Seiten einer Buchung auf dem Server in einen anderen Jahresordner – nötig, wenn das
     * Buchungsdatum über einen Jahreswechsel geändert wurde. Blockierend und fehlertolerant: klappt eine
     * Datei nicht (offline), bleibt sie liegen und wird beim nächsten Anlauf erneut versucht.
     */
    public static void moveYear(Context context, List<String> pages, int fromYear, int toYear) {
        if (pages == null || pages.isEmpty() || fromYear == toYear || fromYear < 0 || toYear < 0) {
            return;
        }
        Context app = context.getApplicationContext();
        de.spahr.ausgaben.settings.SettingsStore settings =
                new de.spahr.ausgaben.settings.SettingsStore(app);
        if (!settings.hasRemoteConfig()) {
            return;
        }
        de.spahr.ausgaben.net.RemoteStorage storage;
        try {
            storage = de.spahr.ausgaben.net.RemoteStorage.from(settings);
        } catch (Exception e) {
            return;
        }
        String base = ReceiptSync.remoteBase(settings);
        String from = base + "/" + fromYear;
        String to = base + "/" + toYear;
        for (String page : pages) {
            for (String name : new String[]{page, NoteReceipt.originalName(page)}) {
                try {
                    byte[] bytes = storage.downloadBytes(from, name);
                    storage.ensureFolder(base);
                    storage.ensureFolder(to);
                    storage.uploadBytes(to, name, bytes);
                    storage.delete(from, name);
                } catch (Exception e) {
                    // Datei gibt es dort nicht (z. B. kein Original) oder gerade offline – überspringen.
                }
            }
        }
    }

    /** Löscht eine Seite samt Original – lokal und aus der Merkliste. */
    public static void delete(Context context, String name) {
        if (name == null) {
            return;
        }
        Context app = context.getApplicationContext();
        for (String n : new String[]{name, NoteReceipt.originalName(name)}) {
            Receipts.localFile(app, n).delete();
            Receipts.removePending(app, n);
        }
    }
}
