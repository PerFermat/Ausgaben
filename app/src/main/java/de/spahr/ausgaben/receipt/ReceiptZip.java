package de.spahr.ausgaben.receipt;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.spahr.ausgaben.util.ProgressListener;

/**
 * Packt die Belege einer gefilterten Buchungsliste in eine ZIP-Datei – unter sprechenden Namen
 * (siehe {@link ReceiptExportName}) statt der UUIDs, unter denen die App sie führt.
 *
 * <p>Was auf dem Gerät fehlt, wird aus dem Sync-Ordner nachgeholt. Geht das nicht (kein Netz, Datei
 * dort nicht vorhanden), zählt es als fehlend und der Lauf geht weiter: lieber die vorhandenen Belege
 * in der Hand als gar keine.</p>
 *
 * <p><b>Blockierend</b> – vom Aufrufer auf einem Hintergrund-Thread nutzen.</p>
 */
public final class ReceiptZip {

    /** Was ein Lauf zustande gebracht hat. */
    public static final class Result {
        /** Seiten, die in der Datei gelandet sind. */
        public int written;
        /** Seiten, die weder lokal noch auf dem Server zu finden waren. */
        public int missing;
        /** Behandelte Belege (Buchungen mit Beleg). */
        public int receipts;
    }

    private ReceiptZip() {
    }

    /**
     * Schreibt die Belege der {@code jobs} als ZIP nach {@code out}. Der Strom wird geschlossen.
     *
     * @param progress darf {@code null} sein; wird je fertigem Beleg gemeldet
     */
    public static Result write(Context context, OutputStream out, List<ReceiptExportJobs.Job> jobs,
                               ProgressListener progress) throws IOException {
        Context app = context.getApplicationContext();
        Result result = new Result();
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            int total = jobs == null ? 0 : jobs.size();
            for (int i = 0; jobs != null && i < total; i++) {
                ReceiptExportJobs.Job job = jobs.get(i);
                result.receipts++;
                List<String> pages = ReceiptPages.find(app, job.tagName, job.year, job.ext);
                if (pages.isEmpty()) {
                    // Nicht einmal die erste Seite aufzutreiben – der ganze Beleg fehlt.
                    result.missing++;
                } else {
                    for (String page : pages) {
                        File file = ReceiptSync.ensureLocal(app, page, job.year);
                        if (file == null || !file.exists()) {
                            result.missing++;
                            continue;
                        }
                        put(zip, entryName(job, page, usedNames), file);
                        result.written++;
                    }
                }
                if (progress != null) {
                    progress.onProgress(i + 1, total);
                }
            }
        }
        return result;
    }

    /**
     * Der Eintragsname, und zwar ein noch freier. Die Buchungsnummer im Namen macht eine
     * Namensgleichheit eigentlich unmöglich – aber ein doppelter Eintrag wäre ein stiller Verlust
     * beim Auspacken, und ein {@link HashSet} dagegen kostet nichts.
     */
    private static String entryName(ReceiptExportJobs.Job job, String page, Set<String> used) {
        String name = job.istWertpapier()
                ? ReceiptExportName.ofSecurity(job.createdAt, job.action, job.securityName,
                        job.amountCents, job.bookingId, page)
                : ReceiptExportName.of(job.createdAt, job.payee, job.amountCents, job.bookingId, page);
        String candidate = name;
        int n = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            int dot = name.lastIndexOf('.');
            candidate = name.substring(0, dot) + "-" + n++ + name.substring(dot);
        }
        return candidate;
    }

    private static void put(ZipOutputStream zip, String name, File file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] buf = new byte[8192];
        try (InputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                zip.write(buf, 0, n);
            }
        }
        zip.closeEntry();
    }
}
